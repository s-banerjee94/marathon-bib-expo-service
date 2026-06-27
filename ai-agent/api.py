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

import json
from typing import Any, Literal

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from langchain_core.messages import HumanMessage
from langgraph.types import Command
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

from agent import build_agent
from approval import ApprovalMode
from auth import Session
from settings import Settings, load_settings

settings: Settings = load_settings()
app = FastAPI(title="Marathon Bib Expo AI Agent")


class CamelModel(BaseModel):
    """Base for request bodies: accept camelCase JSON (userId), keep snake_case in Python.

    populate_by_name also lets the original snake_case names through, so both work.
    """

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ChatRequest(CamelModel):
    """A user's message plus the identity Spring vouches for, all in one JSON body."""

    token: str  # the user's JWT, forwarded by Spring (carried in the body, not a header)
    message: str
    user_id: int
    role: str
    organization_id: int | None = None
    mode: str | None = None  # optional per-request override: auto | agent | ask
    internal_secret: str | None = None  # shared secret; only checked when one is configured


class Decision(CamelModel):
    """One human answer to one pending write (order matches the returned 'pending' list)."""

    type: Literal["approve", "reject", "edit", "respond"]
    message: str | None = None
    edited_action: dict[str, Any] | None = None


class ResumeRequest(CamelModel):
    """The decisions for a paused conversation, plus the same identity as the chat call."""

    token: str
    user_id: int
    role: str
    organization_id: int | None = None
    decisions: list[Decision]
    internal_secret: str | None = None


def _require_secret(provided: str | None) -> None:
    """Reject anyone but Spring when a shared secret is configured."""
    if settings.internal_secret and provided != settings.internal_secret:
        raise HTTPException(status_code=401, detail="Invalid internal secret.")


def _pending_actions(hitl: dict) -> list[dict]:
    """Build the list of writes awaiting approval from a HITL interrupt payload."""
    configs = hitl.get("review_configs", [])
    return [
        {
            "name": action["name"],
            "args": action["args"],
            "description": action.get("description"),
            "allowedDecisions": (
                configs[i]["allowed_decisions"] if i < len(configs) else ["approve", "reject"]
            ),
        }
        for i, action in enumerate(hitl["action_requests"])
    ]


def _format(result: dict, thread_id: str) -> dict:
    """Turn an agent result into either a final reply or an approval request."""
    interrupts = result.get("__interrupt__")
    if interrupts:
        pending = _pending_actions(interrupts[0].value)
        return {"status": "needs_approval", "pending": pending, "threadId": thread_id}
    return {"status": "complete", "reply": result["messages"][-1].content, "threadId": thread_id}


async def _run(session: Session, payload: object, mode: str | None) -> dict:
    """Build this user's agent and run one step (a new message or a resume)."""
    built = await build_agent(settings, session)
    if mode:
        try:
            built.mode_state.mode = ApprovalMode(mode.strip().lower())
        except ValueError:
            raise HTTPException(status_code=400, detail="mode must be auto, agent or ask.")
    thread_id = f"user-{session.user_id}"
    config = {"configurable": {"thread_id": thread_id}}
    result = await built.agent.ainvoke(payload, config=config)
    return _format(result, thread_id)


def _sse(event: str, data: dict) -> str:
    """Format one Server-Sent Event: an 'event:' line and a JSON 'data:' line."""
    return f"event: {event}\ndata: {json.dumps(data)}\n\n"


def _tool_events(chunk: dict):
    """Yield a 'tool' SSE event for each tool call the model proposes in an updates chunk."""
    for update in chunk.values():
        if not isinstance(update, dict):
            continue
        for message in update.get("messages", []) or []:
            for call in getattr(message, "tool_calls", None) or []:
                yield _sse("tool", {"name": call.get("name"), "args": call.get("args", {})})


async def _event_stream(session: Session, payload: object, mode: str | None):
    """Run the agent and yield SSE events as it works: 'tool' when it calls a tool, 'token' for
    each piece of the answer, then either an 'interrupt' (a write needs approval) or a final
    'done'. Any failure is sent as an 'error' event, since once streaming has started we can no
    longer change the HTTP status code.
    """
    try:
        built = await build_agent(settings, session)
        if mode:
            try:
                built.mode_state.mode = ApprovalMode(mode.strip().lower())
            except ValueError:
                yield _sse("error", {"detail": "mode must be auto, agent or ask."})
                return
        thread_id = f"user-{session.user_id}"
        config = {"configurable": {"thread_id": thread_id}}
        reply_parts: list[str] = []

        # Two stream modes at once: "messages" gives token-by-token model output; "updates"
        # carries node results, which is where tool calls and an approval interrupt show up.
        async for stream_mode, chunk in built.agent.astream(
            payload, config=config, stream_mode=["updates", "messages"]
        ):
            if stream_mode == "messages":
                message_chunk, _meta = chunk
                text = message_chunk.content
                if isinstance(text, str) and text:
                    reply_parts.append(text)
                    yield _sse("token", {"text": text})
            elif stream_mode == "updates":
                if "__interrupt__" in chunk:
                    pending = _pending_actions(chunk["__interrupt__"][0].value)
                    yield _sse("interrupt", {"pending": pending, "threadId": thread_id})
                    return
                for event in _tool_events(chunk):
                    yield event
        yield _sse("done", {"threadId": thread_id, "reply": "".join(reply_parts)})
    except Exception as exc:  # noqa: BLE001 - report any failure to the client as an event
        yield _sse("error", {"detail": str(exc)})


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}


@app.post("/chat")
async def chat(req: ChatRequest) -> dict:
    _require_secret(req.internal_secret)
    session = Session(
        token=req.token,
        user_id=req.user_id,
        role=req.role,
        organization_id=req.organization_id,
    )
    return await _run(session, {"messages": [HumanMessage(content=req.message)]}, req.mode)


@app.post("/chat/stream")
async def chat_stream(req: ChatRequest) -> StreamingResponse:
    _require_secret(req.internal_secret)
    session = Session(
        token=req.token,
        user_id=req.user_id,
        role=req.role,
        organization_id=req.organization_id,
    )
    return StreamingResponse(
        _event_stream(session, {"messages": [HumanMessage(content=req.message)]}, req.mode),
        media_type="text/event-stream",
    )


@app.post("/chat/resume")
async def resume(req: ResumeRequest) -> dict:
    _require_secret(req.internal_secret)
    session = Session(
        token=req.token,
        user_id=req.user_id,
        role=req.role,
        organization_id=req.organization_id,
    )
    decisions = [d.model_dump(exclude_none=True) for d in req.decisions]
    return await _run(session, Command(resume={"decisions": decisions}), mode=None)


@app.post("/chat/resume/stream")
async def resume_stream(req: ResumeRequest) -> StreamingResponse:
    _require_secret(req.internal_secret)
    session = Session(
        token=req.token,
        user_id=req.user_id,
        role=req.role,
        organization_id=req.organization_id,
    )
    decisions = [d.model_dump(exclude_none=True) for d in req.decisions]
    return StreamingResponse(
        _event_stream(session, Command(resume={"decisions": decisions}), mode=None),
        media_type="text/event-stream",
    )


if __name__ == "__main__":
    uvicorn.run(app, host=settings.api_host, port=settings.api_port)
