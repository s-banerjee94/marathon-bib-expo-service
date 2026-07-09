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
    mcp_enabled: bool | None = Field(
        default=None,
        description="True/false to set for this turn; omit (null) to use the user's saved setting. "
        "False = offer no tools this turn (pure chat); no tool schemas reach the model.",
    )
    disabled_tools: list[str] | None = Field(
        default=None,
        description="Tool names to hide this turn (token saving); omit (null) to use the saved "
        "setting. Ignored when mcpEnabled is false.",
    )
    attachment_ids: list[str] | None = Field(
        default=None,
        description="Ids returned by POST /chat/attachments for files (image/PDF) the assistant "
        "should read with this message; omit (null) or [] for a text-only message. At most 2.",
    )


class AttachmentInfo(CamelModel):
    """One stored upload the client echoes back in a chat message's attachmentIds."""

    attachment_id: str = Field(description="Send this back in ChatRequest.attachmentIds to attach the file.")
    kind: Literal["image", "pdf"] = Field(description="What the file is, so the UI can show the right icon.")
    filename: str = Field(description="The original file name.")
    mime_type: str = Field(description="The file's MIME type (e.g. image/png, application/pdf).")
    size_bytes: int = Field(description="The stored file size in bytes.")
    url: str = Field(
        description="Short-lived presigned GET URL to display the file right away — the same URL "
        "source /chat/history returns, so the composer and the transcript share one render path.",
    )


class AttachmentUploadResponse(CamelModel):
    """The result of POST /chat/attachments: one info per accepted file, in upload order."""

    attachments: list[AttachmentInfo] = Field(description="The accepted files; pass their ids to /chat.")


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
    mcp_enabled: bool | None = Field(
        default=None,
        description="Send the same value used to start the conversation (or omit to use the saved "
        "setting); must keep the pending tool visible.",
    )
    disabled_tools: list[str] | None = Field(
        default=None,
        description="Send the same value used to start the conversation (or omit to use the saved "
        "setting); must not include the pending tool.",
    )


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


class HistoryAttachment(CamelModel):
    """A file attached to a stored user message, with a short-lived URL to display it."""

    kind: Literal["image", "pdf"] = Field(description="image = render inline; pdf = show as a link/preview.")
    filename: str = Field(description="Original file name.")
    mime_type: str = Field(description="The file's MIME type (e.g. image/png, application/pdf).")
    url: str = Field(description="Short-lived presigned GET URL; may 404 if the file has expired or was deleted.")


class HistoryMessage(CamelModel):
    """One restorable line of history: a real user/assistant turn, or a summarization divider."""

    role: Literal["user", "assistant"] = Field(description="Who said it.")
    content: str = Field(description="The message text, in markdown.")
    summary: bool = Field(default=False, description="True when this marks where earlier turns were summarized.")
    attachments: list[HistoryAttachment] = Field(
        default_factory=list,
        description="Files on this user turn (render thumbnails from these); [] when none. The "
        "'(attached: …)' text in `content` is a plain fallback you may hide when showing thumbnails.",
    )


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


class ToolInfo(CamelModel):
    """One tool the signed-in user may see, for the enable/disable toggle UI (GET /chat/tools)."""

    name: str = Field(description="Internal tool name; send it back in disabledTools to hide it.")
    label: str = Field(description="Human-friendly label to show in the toggle (e.g. 'Create Event').")


class ToolsResponse(CamelModel):
    """The enable/disable UI payload: the tools this role may use plus the user's saved state.

    Returned by GET /chat/tools (current saved state) and by PUT /chat/tools (the state just saved),
    so the frontend can render the toggle already reflecting the user's preference.
    """

    tools: list[ToolInfo] = Field(description="Tools the signed-in user's role may enable or disable.")
    mcp_enabled: bool = Field(description="The user's saved master switch (true = tools on).")
    disabled_tools: list[str] = Field(description="The user's saved list of hidden tool names.")


class ToolPrefsRequest(CamelModel):
    """Save the user's assistant-tool choice (full replace); persists across sessions and devices."""

    mcp_enabled: bool = Field(default=True, description="False = no tools at all (pure chat).")
    disabled_tools: list[str] = Field(
        default_factory=list, description="Tool names to keep hidden; ignored when mcpEnabled is false."
    )
