"""HTTP entry point for the agent: the browser sends a user's message here directly.

The browser calls these endpoints cross-origin with the user's access token in an
'Authorization: Bearer' header. We verify that token (RS256, public key only), read the
trusted identity (role / id) from its signed claims, and build a per-request agent for that
user, so each caller gets their own role-filtered tools and persistent memory.

Because writes pause through the checkpointer (not a blocking console prompt), an approval
is returned to the caller and resumed later via /chat/resume on the same conversation.

Run it with:  uv run python api.py      (or: uv run uvicorn api:app --reload)
"""

import json
import logging
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Any, Literal

import jwt
import openai
import uvicorn
from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from langchain_core.messages import AIMessage, AIMessageChunk, HumanMessage
from langgraph.types import Command
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel
from sse_starlette import EventSourceResponse

from agent import BuiltAgent, _get_checkpointer, build_agent
from approval import ApprovalMode
from auth import Session, verify_token
from settings import Settings, load_settings
from usage import UsageLimitError, check_allowed, usage_snapshot

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
    """A user's message. Identity comes from the VERIFIED JWT in the Authorization header,
    never from plain body fields."""

    message: str = Field(description="The user's message to the assistant.")
    mode: str = Field(description="Approval mode (required): auto | agent | ask.", min_length=1)


class Decision(CamelModel):
    """One human answer to one pending write (order matches the returned 'pending' list).

    * approve -> run the action as proposed.
    * edit    -> describe the change in plain words in `message`; the agent revises and asks again.
    * reject  -> cancel the action; `message` is an optional reason.
    """

    type: Literal["approve", "edit", "reject"]
    message: str | None = None


class ResumeRequest(CamelModel):
    """The decisions for a paused conversation. Identity comes from the verified JWT, as for /chat."""

    decisions: list[Decision]
    mode: str = Field(description="Approval mode (required): auto | agent | ask. Send the same mode the conversation is using.", min_length=1)


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

    cursor: int | None = Field(default=None, description="nextCursor from the previous page; omit for the newest page.")


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


class UsageResponse(CamelModel):
    """The caller's daily AI token budget — a read-only meter (mirrors Spring's usage response)."""

    used: int = Field(description="Tokens used today (counted in UTC).")
    limit: int = Field(description="Daily token cap for the caller's role, or -1 when uncapped.")
    remaining: int = Field(description="Tokens left today, or -1 when uncapped.")
    resets_at: datetime = Field(description="When the daily counter resets (next UTC midnight).")


# --------------------------------------------------------------------------- app setup


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
        "AI agent chat API. The browser calls it directly with a Bearer access token, which is "
        "verified here (RS256, public key only) before any tools run."
    ),
    version="1.0.0",
    lifespan=lifespan,
    openapi_tags=[
        {"name": "chat", "description": "Send a message and resume after an approval pause."},
        {"name": "meta", "description": "Health and diagnostics."},
    ],
)

# Browser-direct calls are cross-origin (local dev, and the Amplify site vs the api host in prod). Auth
# rides in the Authorization header, NOT a cookie, so we do not use credentialed CORS — which lets dev
# use a wildcard origin (set BIBEXPO_CORS_ALLOWED_ORIGINS='*' to test from a phone on the same Wi-Fi).
# In prod set it to the exact Amplify origin. The frontend must stream with fetch() (EventSource cannot
# send headers), which triggers the CORS preflight this handles.
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_allowed_origins,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
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


@app.exception_handler(UsageLimitError)
async def _usage_limited(request: Request, exc: UsageLimitError) -> JSONResponse:
    """Turn a spent daily budget into a 429 with the user-facing message (checked before streaming)."""
    return JSONResponse(status_code=429, content={"detail": str(exc)})


@app.exception_handler(Exception)
async def _unhandled_exception(request: Request, exc: Exception) -> JSONResponse:
    """Last-resort handler: log the full traceback and return a clean 500 (never a stack trace)."""
    logger.exception("unhandled error on %s %s", request.method, request.url.path)
    return JSONResponse(status_code=500, content={"detail": "The AI assistant hit an unexpected error."})


# --------------------------------------------------------------------------- helpers

# Tool-call argument keys whose values must never be sent to the client in the clear.
_SENSITIVE_ARG_KEYS = {"password", "secret", "token", "api_key", "apikey", "auth_token", "authtoken"}


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


def _sse(event: str, payload: dict) -> dict:
    """One Server-Sent Event for sse-starlette: a named event carrying a JSON data line."""
    return {"event": event, "data": json.dumps(payload)}


async def _stream_run(session: Session, payload: object, mode: str | None):
    """Run one step and yield SSE events: 'token' deltas as the answer forms, then either
    'needs_approval' (a write is paused for sign-off) or 'done' (final reply). Once streaming has
    begun the HTTP status is already sent, so a failure becomes an 'error' event, not an HTTP error.
    """
    try:
        built = await build_agent(settings, session)
        try:
            _set_mode(built, mode)
        except ValueError:
            yield _sse("error", {"detail": "mode must be auto, agent or ask."})
            return
        thread_id = _thread_id(session.user_id)
        config = {"configurable": {"thread_id": thread_id}}
        if not isinstance(payload, Command):
            await _discard_stale_approval(built.agent, config, thread_id)

        reply_parts: list[str] = []
        pending: list[PendingAction] = []
        async for stream_mode, data in built.agent.astream(
            payload, config=config, stream_mode=["updates", "messages"]
        ):
            if stream_mode == "messages":
                chunk, meta = data
                # Stream only the main model's answer pieces; skip the summarization model's tokens
                # (it runs on a different model) so a background compaction never leaks into the chat.
                if isinstance(chunk, AIMessageChunk) and meta.get("ls_model_name") != settings.summary_model:
                    text = _text(chunk.content)
                    if text:
                        reply_parts.append(text)
                        yield _sse("token", {"text": text})
            elif stream_mode == "updates" and isinstance(data, dict):
                interrupts = data.get("__interrupt__")
                if interrupts:
                    pending = _all_pending(interrupts)

        if pending:
            logger.info("thread=%s stream needs approval for %d action(s)", thread_id, len(pending))
            yield _sse("needs_approval", {
                "pending": [p.model_dump(by_alias=True) for p in pending],
                "threadId": thread_id,
            })
        else:
            reply = "".join(reply_parts)
            if not reply:  # nothing streamed (rare): fall back to the final stored message
                snapshot = await built.agent.aget_state(config)
                messages = snapshot.values.get("messages") if snapshot and snapshot.values else None
                if messages:
                    reply = _text(messages[-1].content)
            logger.info("thread=%s stream complete", thread_id)
            yield _sse("done", {"reply": reply, "threadId": thread_id})
    except openai.RateLimitError as exc:
        logger.warning("OpenAI rate limit during stream: %s", exc)
        yield _sse("error", {"detail": "The assistant is busy right now, please try again in a few seconds."})
    except Exception:
        logger.exception("unhandled error during stream")
        yield _sse("error", {"detail": "The AI assistant hit an unexpected error."})


def _bearer(request: Request) -> str | None:
    """The token from an 'Authorization: Bearer <jwt>' header, or None when absent."""
    header = request.headers.get("authorization", "")
    if header[:7].lower() == "bearer ":
        return header[7:].strip() or None
    return None


def _verified(token: str) -> Session:
    """Verify a JWT or fail the request with 401 (the crypto reason is logged, never returned)."""
    try:
        return verify_token(token, settings)
    except jwt.InvalidTokenError as exc:
        logger.info("token rejected: %s", exc)
        raise HTTPException(status_code=401, detail="Your session is invalid or has expired. Please log in again.")


def _authenticate(request: Request) -> Session:
    """Identity for a chat call, from the VERIFIED Bearer access token in the Authorization header."""
    bearer = _bearer(request)
    if not bearer:
        raise HTTPException(status_code=401, detail="Missing credentials.")
    return _verified(bearer)


def _authenticated_user_id(request: Request) -> int:
    """Trusted user id for a thread read/delete, from the verified Bearer token, so a user can
    only ever touch their own conversation."""
    return _authenticate(request).user_id


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
async def chat(req: ChatRequest, request: Request) -> ChatResponse:
    session = _authenticate(request)
    check_allowed(settings, session.user_id, session.role)
    logger.info("POST /chat user=%s role=%s mode=%s len=%d", session.user_id, session.role, req.mode, len(req.message))
    return await _run(session, {"messages": [HumanMessage(content=req.message)]}, req.mode)


@app.post("/chat/resume", tags=["chat"], summary="Resume the AI agent after an approval pause")
async def resume(req: ResumeRequest, request: Request) -> ChatResponse:
    session = _authenticate(request)
    check_allowed(settings, session.user_id, session.role)
    logger.info("POST /chat/resume user=%s mode=%s decisions=%d", session.user_id, req.mode, len(req.decisions))
    decisions = [_to_decision(d) for d in req.decisions]
    return await _run(session, Command(resume={"decisions": decisions}), req.mode)


# Documented once for both streaming endpoints so Swagger explains the SSE event vocabulary.
_SSE_DESCRIPTION = (
    "Server-Sent Events stream (text/event-stream). Event types:\n"
    "- `token` — `{text}`: a piece of the answer; append as it arrives.\n"
    "- `needs_approval` — `{pending[], threadId}`: a write awaits sign-off; the stream ends, then "
    "call the resume-stream endpoint with one decision per pending action.\n"
    "- `done` — `{reply, threadId}`: the final assembled reply.\n"
    "- `error` — `{detail}`: a failure after streaming began.\n\n"
    "Consume with fetch() streaming, not EventSource (the token must travel in the Authorization "
    "header). Auth: `Authorization: Bearer <accessToken>`."
)


@app.post(
    "/chat/stream",
    tags=["chat"],
    summary="Send a message to the AI agent (streamed via SSE)",
    description=_SSE_DESCRIPTION,
)
async def chat_stream(req: ChatRequest, request: Request) -> EventSourceResponse:
    session = _authenticate(request)
    check_allowed(settings, session.user_id, session.role)
    logger.info("POST /chat/stream user=%s role=%s mode=%s len=%d", session.user_id, session.role, req.mode, len(req.message))
    payload = {"messages": [HumanMessage(content=req.message)]}
    return EventSourceResponse(_stream_run(session, payload, req.mode), ping=15)


@app.post(
    "/chat/resume/stream",
    tags=["chat"],
    summary="Resume after an approval pause (streamed via SSE)",
    description=_SSE_DESCRIPTION,
)
async def resume_stream(req: ResumeRequest, request: Request) -> EventSourceResponse:
    session = _authenticate(request)
    check_allowed(settings, session.user_id, session.role)
    logger.info("POST /chat/resume/stream user=%s mode=%s decisions=%d", session.user_id, req.mode, len(req.decisions))
    decisions = [_to_decision(d) for d in req.decisions]
    return EventSourceResponse(_stream_run(session, Command(resume={"decisions": decisions}), req.mode), ping=15)


@app.delete("/chat/memory", tags=["chat"], summary="Delete a user's conversation memory (start fresh)")
async def reset_memory(request: Request) -> Response:
    """Wipe one user's stored conversation so their next message starts with empty history.

    Reaches the shared checkpointer directly — no agent is built, since a delete needs no
    tools or model — and removes every checkpoint and write for thread user-<id>. Idempotent:
    deleting an already-empty thread is a no-op and still returns 204.
    """
    user_id = _authenticated_user_id(request)
    thread_id = _thread_id(user_id)
    logger.info("DELETE /chat/memory user=%s thread=%s", user_id, thread_id)
    await _get_checkpointer(settings).adelete_thread(thread_id)
    return Response(status_code=204)


@app.post("/chat/history", tags=["chat"], summary="Fetch a page of stored conversation history")
async def history(req: HistoryRequest, request: Request) -> HistoryResponse:
    """Return a page of the user's prior messages so the frontend can restore the chat after a reload.

    Reads the saved messages from the checkpointer, drops tool-call noise, and slices the newest 25
    not yet seen (the client walks older with the returned cursor). The summary divider, if present,
    is the oldest stored line, so paging stops there.
    """
    user_id = _authenticated_user_id(request)
    thread_id = _thread_id(user_id)
    messages = await _load_messages(thread_id)
    outcome = _paginate_history(_to_history(messages), req.cursor)
    logger.info("POST /chat/history user=%s returned=%d hasMore=%s", user_id, len(outcome.messages), outcome.has_more)
    return outcome


@app.get("/chat/usage", tags=["chat"], summary="Get the caller's daily AI token usage")
async def usage(request: Request) -> UsageResponse:
    """Read-only meter for the signed-in user: tokens used, the role cap, what remains, and when it
    resets (next UTC midnight). Never consumes budget and never returns 429. Identity comes from the
    Bearer access token."""
    bearer = _bearer(request)
    if not bearer:
        raise HTTPException(status_code=401, detail="Missing credentials.")
    session = _verified(bearer)
    return UsageResponse(**usage_snapshot(settings, session.user_id, session.role))


if __name__ == "__main__":
    uvicorn.run(app, host=settings.api_host, port=settings.api_port)
