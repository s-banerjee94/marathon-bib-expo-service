"""Chat endpoints: send a message, resume after an approval pause, read/reset history, read usage.

Each call verifies the caller's Bearer token, builds a per-request agent for that user, and runs
one step. Writes pause through the checkpointer (not a blocking prompt), so an approval is returned
to the caller and resumed later via the resume endpoint on the same conversation.
"""

import json
import logging
from typing import Any

import openai
from fastapi import APIRouter, HTTPException, Request, Response
from langchain_core.messages import AIMessage, AIMessageChunk, HumanMessage
from langgraph.types import Command
from sse_starlette import EventSourceResponse

from ..agent.approval import ASK_USER_TOOL, ApprovalMode
from ..agent.builder import BuiltAgent, _get_checkpointer, build_agent
from ..agent.card import fallback_card, redact_args, render_card, text_content
from ..agent.usage import check_allowed, usage_snapshot
from ..core.auth import Session
from ..core.settings import settings
from ..dependencies import authenticate, authenticated_user_id
from ..schemas import (
    ChatRequest,
    ChatResponse,
    Decision,
    HistoryMessage,
    HistoryRequest,
    HistoryResponse,
    PendingAction,
    PendingField,
    PendingResponse,
    ResumeRequest,
    UsageResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter(tags=["chat"])


# --------------------------------------------------------------------------- helpers


def _pending_actions(hitl: dict) -> list[PendingAction]:
    """Build the writes awaiting approval from one HITL interrupt payload.

    Fixed shape per action: a markdown `summary` (what to show) + a small JSON `actions`
    list (the buttons). `args` is kept (masked) so an 'edit' decision has the original values.
    The summary starts as the mechanical fallback; display paths upgrade it via _render_pending.
    """
    requests = hitl.get("action_requests") or []
    configs = hitl.get("review_configs") or []
    pending: list[PendingAction] = []
    for i, action in enumerate(requests):
        name = action.get("name", "action")
        args = action.get("args") or {}
        allowed = configs[i].get("allowed_decisions") if i < len(configs) else None
        card = fallback_card(name, args)
        pending.append(
            PendingAction(
                id=action.get("id") or f"action-{i}",
                name=name,
                title=card.title,
                summary=card.summary,
                fields=[PendingField(**field) for field in card.fields],
                args=redact_args(args),
                actions=allowed or ["approve", "edit", "reject"],
            )
        )
    return pending


async def _render_pending(
    pending: list[PendingAction], messages: list, user_id: int | None, tools: list | None
) -> None:
    """Upgrade each write's display (title, fields, summary) to the model-rendered card.

    `tools` are the caller's own role-filtered bindings: the renderer may run read-only
    lookups with them so every entity id resolves to a name. ask_user is skipped (the frontend
    builds its picker from args), and a failed render keeps the mechanical fallback.
    """
    for action in pending:
        if action.name == ASK_USER_TOOL:
            continue
        card = await render_card(settings, user_id, action.name, action.args, messages, tools)
        action.title = card.title
        action.summary = card.summary
        action.fields = [PendingField(**field) for field in card.fields]


def _all_pending(interrupts: object) -> list[PendingAction]:
    """Flatten every pending action across all interrupts (not just the first)."""
    pending: list[PendingAction] = []
    for interrupt in interrupts or []:
        value = getattr(interrupt, "value", interrupt)
        if isinstance(value, dict):
            pending.extend(_pending_actions(value))
    return pending


async def _format(result: dict, thread_id: str, user_id: int | None, tools: list | None) -> ChatResponse:
    """Turn an agent result into the fixed ChatResponse: a final reply or an approval request."""
    interrupts = result.get("__interrupt__")
    if interrupts:
        pending = _all_pending(interrupts)
        await _render_pending(pending, result.get("messages") or [], user_id, tools)
        logger.info("thread=%s needs approval for %d action(s)", thread_id, len(pending))
        return ChatResponse(status="needs_approval", reply="", pending=pending, thread_id=thread_id)
    reply = text_content(result["messages"][-1].content)
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


# One conversation per user: only one agent run may execute at a time. A second concurrent
# request (typically the same user sending from another browser tab) is rejected outright,
# not queued, so two messages can never interleave into the one shared conversation. This is
# LangGraph's "reject" double-texting strategy, hand-rolled because the open-source framework
# only ships multitask_strategy on the managed Platform.
_in_flight_users: set[int] = set()


def _begin_run(user_id: int) -> None:
    """Claim the user's single run slot, or reject with 409 if a run is already in progress."""
    if user_id in _in_flight_users:
        logger.info("rejecting concurrent run for user=%s (one already in progress)", user_id)
        raise HTTPException(
            status_code=409,
            detail="Please wait for your current message to finish before sending another.",
        )
    _in_flight_users.add(user_id)


def _end_run(user_id: int) -> None:
    """Release the user's run slot; always called from a finally, including when a stream ends."""
    _in_flight_users.discard(user_id)


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
    outcome = await _format(result, thread_id, session.user_id, built.tools)
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
                    text = text_content(chunk.content)
                    if text:
                        reply_parts.append(text)
                        yield _sse("token", {"text": text})
            elif stream_mode == "updates" and isinstance(data, dict):
                interrupts = data.get("__interrupt__")
                if interrupts:
                    pending = _all_pending(interrupts)

        if pending:
            # The renderer needs the saved conversation (where the id→name lookups live).
            snapshot = await built.agent.aget_state(config)
            messages = (getattr(snapshot, "values", None) or {}).get("messages") or []
            await _render_pending(pending, messages, session.user_id, built.tools)
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
                    reply = text_content(messages[-1].content)
            logger.info("thread=%s stream complete", thread_id)
            yield _sse("done", {"reply": reply, "threadId": thread_id})
    except openai.RateLimitError as exc:
        logger.warning("OpenAI rate limit during stream: %s", exc)
        yield _sse("error", {"detail": "The assistant is busy right now, please try again in a few seconds."})
    except Exception:
        logger.exception("unhandled error during stream")
        yield _sse("error", {"detail": "The AI assistant hit an unexpected error."})


async def _guarded_stream(user_id: int, session: Session, payload: object, mode: str | None):
    """Wrap _stream_run so the user's single-flight slot is released once the stream ends.

    The slot is claimed in the endpoint (so a concurrent request gets a real 409 before any
    streaming starts); this releases it when the stream finishes, errors, or the client drops.
    """
    try:
        async for event in _stream_run(session, payload, mode):
            yield event
    finally:
        _end_run(user_id)


def _thread_id(user_id: int) -> str:
    """The single conversation thread id for a user (the agent keeps one conversation per user)."""
    return f"user-{user_id}"


def _to_decision(decision: Decision) -> dict[str, Any]:
    """Translate one client decision into the LangChain middleware's decision payload.

    The four types map straight through to the middleware:
      * approve -> run the tool as proposed.
      * edit    -> run the tool with the user's corrected values (edited_action from the form).
                   args pass through verbatim, so the client must keep the tool's original arg
                   structure (including any {"request": {...}} wrapper) and omit masked secrets.
      * reject  -> do not run the tool; message is optional feedback the model reads next.
      * respond -> the user's message IS the tool result (only for the ask_user question tool).
    """
    if decision.type == "approve":
        return {"type": "approve"}
    if decision.type == "edit":
        edited = decision.edited_action
        if edited is None:
            raise HTTPException(status_code=400, detail="An edit needs the corrected values.")
        return {"type": "edit", "edited_action": {"name": edited.name, "args": edited.args}}
    if decision.type == "respond":
        answer = (decision.message or "").strip()
        if not answer:
            raise HTTPException(status_code=400, detail="A response needs an answer.")
        return {"type": "respond", "message": answer}
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
        text = text_content(message.content)
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


# --------------------------------------------------------------------------- endpoints


@router.post("/chat", summary="Send a message to the AI agent")
async def chat(req: ChatRequest, request: Request) -> ChatResponse:
    session = authenticate(request)
    check_allowed(settings, session.user_id, session.role)
    logger.info("POST /chat user=%s role=%s mode=%s len=%d", session.user_id, session.role, req.mode, len(req.message))
    _begin_run(session.user_id)
    try:
        return await _run(session, {"messages": [HumanMessage(content=req.message)]}, req.mode)
    finally:
        _end_run(session.user_id)


@router.post("/chat/resume", summary="Resume the AI agent after an approval pause")
async def resume(req: ResumeRequest, request: Request) -> ChatResponse:
    session = authenticate(request)
    check_allowed(settings, session.user_id, session.role)
    logger.info("POST /chat/resume user=%s mode=%s decisions=%d", session.user_id, req.mode, len(req.decisions))
    decisions = [_to_decision(d) for d in req.decisions]
    _begin_run(session.user_id)
    try:
        return await _run(session, Command(resume={"decisions": decisions}), req.mode)
    finally:
        _end_run(session.user_id)


@router.post(
    "/chat/stream",
    summary="Send a message to the AI agent (streamed via SSE)",
    description=_SSE_DESCRIPTION,
)
async def chat_stream(req: ChatRequest, request: Request) -> EventSourceResponse:
    session = authenticate(request)
    check_allowed(settings, session.user_id, session.role)
    logger.info("POST /chat/stream user=%s role=%s mode=%s len=%d", session.user_id, session.role, req.mode, len(req.message))
    _begin_run(session.user_id)
    payload = {"messages": [HumanMessage(content=req.message)]}
    return EventSourceResponse(_guarded_stream(session.user_id, session, payload, req.mode), ping=15)


@router.post(
    "/chat/resume/stream",
    summary="Resume after an approval pause (streamed via SSE)",
    description=_SSE_DESCRIPTION,
)
async def resume_stream(req: ResumeRequest, request: Request) -> EventSourceResponse:
    session = authenticate(request)
    check_allowed(settings, session.user_id, session.role)
    logger.info("POST /chat/resume/stream user=%s mode=%s decisions=%d", session.user_id, req.mode, len(req.decisions))
    decisions = [_to_decision(d) for d in req.decisions]
    _begin_run(session.user_id)
    payload = Command(resume={"decisions": decisions})
    return EventSourceResponse(_guarded_stream(session.user_id, session, payload, req.mode), ping=15)


@router.delete("/chat/memory", summary="Delete a user's conversation memory (start fresh)")
async def reset_memory(request: Request) -> Response:
    """Wipe one user's stored conversation so their next message starts with empty history.

    Reaches the shared checkpointer directly — no agent is built, since a delete needs no
    tools or model — and removes every checkpoint and write for thread user-<id>. Idempotent:
    deleting an already-empty thread is a no-op and still returns 204.
    """
    user_id = authenticated_user_id(request)
    thread_id = _thread_id(user_id)
    logger.info("DELETE /chat/memory user=%s thread=%s", user_id, thread_id)
    await _get_checkpointer(settings).adelete_thread(thread_id)
    return Response(status_code=204)


@router.post("/chat/history", summary="Fetch a page of stored conversation history")
async def history(req: HistoryRequest, request: Request) -> HistoryResponse:
    """Return a page of the user's prior messages so the frontend can restore the chat after a reload.

    Reads the saved messages from the checkpointer, drops tool-call noise, and slices the newest 25
    not yet seen (the client walks older with the returned cursor). The summary divider, if present,
    is the oldest stored line, so paging stops there.
    """
    user_id = authenticated_user_id(request)
    thread_id = _thread_id(user_id)
    messages = await _load_messages(thread_id)
    outcome = _paginate_history(_to_history(messages), req.cursor)
    logger.info("POST /chat/history user=%s returned=%d hasMore=%s", user_id, len(outcome.messages), outcome.has_more)
    return outcome


@router.get("/chat/pending", summary="Get the write awaiting approval, if any (reload recovery)")
async def pending(request: Request) -> PendingResponse:
    """Return the write(s) currently awaiting this user's approval, so a reloaded page can restore
    the approval card. Reads the thread's saved state through the agent and returns the same
    PendingAction shape as /chat, or an idle response when nothing is paused.

    Read-only: never runs a tool, never resumes, and never consumes token budget. This is the
    self-hosted counterpart of LangGraph Server's thread-state read, narrowed to the pending interrupt.
    """
    session = authenticate(request)
    thread_id = _thread_id(session.user_id)
    built = await build_agent(settings, session)
    snapshot = await built.agent.aget_state({"configurable": {"thread_id": thread_id}})
    actions = _all_pending(_state_interrupts(snapshot))
    if actions:
        messages = (getattr(snapshot, "values", None) or {}).get("messages") or []
        await _render_pending(actions, messages, session.user_id, built.tools)
    status = "needs_approval" if actions else "idle"
    logger.info("GET /chat/pending user=%s status=%s count=%d", session.user_id, status, len(actions))
    return PendingResponse(status=status, pending=actions, thread_id=thread_id)


@router.get("/chat/usage", summary="Get the caller's daily AI token usage")
async def usage(request: Request) -> UsageResponse:
    """Read-only meter for the signed-in user: tokens used, the role cap, what remains, and when it
    resets (next UTC midnight). Never consumes budget and never returns 429. Identity comes from the
    Bearer access token."""
    session = authenticate(request)
    return UsageResponse(**usage_snapshot(settings, session.user_id, session.role))
