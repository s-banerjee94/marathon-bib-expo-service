from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import TYPE_CHECKING

import boto3
from langchain.agents import create_agent
from langchain.agents.middleware import HumanInTheLoopMiddleware, SummarizationMiddleware
from langchain_core.tools import tool
from langchain_mcp_adapters.sessions import create_session
from langchain_mcp_adapters.tools import convert_mcp_tool_to_langchain_tool
from langgraph_checkpoint_aws import DynamoDBSaver

from ..core.auth import Session
from ..core.settings import Settings
from .approval import ModeState, build_interrupt_on
from .tool_visibility import visible_tools
from .usage import UsageTrackingMiddleware

if TYPE_CHECKING:  # imported only for type hints, never at runtime
    from langchain_mcp_adapters.sessions import Connection
    from langgraph.graph.state import CompiledStateGraph
    from mcp import ClientSession
    from mcp.types import Tool as MCPTool

# Conversations expire from DynamoDB after this long without activity (mirrors the
# Java chat-memory TTL of 30 days).
_CHECKPOINT_TTL_SECONDS = 30 * 24 * 60 * 60

# When the running history approaches the model's working budget, older turns are condensed into a
# summary while the most recent few are kept verbatim (controls cost and TPM). We trigger at 75% of
# the configurable context budget — leaving headroom for the next message, the reply, tool output
# and the summary call itself — and keep only the last few messages.
_SUMMARY_TRIGGER_FRACTION = 0.75
_SUMMARY_KEEP_MESSAGES = 5


@dataclass
class BuiltAgent:
    """What build_agent hands back: the agent plus a small summary of the session.

    Bundling these in one named object (instead of a long tuple) keeps the call site
    readable, the same way Session did for login.
    """

    agent: CompiledStateGraph  # the LangGraph agent to invoke
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
several match, you MUST call the ask_user tool with those records as its options (each
labelled by a human detail such as date or city) so the user can pick one — never list the
matches yourself or ask the user to choose in prose; presenting a choice between records as
plain text instead of ask_user is not allowed. When none match, say so. A user may also
hand you a numeric id directly — accept it, but before calling any write tool, first fetch
that record with the matching get or search tool, so you have confirmed the id exists and
know the record by name. Never ask the user for a numeric id, and do not show raw ids
unless asked.

Be concise. For questions, just call the relevant read-only tool and report the result.
When the user asks you to create, change or remove data, gather the details you need and go
ahead. The application itself pauses and asks the user to approve a write when confirmation is
required, so do not ask the user to confirm again yourself; only ask a question when a detail
is genuinely missing or ambiguous.

Respect each tool's allowed values. For a field that accepts only a fixed set of options (for
example a role or status), use one of those exact values; if the user's wording does not match
an allowed value, call the ask_user tool with the allowed values so they can choose — do not
guess. If a tool call fails because a value is invalid, do not silently try again with another
guess: tell the user plainly what was wrong and, when the field has fixed choices, ask them to
pick a valid one.

Always answer in clean, conversational markdown — never raw JSON. Present a single record as
a few labelled lines (a bold title with its key details beneath); present several records as a
markdown table or a short bullet list. Use the human-friendly fields such as names, dates and
status, and leave out internal ids unless the user asks for them.
"""


@tool
def ask_user(question: str, options: list[str], allow_custom: bool = False) -> str:
    """Ask the signed-in user to choose when you cannot resolve something yourself.

    Call this only for a genuine choice the user must make — most often when a search by name
    returns several matches and you cannot tell which one they meant. Pass a short question and
    the candidate options as short human-readable strings; set allow_custom to true only when a
    free-text answer also makes sense. Do not call this for values you can look up with another
    tool, and never to confirm a write (the application already handles that approval).
    """
    # The user's reply is returned as this tool's result via the `respond` decision, so this
    # body does not run in normal operation; it only guards the unexpected no-pause case.
    return "No response was provided."


# Built once and shared by every request. The checkpointer wraps a boto3 DynamoDB client
# (comparatively expensive to create) and is user-independent — it keys conversations by
# thread_id only when invoked — so one instance serves everyone. The tool *schemas* (names,
# descriptions, input shapes) are identical for every caller, so we fetch them once too; only
# the per-request auth token differs, and that is re-bound on each request (never cached).
_checkpointer: DynamoDBSaver | None = None
_tool_schemas: list[MCPTool] | None = None
_tool_schemas_lock = asyncio.Lock()


def _get_checkpointer(settings: Settings) -> DynamoDBSaver:
    """Return the shared DynamoDB checkpointer, creating it on first use.

    boto3 picks up the LocalStack creds settings already loaded; endpoint_url points at
    LocalStack locally and is None (real AWS) in production, where profile is also None
    (EC2 instance role). The client is stateless and user-independent, so we build it once.
    """
    global _checkpointer
    if _checkpointer is None:
        boto_session = boto3.Session(
            region_name=settings.aws_region,
            profile_name=settings.aws_profile,
        )
        _checkpointer = DynamoDBSaver(
            table_name=settings.checkpoint_table,
            session=boto_session,
            endpoint_url=settings.ddb_endpoint_url,
            ttl_seconds=_CHECKPOINT_TTL_SECONDS,
        )
    return _checkpointer


async def _list_tool_schemas(session: ClientSession) -> list[MCPTool]:
    """Read every tool definition from an open MCP session, following pagination."""
    schemas: list[MCPTool] = []
    cursor: str | None = None
    while True:
        page = await session.list_tools(cursor=cursor)
        schemas.extend(page.tools)
        cursor = page.nextCursor
        if not cursor:
            break
    return schemas


async def _get_tool_schemas(connection: Connection) -> list[MCPTool]:
    """Return the MCP tool schemas, fetching them from the server once and caching them.

    The schemas are the same for every user, so the one-time network round trip rides on
    whichever request is first; its token only authenticates the *listing*, and the schemas
    it returns carry no token. The lock stops two early concurrent requests both fetching.
    """
    global _tool_schemas
    if _tool_schemas is None:
        async with _tool_schemas_lock:
            if _tool_schemas is None:
                async with create_session(connection) as session:
                    await session.initialize()
                    _tool_schemas = await _list_tool_schemas(session)
    return _tool_schemas


async def build_agent(settings: Settings, session: Session) -> BuiltAgent:
    """Build an agent for one already-authenticated user.

    The caller supplies the session (the user's token + role + id): the REPL gets it
    from login(), and the HTTP server gets it from the identity Spring forwards. This
    keeps "how we learned who the user is" out of agent assembly, so the same builder
    serves both the dev REPL and a per-request web call.

    Returns a BuiltAgent (the agent plus a summary of the session).
    """
    connection: Connection = {
        "transport": "sse",
        "url": settings.mcp_sse_url,
        "headers": {"Authorization": f"Bearer {session.token}"},
        # Fail fast if the MCP server is unreachable or a tool call hangs, instead of waiting
        # on the library's short 5s default (sse_read_timeout keeps its generous 5-min default).
        "timeout": settings.mcp_timeout_seconds,
    }
    schemas = await _get_tool_schemas(connection)

    # Wrap the cached schemas as LangChain tools bound to THIS request's connection, so every
    # tool call carries this user's own fresh token (the token is never cached — only the
    # schemas are). The MCP server sends every client the full list and cannot filter per user,
    # so we trim it next using the role from the trusted login response; the server still
    # enforces real access on every call as a backstop. Writes that survive the filter are still
    # gated by HumanInTheLoopMiddleware below, which pauses them for approval.
    all_tools = [
        convert_mcp_tool_to_langchain_tool(
            None, schema, connection=connection, server_name="bibexpo"
        )
        for schema in schemas
    ]
    # ask_user is a local UI tool (not from MCP), so every role gets it: appended after role
    # filtering so it is never trimmed out. It always pauses for a `respond` (see build_interrupt_on).
    tools = visible_tools(session.role, all_tools) + [ask_user]

    # Persist conversation state to DynamoDB so memory survives restarts (shared, built once).
    checkpointer = _get_checkpointer(settings)

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
                trigger=("tokens", int(_SUMMARY_TRIGGER_FRACTION * settings.context_budget_tokens)),
                keep=("messages", _SUMMARY_KEEP_MESSAGES),
            ),
            # Records each model call's token usage to the user's daily counter (Spring's budget gate).
            UsageTrackingMiddleware(settings, session.user_id),
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
