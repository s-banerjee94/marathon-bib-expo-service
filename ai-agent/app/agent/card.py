"""Render the human-readable approval card for a pending write.

A pending tool call carries only what the tool needs — numeric ids and camelCase argument
names — which is meaningless to the person approving it. A small structured-output model
call rewrites it for display: labels become plain English and ids become the names the
agent already looked up earlier in the conversation (the system prompt guarantees that
lookup happened). If the render call fails for any reason, a mechanical formatter still
produces a readable card, so an approval is never blocked by cosmetics.
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
from dataclasses import dataclass
from typing import Any

from langchain.agents import create_agent
from langchain.chat_models import init_chat_model
from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from ..core.settings import Settings
from .approval import is_read_tool
from .usage import UsageTrackingMiddleware, record_usage

logger = logging.getLogger(__name__)

# Tool-call argument keys whose values must never leave the server in the clear — masked in
# the displayed summary, in the args echoed to the client, and in the render model's input.
SENSITIVE_ARG_KEYS = {"password", "secret", "token", "api_key", "apikey", "auth_token", "authtoken"}

_MASK = "••••"

# How much conversation the renderer sees: enough to cover the lookups the agent ran just
# before proposing the write (where the id→name pairs live), small enough to stay cheap.
_CONTEXT_MESSAGES = 15
_CONTEXT_CHARS_PER_MESSAGE = 1500

# The record directory mined from ALL stored tool results (id→name pairs survive even when the
# lookup happened long before the write and has scrolled out of the recent-tail window).
_DIRECTORY_MAX_RECORDS = 40
_NAME_KEYS = frozenset({"name", "fullName", "full_name", "title", "username", "label"})

# A slow render must not hold the approval hostage; past this the fallback card is used.
_RENDER_TIMEOUT_SECONDS = 15

# The lookup escalation may run a few real read tools, so it gets a longer (but still hard) cap.
_RESOLVE_TIMEOUT_SECONDS = 30


def redact_args(args: dict) -> dict:
    """Mask sensitive values (passwords, tokens) anywhere in a tool call's args.

    Recurses into nested dicts, since tool args are commonly wrapped as {"request": {...}}.
    """
    masked: dict = {}
    for key, value in args.items():
        if key.lower() in SENSITIVE_ARG_KEYS:
            masked[key] = _MASK
        elif isinstance(value, dict):
            masked[key] = redact_args(value)
        else:
            masked[key] = value
    return masked


def text_content(content: object) -> str:
    """Flatten a message's content to a plain string.

    Model content is usually a plain string, but can be a list of parts (multimodal);
    we keep only the text so callers always get a renderable string.
    """
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = [p if isinstance(p, str) else p.get("text", "") for p in content if isinstance(p, (str, dict))]
        return "".join(parts)
    return "" if content is None else str(content)


def _unwrap(args: dict) -> dict:
    """Peel the common {"request": {...}} wrapper so only the real fields are displayed."""
    inner = args.get("request") if set(args) == {"request"} and isinstance(args.get("request"), dict) else args
    return inner if isinstance(inner, dict) else args


def _humanize(key: str) -> str:
    """Turn a camelCase/snake_case argument key into a plain label: recipientPhone -> Recipient phone."""
    words = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", key.replace("_", " ")).split()
    if not words:
        return key
    sentence = " ".join(word.lower() for word in words)
    return sentence[0].upper() + sentence[1:]


@dataclass
class RenderedCard:
    """Everything the client needs to display one pending action.

    `fields` carries per-argument display data (exact args key + plain label + resolved
    value) so the frontend can label its edit form and show names instead of ids; `summary`
    is the same content pre-rendered as markdown for the simple display path.
    """

    title: str                    # plain header, e.g. "Invite User" (never the raw tool name)
    fields: list[dict[str, str]]  # [{key, label, value}] — key matches the args key verbatim
    summary: str                  # the card as ready-to-render markdown


def _card_markdown(title: str, rows: list[tuple[str, Any]]) -> str:
    """The one card shape both render paths produce: a bold title plus labelled lines."""
    lines = [f"**{title}** — please review and confirm:"]
    lines += [f"- **{label}**: {value}" for label, value in rows]
    return "\n".join(lines)


def _as_card(title: str, fields: list[dict[str, str]]) -> RenderedCard:
    """Assemble the final card from display fields (markdown derived, so the two never drift)."""
    return RenderedCard(
        title=title,
        fields=fields,
        summary=_card_markdown(title, [(f["label"], f["value"]) for f in fields]),
    )


def tool_title(name: str) -> str:
    """A tool's display title from its raw name: create_event -> Create Event.

    Shared by the approval card and the enable/disable tool list so the two never drift.
    """
    return name.replace("_", " ").strip().title()


def fallback_card(name: str, args: dict) -> RenderedCard:
    """Readable card without any model call: humanised labels, values shown as-is (secrets masked).

    This is what a stale-approval sweep uses (no display needed) and what the user sees if
    the model render fails — ids stay raw there, but the card always appears.
    """
    title = tool_title(name)
    inner = _unwrap(redact_args(args))
    fields = [{"key": key, "label": _humanize(key), "value": str(value)} for key, value in inner.items()]
    return _as_card(title, fields)


class _CardRow(BaseModel):
    """One display row of the approval card."""

    key: str = Field(description="The argument key exactly as given (e.g. 'organizationId') — copy it verbatim, never rename or invent keys.")
    label: str = Field(description="Plain-English field label, e.g. 'Organization' — never camelCase or snake_case.")
    value: str = Field(description="Human-readable value; the entity's name instead of its numeric id whenever the conversation reveals it.")


class _ApprovalCard(BaseModel):
    """The whole card: a short title plus one row per argument."""

    title: str = Field(description="Short action title, e.g. 'Invite User'.")
    rows: list[_CardRow] = Field(description="One row per argument, in the given order.")


_RENDER_INSTRUCTIONS = """\
You write the approval card a person sees before a proposed action in the Marathon Bib Expo
application runs. You are given the tool name, its raw arguments, and the recent conversation.

Rules:
- Produce one row per argument, in the given order. Never add, drop or merge fields.
- Each row's key is that argument's key copied verbatim (e.g. "organizationId").
- Labels are plain English ("recipientPhone" -> "Recipient phone"), never camelCase or snake_case.
- When an argument is a numeric id, check the "Records seen in this conversation" list and the
  conversation itself for which record it refers to (a search or get result pairing that id with
  a name); show the record's name as the value and drop the number. Use only names actually
  present in that material — never guess.
- When no name for an id appears anywhere in the given material, keep the id as the value.
- Copy phones, emails, dates, roles and other literal values exactly, including letter case
  (DISTRIBUTOR stays DISTRIBUTOR); keep "••••" masked.
"""

# Built once and shared: the render model is user-independent (the per-user data rides in the
# prompt), and binding the schema is pure setup — same lazy-singleton pattern as the checkpointer.
_card_model: Any = None


def _get_card_model(settings: Settings) -> Any:
    global _card_model
    if _card_model is None:
        model = init_chat_model(f"openai:{settings.summary_model}")
        # include_raw keeps the raw AIMessage so the call's token usage can be recorded.
        _card_model = model.with_structured_output(_ApprovalCard, include_raw=True)
    return _card_model


def _conversation_extract(messages: list) -> str:
    """The recent conversation tail as plain text, so the renderer can find id→name pairs."""
    lines: list[str] = []
    for message in messages[-_CONTEXT_MESSAGES:]:
        text = text_content(getattr(message, "content", None))
        if not text.strip():
            continue
        role = getattr(message, "type", "message")
        lines.append(f"{role}: {text[:_CONTEXT_CHARS_PER_MESSAGE]}")
    return "\n".join(lines)


def _iter_dicts(node: object):
    """Every dict inside a parsed tool result, however the records are nested (lists, envelopes)."""
    if isinstance(node, dict):
        yield node
        for value in node.values():
            yield from _iter_dicts(value)
    elif isinstance(node, list):
        for item in node:
            yield from _iter_dicts(item)


def _record_line(tool: str, record: dict) -> str | None:
    """One directory line for a record that pairs at least one id with at least one name."""
    ids = {k: v for k, v in record.items() if (k == "id" or k.endswith("Id")) and v is not None}
    names = {k: v for k, v in record.items() if (k in _NAME_KEYS or k.endswith("Name")) and v}
    if not ids or not names:
        return None
    return f"{tool}: {json.dumps({**ids, **names}, ensure_ascii=False, default=str)}"


def _known_records(messages: list) -> str:
    """A compact id→name directory mined from EVERY stored tool result, oldest to newest.

    The recent-tail extract misses lookups that happened many turns ago; this scan does not,
    so an id resolved once stays resolvable for the rest of the conversation (up to the
    summarization boundary). Non-JSON tool results are skipped; newest records win the cap.
    """
    lines: list[str] = []
    seen: set[str] = set()
    for message in messages:
        if getattr(message, "type", None) != "tool":
            continue
        try:
            payload = json.loads(text_content(getattr(message, "content", None)))
        except (ValueError, TypeError):
            continue
        tool = getattr(message, "name", None) or "tool"
        for record in _iter_dicts(payload):
            line = _record_line(tool, record)
            if line and line not in seen:
                seen.add(line)
                lines.append(line)
    return "\n".join(lines[-_DIRECTORY_MAX_RECORDS:])


def _render_input(name: str, args: dict, messages: list) -> str:
    """The one prompt body both render tiers receive: masked args + record directory + tail."""
    return (
        f"Tool: {name}\n"
        f"Arguments:\n{json.dumps(_unwrap(redact_args(args)), ensure_ascii=False, indent=2)}\n\n"
        f"Records seen in this conversation (id → details):\n{_known_records(messages) or '(none)'}\n\n"
        f"Recent conversation (oldest first):\n{_conversation_extract(messages)}"
    )


def _is_id_key(key: str) -> bool:
    return key == "id" or key.endswith("Id") or key.endswith("_id")


def _unresolved_id_keys(fields: list[dict[str, str]], inner_args: dict) -> list[str]:
    """Id arguments whose rendered value is still just the number (no name was found)."""
    unresolved: list[str] = []
    for field in fields:
        key = field["key"]
        if not _is_id_key(key):
            continue
        value = field["value"].strip()
        raw = str(inner_args.get(key, "")).strip()
        if value == raw or not re.search(r"[A-Za-z]", value):
            unresolved.append(key)
    return unresolved


def _entity_stem(key: str) -> str:
    """The entity word an id key refers to, as a match stem: eventId -> 'event', categoryId -> 'categor'."""
    base = key[:-3] if key.endswith("_id") else key[:-2] if key.endswith("Id") else ""
    base = base.replace("_", "").lower()
    return base[:-1] if base.endswith("y") else base  # category -> categor (matches 'categories')


def _lookup_tools(tools: list, unresolved_keys: list[str]) -> list:
    """The read-only tools that can resolve the unresolved entities (matched by name, generically).

    Only reads are ever eligible (they need no approval and change nothing), and only those
    whose name mentions the entity — so the resolver model gets a handful of schemas, not all.
    """
    stems = {stem for stem in (_entity_stem(key) for key in unresolved_keys) if stem}
    if not stems:
        return []
    return [
        tool
        for tool in tools or []
        if is_read_tool(tool.name) and any(stem in tool.name.lower() for stem in stems)
    ]


_RESOLVE_INSTRUCTIONS = _RENDER_INSTRUCTIONS + """\

Some ids in the arguments are not named anywhere in the given material. You have read-only
lookup tools: use them to fetch each such record (get it by id, or search and match the id;
any parent ids a lookup needs are in the arguments), then use that record's name as the row's
value. If a lookup fails or finds nothing for an id, keep the id as the value. Do nothing with
the tools beyond these lookups, and produce the final card when done.
"""


async def _resolve_card(
    settings: Settings, user_id: int | None, name: str, args: dict, messages: list, tools: list
) -> RenderedCard | None:
    """Second tier: a tiny read-only agent that looks up the missing records, then emits the card.

    Runs with the user's own tool bindings, so every lookup is RBAC-checked server-side as that
    user. Stateless (no checkpointer): reads never pause, and nothing is persisted.
    """
    agent = create_agent(
        model=f"openai:{settings.summary_model}",
        tools=tools,
        system_prompt=_RESOLVE_INSTRUCTIONS,
        response_format=_ApprovalCard,
        middleware=[UsageTrackingMiddleware(settings, user_id)],
    )
    result = await asyncio.wait_for(
        agent.ainvoke({"messages": [HumanMessage(content=_render_input(name, args, messages))]}),
        _RESOLVE_TIMEOUT_SECONDS,
    )
    card = result.get("structured_response")
    if card is None:
        return None
    return _as_card(card.title, [{"key": r.key, "label": r.label, "value": r.value} for r in card.rows])


async def _record_render_usage(settings: Settings, user_id: int | None, raw: Any) -> None:
    """Count the render call's tokens against the user's daily budget; never let it fail the card."""
    if user_id is None or raw is None:
        return
    usage = getattr(raw, "usage_metadata", None)
    total = usage.get("total_tokens") if usage else None
    if not total:
        return
    try:
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(None, record_usage, settings, user_id, int(total))
    except Exception as exc:  # noqa: BLE001 — usage accounting must never break a card
        logger.warning("could not record card-render usage for user %s: %s", user_id, exc)


async def render_card(
    settings: Settings, user_id: int | None, name: str, args: dict, messages: list, tools: list | None = None
) -> RenderedCard:
    """The display card for one pending action: model-rendered, or the mechanical fallback.

    Two tiers. Tier 1 (always): one structured-output call that resolves ids from the
    conversation material; the model never writes the markdown itself, so the card shape stays
    fixed. Tier 2 (only when tier 1 leaves an id numeric AND matching read tools were given):
    a read-only lookup agent fetches the missing records so EVERY entity id resolves to a name.
    Any failure at any tier degrades to the previous tier's card — approvals never block.
    """
    try:
        prompt = [
            SystemMessage(content=_RENDER_INSTRUCTIONS),
            HumanMessage(content=_render_input(name, args, messages)),
        ]
        result = await asyncio.wait_for(
            _get_card_model(settings).ainvoke(prompt), _RENDER_TIMEOUT_SECONDS
        )
        await _record_render_usage(settings, user_id, result.get("raw"))
        card = result.get("parsed")
        if card is None:
            raise ValueError(f"model returned no parsable card: {result.get('parsing_error')}")
        fields = [{"key": row.key, "label": row.label, "value": row.value} for row in card.rows]
        rendered = _as_card(card.title, fields)

        unresolved = _unresolved_id_keys(fields, _unwrap(redact_args(args)))
        lookup_tools = _lookup_tools(tools, unresolved) if unresolved else []
        if lookup_tools:
            try:
                resolved = await _resolve_card(settings, user_id, name, args, messages, lookup_tools)
                if resolved is not None:
                    return resolved
            except Exception as exc:  # noqa: BLE001 — escalation is best-effort on top of tier 1
                logger.warning("approval-card lookup escalation failed for tool %s: %s", name, exc)
        return rendered
    except Exception as exc:  # noqa: BLE001 — a display render must never block an approval
        logger.warning("approval-card render failed for tool %s: %s", name, exc)
        return fallback_card(name, args)
