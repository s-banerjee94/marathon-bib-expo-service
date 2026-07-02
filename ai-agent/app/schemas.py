"""Request/response bodies for the chat API: camelCase on the wire, snake_case in Python."""

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


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


class EditedAction(CamelModel):
    """The user's corrected tool call for an `edit` decision: the same tool, new args.

    `args` must keep the exact structure the tool expects — including any {"request": {...}}
    wrapper — because the tool runs directly with these values. Do not send back masked
    placeholders ("••••") for secret fields; leave them out so real values are not overwritten.
    """

    name: str = Field(description="Tool to run — the same name as the reviewed action.")
    args: dict[str, Any] = Field(description="Corrected tool args, in the tool's original structure.")


class Decision(CamelModel):
    """One human answer to one pending action (order matches the returned 'pending' list).

    * approve -> run the action as proposed.
    * edit    -> run it with corrected values; put them in `edited_action`.
    * reject  -> cancel the action; `message` is an optional reason/feedback.
    * respond -> answer an ask_user question; `message` is the user's answer (used as the result).
    """

    type: Literal["approve", "edit", "reject", "respond"]
    message: str | None = None
    edited_action: EditedAction | None = None


class ResumeRequest(CamelModel):
    """The decisions for a paused conversation. Identity comes from the verified JWT, as for /chat."""

    decisions: list[Decision]
    mode: str = Field(description="Approval mode (required): auto | agent | ask. Send the same mode the conversation is using.", min_length=1)


class PendingField(CamelModel):
    """Display data for one argument of a pending action — for form labels and id-free values."""

    key: str = Field(description="The args key this field describes, verbatim (e.g. 'organizationId').")
    label: str = Field(description="Plain-English label to show (e.g. 'Organization').")
    value: str = Field(description="Display value — the record's name instead of its numeric id when known.")


class PendingAction(CamelModel):
    """One write awaiting approval: markdown to display + the decision buttons to offer."""

    id: str = Field(description="Stable handle for this action within the turn.")
    name: str = Field(description="Tool the agent wants to run (internal; send back on edit, don't display).")
    title: str = Field(description="Plain header for the card (e.g. 'Invite User') — show this, not `name`.")
    summary: str = Field(description="Markdown summary of the action to show the user.")
    fields: list[PendingField] = Field(description="Per-argument display data (labels + resolved values); match to args by `key`. Display-only — edit submissions must take values from `args`.")
    args: dict[str, Any] = Field(description="Proposed tool args (secrets masked); the source of truth for building an edit submission.")
    actions: list[str] = Field(description="Decision buttons to render (e.g. approve, reject, edit).")


class ChatResponse(CamelModel):
    """The fixed reply shape for /chat and /chat/resume; all fields always present."""

    status: Literal["complete", "needs_approval"] = Field(description="complete = answer in reply; needs_approval = decisions required for pending.")
    reply: str = Field(description="The assistant's markdown answer; '' when status is needs_approval.")
    pending: list[PendingAction] = Field(description="Writes awaiting approval; [] when status is complete.")
    thread_id: str = Field(description="Conversation thread id (always user-<id>).")


class PendingResponse(CamelModel):
    """The write(s) currently awaiting approval on the user's conversation, for reload recovery.

    Same PendingAction shape as /chat, so a reloaded page renders the identical approval card.
    """

    status: Literal["needs_approval", "idle"] = Field(description="needs_approval = pending has actions to decide; idle = nothing awaiting approval.")
    pending: list[PendingAction] = Field(description="Writes awaiting approval; [] when idle.")
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
