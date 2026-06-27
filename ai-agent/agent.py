from dataclasses import dataclass

import boto3
from langchain.agents import create_agent
from langchain.agents.middleware import HumanInTheLoopMiddleware, SummarizationMiddleware
from langchain_mcp_adapters.client import MultiServerMCPClient
from langgraph_checkpoint_aws import DynamoDBSaver

from approval import ModeState, build_interrupt_on
from auth import Session
from settings import Settings, load_settings
from tool_visibility import visible_tools

# Conversations expire from DynamoDB after this long without activity (mirrors the
# Java chat-memory TTL of 30 days).
_CHECKPOINT_TTL_SECONDS = 30 * 24 * 60 * 60

# Once the message history passes this many tokens, older turns are condensed into a
# running summary while the most recent turns are kept verbatim (controls cost / TPM).
_SUMMARY_TRIGGER_TOKENS = 4000
_SUMMARY_KEEP_MESSAGES = 20


@dataclass
class BuiltAgent:
    """What build_agent hands back: the agent plus a small summary of the session.

    Bundling these in one named object (instead of a long tuple) keeps the call site
    readable, the same way Session did for login.
    """

    agent: object          # the LangGraph agent to invoke
    tools: list            # tools the agent may use (already filtered by role)
    role: str              # the signed-in user's role
    total_tools: int       # tools the server offered before filtering
    user_id: int | None    # signed-in user's id (used to key their conversation memory)
    mode_state: ModeState  # current approval mode; flip .mode to switch live


SYSTEM_PROMPT = """\
You are the assistant for the Marathon Bib Expo application, which manages marathon
events, races, categories, participants, bib and goodies distribution, users and
organizations. Help only with running this application; politely decline anything else.

You act on behalf of the signed-in user and already operate within their role and
permissions, so never ask the user what their role is or claim access you do not have.
You are only given the tools that user is allowed to use; if they ask for something you
have no tool for, say plainly in one sentence that you cannot do that here, then point
them to what you can help with. Do not ask for their role and do not offer to guide them
through an action you cannot perform.

People refer to events, organizations and users by name, not by numeric id. When a tool
needs an id you were not given, resolve it yourself using a search tool with the name the
user mentioned, then use that record's id. When exactly one record matches, use it; when
several match, ask the user to choose by a human detail such as date or city; when none
match, say so. Never ask the user for a numeric id, and do not show raw ids unless asked.

Be concise. For questions, just call the relevant read-only tool and report the result.
Before any action that creates, changes or removes data, confirm the details first.
"""


async def build_agent(settings: Settings, session: Session) -> BuiltAgent:
    """Build an agent for one already-authenticated user.

    The caller supplies the session (the user's token + role + id): the REPL gets it
    from login(), and the HTTP server gets it from the identity Spring forwards. This
    keeps "how we learned who the user is" out of agent assembly, so the same builder
    serves both the dev REPL and a per-request web call.

    Returns a BuiltAgent (the agent plus a summary of the session).
    """
    client = MultiServerMCPClient(
        {
            "bibexpo": {
                "transport": "sse",
                "url": settings.mcp_sse_url,
                "headers": {"Authorization": f"Bearer {session.token}"},
            }
        }
    )
    all_tools = await client.get_tools()

    # Keep only the tools this user's role may see. The MCP server sends every client the full
    # list (it cannot filter per user), so we trim it here using the role from the trusted login
    # response; the server still enforces real access on every call as a backstop. Writes that
    # survive the filter are still gated by the "confirm before any change" rule in the prompt.
    tools = visible_tools(session.role, all_tools)

    # Persist conversation state to DynamoDB so memory survives restarts. boto3 picks up the
    # LocalStack creds that settings already loaded; endpoint_url points at LocalStack locally
    # and is None (real AWS) in production, where profile is also None (EC2 instance role).
    boto_session = boto3.Session(
        region_name=settings.aws_region,
        profile_name=settings.aws_profile,
    )
    checkpointer = DynamoDBSaver(
        table_name=settings.checkpoint_table,
        session=boto_session,
        endpoint_url=settings.ddb_endpoint_url,
        ttl_seconds=_CHECKPOINT_TTL_SECONDS,
    )

    # The approval gate. mode_state starts from settings but is mutable so the REPL can
    # switch modes live; the interrupt_on map reads it fresh on every tool call.
    mode_state = ModeState(mode=settings.approval_mode)

    agent = create_agent(
        model=f"openai:{settings.openai_model}",
        tools=tools,
        system_prompt=SYSTEM_PROMPT,
        checkpointer=checkpointer,
        middleware=[
            HumanInTheLoopMiddleware(interrupt_on=build_interrupt_on(tools, mode_state)),
            SummarizationMiddleware(
                model=f"openai:{settings.summary_model}",
                trigger=("tokens", _SUMMARY_TRIGGER_TOKENS),
                keep=("messages", _SUMMARY_KEEP_MESSAGES),
            ),
        ],
    )
    return BuiltAgent(
        agent=agent,
        tools=tools,
        role=session.role,
        total_tools=len(all_tools),
        user_id=session.user_id,
        mode_state=mode_state,
    )


# Self-test: `uv run python agent.py` connects and lists the tools visible to this role.
if __name__ == "__main__":
    import asyncio

    from auth import login

    _settings = load_settings()
    built = asyncio.run(build_agent(_settings, login(_settings)))
    print(f"Connected as {built.role}. {len(built.tools)} of {built.total_tools} tools available:")
    for tool in built.tools:
        print(" -", tool.name)
