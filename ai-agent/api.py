"""HTTP entry point for the agent: Spring forwards a user's message here.

The browser never calls this directly. Spring authenticates the user, then calls these
endpoints server-to-server, forwarding the user's JWT and trusted identity (role / id)
together in a single JSON body (this is an internal service-to-service API, so the token
travels in the body rather than an Authorization header). We build a per-request agent for
that user, so each caller gets their own role-filtered tools and persistent memory.

Because writes pause through the checkpointer (not a blocking console prompt), an approval
is returned to the caller and resumed later via /chat/resume on the same conversation.

Run it with:  uv run python api.py      (or: uv run uvicorn api:app --reload)
"""

import hmac
import logging
from contextlib import asynccontextmanager
from typing import Any, Literal

import openai
import uvicorn
from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.responses import JSONResponse
from langchain_core.messages import AIMessage, HumanMessage
from langgraph.types import Command
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from agent import BuiltAgent, _get_checkpointer, build_agent
from approval import ApprovalMode
from auth import Session
from settings import Settings, load_settings

# One logging setup for the whole service. Works whether started via `python api.py` or
# `uvicorn api:app`; our app logs and uvicorn's request logs both land on the console.
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s | %(message)s",
)
logger = logging.getLogger("agent")

settings: Settings = load_settings()


# --------------------------------------------------------------------------- models


class CamelModel(BaseModel):
    """Base for request/response bodies: camelCase JSON on the wire, snake_case in Python.

    populate_by_name also lets the original snake_case names through, so both work on input.
    """

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ChatRequest(CamelModel):
    """A user's message plus the identity Spring vouches for, all in one JSON body."""

    token: str = Field(description="The user's JWT, forwarded by Spring (in the body, not a header).")
    message: str = Field(description="The user's message to the assistant.")
    user_id: int = Field(description="Trusted user id minted by Spring.")
    role: str = Field(description="Trusted role minted by Spring.")
    organization_id: int | None = Field(default=None, description="The user's organization, if any.")
    mode: str = Field(description="Approval mode (required): auto | agent | ask.", min_length=1)
    internal_secret: str | None = Field(default=None, description="Shared secret; checked only when configured.")


class Decision(CamelModel):
    """One human answer to one pending write (order matches the returned 'pending' list).

    * approve -> run the action as proposed.
    * edit    -> describe the change in plain words in `message`; the agent revises and asks again.
    * reject  -> cancel the action; `message` is an optional reason.
    """

    type: Literal["approve", "edit", "reject"]
    message: str | None = None


class ResumeRequest(CamelModel):
    """The decisions for a paused conversation, plus the same identity as the chat call."""

    token: str
    user_id: int
    role: str
    organization_id: int | None = None
    mode: str = Field(description="Approval mode (required): auto | agent | ask. Send the same mode the conversation is using.", min_length=1)
    decisions: list[Decision]
    internal_secret: str | None = None


class ResetRequest(CamelModel):
    """Identity for a conversation reset: just the trusted user id whose thread to wipe.

    No token or message is needed — a delete makes no LLM/MCP call. Spring derives user_id
    from the trusted identity, so a user can only ever reset their own conversation.
    """

    user_id: int = Field(description="Trusted user id minted by Spring; selects the thread to delete.")
    internal_secret: str | None = Field(default=None, description="Shared secret; checked only when configured.")


class PendingAction(CamelModel):
    """One write awaiting approval: markdown to display + the decision buttons to offer."""

    id: str = Field(description="Stable handle for this action within the turn.")
    name: str = Field(description="Tool the agent wants to run.")
    summary: str = Field(description="Markdown summary of the action to show the user.")
    args: dict[str, Any] = Field(description="Proposed tool args (secrets masked); shown so the user can see what to confirm or change.")
    actions: list[str] = Field(description="Decision buttons to render (e.g. approve, reject, edit).")


class ChatResponse(CamelModel):
    """The fixed reply shape for /chat and /chat/resume; all fields always present."""

    status: Literal["complete", "needs_approval"] = Field(description="complete = answer in reply; needs_approval = decisions required for pending.")
    reply: str = Field(description="The assistant's markdown answer; '' when status is needs_approval.")
    pending: list[PendingAction] = Field(description="Writes awaiting approval; [] when status is complete.")
    thread_id: str = Field(description="Conversation thread id (always user-<id>).")


class HistoryRequest(CamelModel):
    """Ask for a page of a user's stored conversation, newest first."""

    user_id: int = Field(description="Trusted user id minted by Spring; selects the thread to read.")
    cursor: int | None = Field(default=None, description="nextCursor from the previous page; omit for the newest page.")
    internal_secret: str | None = Field(default=None, description="Shared secret; checked only when configured.")


class HistoryMessage(CamelModel):
    """One restorable line of history: a real user/assistant turn, or a summarization divider."""

    role: Literal["user", "assistant"] = Field(description="Who said it.")
    content: str = Field(description="The message text, in markdown.")
    summary: bool = Field(default=False, description="True when this marks where earlier turns were summarized.")


class HistoryResponse(CamelModel):
    """A page of history (oldest->newest within the page) plus how to load older messages."""

    messages: list[HistoryMessage] = Field(description="The page's messages, oldest first; prepend above what you have.")
    next_cursor: int | None = Field(description="Pass back as 'cursor' to load the previous page; null when no more.")
    has_more: bool = Field(description="Whether older messages remain to load.")


# --------------------------------------------------------------------------- app setup

# A shared secret is mandatory: Spring must send it on every call and the agent verifies it.
# Without it, anyone who could reach the port could impersonate any user, so fail fast at import
# time rather than serve unauthenticated.
if not settings.internal_secret:
    raise RuntimeError(
        "BIBEXPO_INTERNAL_SECRET must be set; the agent requires a shared secret on every request."
    )


@asynccontextmanager
async def lifespan(_: FastAPI):
    """Log a clear line on startup and shutdown (modern replacement for on_event hooks)."""
    logger.info(
        "AI agent starting (host=%s port=%s model=%s)",
        settings.api_host,
        settings.api_port,
        settings.openai_model,
    )
    yield
    logger.info("AI agent shutting down")


app = FastAPI(
    title="Marathon Bib Expo AI Agent",
    description=(
        "Internal service-to-service API. Spring authenticates the user and forwards the message "
        "in the request body; the browser never calls this directly."
    ),
    version="1.0.0",
    lifespan=lifespan,
    openapi_tags=[
        {"name": "chat", "description": "Send a message and resume after an approval pause."},
        {"name": "meta", "description": "Health and diagnostics."},
    ],
)


@app.exception_handler(openai.RateLimitError)
async def _rate_limited(request: Request, exc: openai.RateLimitError) -> JSONResponse:
    """Turn OpenAI's 429 into an intentional 'busy' reply instead of a generic 500.

    The OpenAI SDK already retries a 429 a few times with backoff; this fires only once those are
    exhausted. Two cases share the 429: transient throughput throttling (TPM/RPM), which clears on
    its own within seconds, and an exhausted account quota, which needs a credit top-up and will not
    fix itself. We answer 429 for both but word them differently and log the quota case louder.
    """
    if getattr(exc, "code", None) == "insufficient_quota":
        logger.error("OpenAI quota exhausted on %s %s: %s", request.method, request.url.path, exc)
        detail = "The AI assistant is temporarily unavailable. Please try again later."
    else:
        logger.warning("OpenAI rate limit on %s %s: %s", request.method, request.url.path, exc)
        detail = "The assistant is busy right now, please try again in a few seconds."
    return JSONResponse(status_code=429, content={"detail": detail})


@app.exception_handler(Exception)
async def _unhandled_exception(request: Request, exc: Exception) -> JSONResponse:
    """Last-resort handler: log the full traceback and return a clean 500 (never a stack trace)."""
    logger.exception("unhandled error on %s %s", request.method, request.url.path)
    return JSONResponse(status_code=500, content={"detail": "The AI assistant hit an unexpected error."})


# --------------------------------------------------------------------------- helpers

# Tool-call argument keys whose values must never be sent to the client in the clear.
_SENSITIVE_ARG_KEYS = {"password", "secret", "token", "api_key", "apikey", "auth_token", "authtoken"}


def _require_secret(provided: str | None) -> None:
    """Reject anyone but Spring: every request must carry the shared secret.

    The secret is mandatory (enforced at startup), so there is no "unset" bypass. Uses a
    constant-time compare so the secret cannot be guessed by timing the response.
    """
    if not provided or not hmac.compare_digest(provided, settings.internal_secret):
        raise HTTPException(status_code=401, detail="Invalid internal secret.")


def _redact_args(args: dict) -> dict:
    """Mask sensitive values (passwords, tokens) before a pending action's args go to the client.

    Recurses into nested dicts, since tool args are commonly wrapped as {"request": {...}}.
    """
    masked: dict = {}
    for key, value in args.items():
        if key.lower() in _SENSITIVE_ARG_KEYS:
            masked[key] = "****"
        elif isinstance(value, dict):
            masked[key] = _redact_args(value)
        else:
            masked[key] = value
    return masked


def _text(content: object) -> str:
    """Flatten a message's content to a markdown string.

    Model content is usually a plain string, but can be a list of parts (multimodal);
    we keep only the text so the frontend always receives a renderable markdown string.
    """
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = [p if isinstance(p, str) else p.get("text", "") for p in content if isinstance(p, (str, dict))]
        return "".join(parts)
    return "" if content is None else str(content)


def _summarize(name: str, args: dict) -> str:
    """Render a human-readable **markdown** summary of one pending write, for display.

    The frontend shows this verbatim (markdown) and only needs the JSON `actions` for buttons.
    Tool args are often wrapped as {"request": {...}}; we unwrap that and mask secrets.
    """
    pretty = name.replace("_", " ").strip().title()
    inner = args.get("request") if set(args) == {"request"} and isinstance(args.get("request"), dict) else args
    lines = [f"**{pretty}** — please review and confirm:"]
    if isinstance(inner, dict) and inner:
        for key, value in inner.items():
            shown = "••••" if key.lower() in _SENSITIVE_ARG_KEYS else value
            lines.append(f"- **{key}**: {shown}")
    return "\n".join(lines)


def _pending_actions(hitl: dict) -> list[PendingAction]:
    """Build the writes awaiting approval from one HITL interrupt payload.

    Fixed shape per action: a markdown `summary` (what to show) + a small JSON `actions`
    list (the buttons). `args` is kept (masked) so an 'edit' decision has the original values.
    """
    requests = hitl.get("action_requests") or []
    configs = hitl.get("review_configs") or []
    pending: list[PendingAction] = []
    for i, action in enumerate(requests):
        name = action.get("name", "action")
        args = action.get("args") or {}
        allowed = configs[i].get("allowed_decisions") if i < len(configs) else None
        pending.append(
            PendingAction(
                id=action.get("id") or f"action-{i}",
                name=name,
                summary=_summarize(name, args),
                args=_redact_args(args),
                actions=allowed or ["approve", "edit", "reject"],
            )
        )
    return pending


def _all_pending(interrupts: object) -> list[PendingAction]:
    """Flatten every pending action across all interrupts (not just the first)."""
    pending: list[PendingAction] = []
    for interrupt in interrupts or []:
        value = getattr(interrupt, "value", interrupt)
        if isinstance(value, dict):
            pending.extend(_pending_actions(value))
    return pending


def _format(result: dict, thread_id: str) -> ChatResponse:
    """Turn an agent result into the fixed ChatResponse: a final reply or an approval request."""
    interrupts = result.get("__interrupt__")
    if interrupts:
        pending = _all_pending(interrupts)
        logger.info("thread=%s needs approval for %d action(s)", thread_id, len(pending))
        return ChatResponse(status="needs_approval", reply="", pending=pending, thread_id=thread_id)
    reply = _text(result["messages"][-1].content)
    return ChatResponse(status="complete", reply=reply, pending=[], thread_id=thread_id)


def _set_mode(built: BuiltAgent, mode: str | None) -> None:
    """Apply an optional per-request approval override; raises ValueError if invalid."""
    if mode:
        built.mode_state.mode = ApprovalMode(mode.strip().lower())


def _state_interrupts(snapshot: object) -> list:
    """Pending approval interrupts on a thread's current state, if any."""
    interrupts = getattr(snapshot, "interrupts", None)
    if interrupts:
        return list(interrupts)
    pending: list = []
    for task in getattr(snapshot, "tasks", ()) or ():
        pending.extend(getattr(task, "interrupts", ()) or ())
    return pending


async def _discard_stale_approval(agent: object, config: dict, thread_id: str) -> None:
    """Auto-reject an unanswered approval so a *new* message can't corrupt the history.

    If the user (or a misbehaving caller) sends a new message while a write is still awaiting
    approval, the stored history would keep an assistant tool-call with no tool response, which
    OpenAI rejects on every later turn. We pre-empt that by rejecting the abandoned action(s)
    first — a write the user walked away from should not run anyway.
    """
    snapshot = await agent.aget_state(config)
    interrupts = _state_interrupts(snapshot)
    if not interrupts:
        return
    pending = _all_pending(interrupts)
    if not pending:
        logger.warning("thread=%s paused on an unrecognised interrupt; cannot auto-clear", thread_id)
        return
    decisions = [{"type": "reject", "message": "Cancelled because a new message was sent."} for _ in pending]
    logger.info("thread=%s discarding %d stale pending action(s) before new message", thread_id, len(pending))
    await agent.ainvoke(Command(resume={"decisions": decisions}), config=config)


async def _run(session: Session, payload: object, mode: str | None) -> ChatResponse:
    """Build this user's agent and run one step (a new message or a resume)."""
    built = await build_agent(settings, session)
    try:
        _set_mode(built, mode)
    except ValueError:
        raise HTTPException(status_code=400, detail="mode must be auto, agent or ask.")
    thread_id = _thread_id(session.user_id)
    config = {"configurable": {"thread_id": thread_id}}
    # A new message (not a resume) must not arrive while an approval is still pending, or the
    # dangling tool-call corrupts the thread; clear it first so the conversation self-heals.
    if not isinstance(payload, Command):
        await _discard_stale_approval(built.agent, config, thread_id)
    result = await built.agent.ainvoke(payload, config=config)
    outcome = _format(result, thread_id)
    logger.info("thread=%s run complete: status=%s", thread_id, outcome.status)
    return outcome


def _session(req: ChatRequest | ResumeRequest) -> Session:
    """The authenticated identity Spring vouched for, lifted out of the request body."""
    return Session(
        token=req.token,
        user_id=req.user_id,
        role=req.role,
        organization_id=req.organization_id,
    )


def _thread_id(user_id: int) -> str:
    """The single conversation thread id for a user (the agent keeps one conversation per user)."""
    return f"user-{user_id}"


def _to_decision(decision: Decision) -> dict[str, str]:
    """Translate one client decision into the LangChain middleware's decision payload.

    The middleware runs a tool only on "approve". An "edit" is phrased by the user in plain
    words, not as replacement args, so we hand it back as guided feedback: don't run this,
    apply the change, and confirm again — the model then re-proposes and pauses once more.
    A "reject" simply cancels. Both edit and reject reach the middleware as a "reject"; only
    the message differs.
    """
    if decision.type == "approve":
        return {"type": "approve"}
    if decision.type == "edit":
        change = (decision.message or "").strip()
        if not change:
            raise HTTPException(status_code=400, detail="An edit needs a message describing the change.")
        return {
            "type": "reject",
            "message": (
                "Do not run this action as proposed. The user asked for this change: "
                f"{change}. Apply it and ask for confirmation again."
            ),
        }
    return {"type": "reject", "message": decision.message or "The user cancelled this action."}


# History is fixed at 25 messages per page; the client only sends a cursor, never a page size.
_HISTORY_PAGE_SIZE = 25


def _is_summary(message: object) -> bool:
    """True if this is the marker the summarization middleware leaves where it compacted history."""
    extra = getattr(message, "additional_kwargs", None) or {}
    return extra.get("lc_source") == "summarization"


def _to_history(messages: list) -> list[HistoryMessage]:
    """Keep only restorable lines: real user/assistant turns and the summarization divider.

    Tool calls, tool results and system text are dropped as noise. The summary marker is a
    HumanMessage, so it must be checked before the plain-user case.
    """
    history: list[HistoryMessage] = []
    for message in messages:
        text = _text(message.content)
        if _is_summary(message):
            history.append(HistoryMessage(role="assistant", content=text, summary=True))
        elif isinstance(message, HumanMessage):
            if text.strip():
                history.append(HistoryMessage(role="user", content=text))
        elif isinstance(message, AIMessage):
            if text.strip():  # skip tool-call-only assistant turns (no visible text)
                history.append(HistoryMessage(role="assistant", content=text))
    return history


def _paginate_history(items: list[HistoryMessage], cursor: int | None) -> HistoryResponse:
    """Slice the newest unseen 25 from a chronological list; cursor counts messages already shown."""
    offset = cursor or 0
    end = max(0, len(items) - offset)
    start = max(0, end - _HISTORY_PAGE_SIZE)
    page = items[start:end]
    has_more = start > 0
    return HistoryResponse(
        messages=page,
        next_cursor=(offset + len(page)) if has_more else None,
        has_more=has_more,
    )


async def _load_messages(thread_id: str) -> list:
    """Read a thread's stored messages straight from the checkpointer (no agent/MCP build)."""
    config = {"configurable": {"thread_id": thread_id}}
    snapshot = await _get_checkpointer(settings).aget_tuple(config)
    if snapshot is None:
        return []
    return snapshot.checkpoint.get("channel_values", {}).get("messages", [])


# --------------------------------------------------------------------------- endpoints


@app.get("/health", tags=["meta"], summary="Liveness check")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/chat", tags=["chat"], summary="Send a message to the AI agent")
async def chat(req: ChatRequest) -> ChatResponse:
    _require_secret(req.internal_secret)
    logger.info("POST /chat user=%s role=%s mode=%s len=%d", req.user_id, req.role, req.mode, len(req.message))
    return await _run(_session(req), {"messages": [HumanMessage(content=req.message)]}, req.mode)


@app.post("/chat/resume", tags=["chat"], summary="Resume the AI agent after an approval pause")
async def resume(req: ResumeRequest) -> ChatResponse:
    _require_secret(req.internal_secret)
    logger.info("POST /chat/resume user=%s mode=%s decisions=%d", req.user_id, req.mode, len(req.decisions))
    decisions = [_to_decision(d) for d in req.decisions]
    return await _run(_session(req), Command(resume={"decisions": decisions}), req.mode)


@app.delete("/chat/memory", tags=["chat"], summary="Delete a user's conversation memory (start fresh)")
async def reset_memory(req: ResetRequest) -> Response:
    """Wipe one user's stored conversation so their next message starts with empty history.

    Reaches the shared checkpointer directly — no agent is built, since a delete needs no
    tools or model — and removes every checkpoint and write for thread user-<id>. Idempotent:
    deleting an already-empty thread is a no-op and still returns 204.
    """
    _require_secret(req.internal_secret)
    thread_id = _thread_id(req.user_id)
    logger.info("DELETE /chat/memory user=%s thread=%s", req.user_id, thread_id)
    await _get_checkpointer(settings).adelete_thread(thread_id)
    return Response(status_code=204)


@app.post("/chat/history", tags=["chat"], summary="Fetch a page of stored conversation history")
async def history(req: HistoryRequest) -> HistoryResponse:
    """Return a page of the user's prior messages so the frontend can restore the chat after a reload.

    Reads the saved messages from the checkpointer, drops tool-call noise, and slices the newest 25
    not yet seen (the client walks older with the returned cursor). The summary divider, if present,
    is the oldest stored line, so paging stops there.
    """
    _require_secret(req.internal_secret)
    thread_id = _thread_id(req.user_id)
    messages = await _load_messages(thread_id)
    outcome = _paginate_history(_to_history(messages), req.cursor)
    logger.info("POST /chat/history user=%s returned=%d hasMore=%s", req.user_id, len(outcome.messages), outcome.has_more)
    return outcome


if __name__ == "__main__":
    uvicorn.run(app, host=settings.api_host, port=settings.api_port)
