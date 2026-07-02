"""Store and read a user's AI-assistant preferences (currently the MCP tool enable/disable choice).

Persisted in DynamoDB beside the other per-user agent state, so a user's toggle sticks across
sessions and devices. One item per user:

    PK = USER#<id>   SK = PREFS#tools
    mcpEnabled (Bool)   disabledTools (List<S>)   updatedAt (S, ISO-8601)

Deliberately NO TTL — a saved setting must persist (unlike the usage/checkpoint tables). This is
Python-owned because the Python agent is the consumer on the hot path (see build_agent).
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any

import boto3

from ..core.settings import Settings

logger = logging.getLogger(__name__)

# A user's tool toggle lives under this sort key; a distinct SK leaves room for other pref groups.
_TOOLS_SK = "PREFS#tools"

def _default_prefs() -> dict:
    """What a caller gets when the user has saved nothing (or a read fails): tools on, none
    disabled. Built fresh per call so no two callers ever share the same mutable list."""
    return {"mcp_enabled": True, "disabled_tools": []}

# Building a boto3 client is comparatively expensive and the client is stateless, so cache one.
_client: Any = None


def _get_client(settings: Settings) -> Any:
    """Return the shared DynamoDB client (same creds/endpoint resolution as usage/checkpoints)."""
    global _client
    if _client is None:
        session = boto3.Session(region_name=settings.aws_region, profile_name=settings.aws_profile)
        _client = session.client("dynamodb", endpoint_url=settings.ddb_endpoint_url)
    return _client


def get_tool_prefs(settings: Settings, user_id: int | None) -> dict:
    """Read a user's saved tool prefs, or the defaults.

    Fails open (returns the defaults) on any store error, so a DynamoDB hiccup never blocks the
    assistant — the same policy usage_today uses on its read side.
    """
    if user_id is None:
        return _default_prefs()
    try:
        response = _get_client(settings).get_item(
            TableName=settings.prefs_table,
            Key={"PK": {"S": f"USER#{user_id}"}, "SK": {"S": _TOOLS_SK}},
        )
        item = response.get("Item")
        if not item:
            return _default_prefs()
        mcp_enabled = item.get("mcpEnabled", {}).get("BOOL", True)
        disabled = [v["S"] for v in item.get("disabledTools", {}).get("L", []) if v.get("S")]
        return {"mcp_enabled": bool(mcp_enabled), "disabled_tools": disabled}
    except Exception as exc:  # noqa: BLE001 — never let a prefs read take down the assistant
        logger.warning("could not read tool prefs for user %s: %s", user_id, exc)
        return _default_prefs()


def save_tool_prefs(
    settings: Settings, user_id: int, mcp_enabled: bool, disabled_tools: list[str]
) -> None:
    """Persist a user's tool toggle as a full replace of the stored item.

    Unlike the read, this does NOT swallow errors: a save the user explicitly asked for should
    surface (as a 5xx) if the store truly failed, rather than silently appear to succeed.
    """
    _get_client(settings).put_item(
        TableName=settings.prefs_table,
        Item={
            "PK": {"S": f"USER#{user_id}"},
            "SK": {"S": _TOOLS_SK},
            "mcpEnabled": {"BOOL": bool(mcp_enabled)},
            "disabledTools": {"L": [{"S": t} for t in disabled_tools]},
            "updatedAt": {"S": datetime.now(timezone.utc).isoformat()},
        },
    )


def resolve_tool_prefs(
    settings: Settings,
    user_id: int | None,
    mcp_enabled: bool | None,
    disabled_tools: list[str] | None,
) -> tuple[bool, list[str]]:
    """The effective tool prefs for one turn.

    Each field the request sent (not None) is used as-is; any field the request omitted falls back
    to the user's saved pref, or the default. When the request supplies both, no store read happens.
    """
    if mcp_enabled is not None and disabled_tools is not None:
        return mcp_enabled, list(disabled_tools)
    stored = get_tool_prefs(settings, user_id)
    return (
        mcp_enabled if mcp_enabled is not None else stored["mcp_enabled"],
        list(disabled_tools) if disabled_tools is not None else stored["disabled_tools"],
    )
