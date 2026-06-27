from dataclasses import dataclass

from langchain.agents import create_agent
from langchain_mcp_adapters.client import MultiServerMCPClient
from langgraph.checkpoint.memory import MemorySaver

from auth import login
from settings import Settings, load_settings
from tool_visibility import visible_tools


@dataclass
class BuiltAgent:
    """What build_agent hands back: the agent plus a small summary of the session.

    Bundling these in one named object (instead of a long tuple) keeps the call site
    readable, the same way Session did for login.
    """

    agent: object        # the LangGraph agent to invoke
    tools: list          # tools the agent may use (already filtered by role)
    role: str            # the signed-in user's role
    total_tools: int     # tools the server offered before filtering


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


async def build_agent(settings: Settings) -> BuiltAgent:
    """Log in, load the MCP tools over SSE, filter them by role, and assemble the agent.

    Returns a BuiltAgent (the agent plus a summary of the session).
    """
    session = login(settings)

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

    agent = create_agent(
        model=f"openai:{settings.openai_model}",
        tools=tools,
        system_prompt=SYSTEM_PROMPT,
        checkpointer=MemorySaver(),
    )
    return BuiltAgent(
        agent=agent,
        tools=tools,
        role=session.role,
        total_tools=len(all_tools),
    )


# Self-test: `uv run python agent.py` connects and lists the tools visible to this role.
if __name__ == "__main__":
    import asyncio

    built = asyncio.run(build_agent(load_settings()))
    print(f"Connected as {built.role}. {len(built.tools)} of {built.total_tools} tools available:")
    for tool in built.tools:
        print(" -", tool.name)
