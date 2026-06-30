"""Record per-user daily token usage to DynamoDB — the counter Spring pre-checks for its budget.

The agent owns the *accounting*: after each model call it atomically adds that call's token
count to today's bucket. Spring owns *enforcement*: it only reads this counter to decide whether
to allow the next turn. Both sides key the same row, so they must agree on its shape:

    PK = USER#<id>   SK = DAY#<yyyy-MM-dd>   (the day is computed in UTC)
    tokens (Number, atomic ADD)   requests (Number)   ttl (Number, epoch seconds)
"""

from __future__ import annotations

import asyncio
import logging
import time
from datetime import datetime, timedelta, timezone
from typing import TYPE_CHECKING, Any

import boto3
from langchain.agents.middleware import AgentMiddleware

from settings import Settings

if TYPE_CHECKING:  # imported only for type hints
    from langgraph.runtime import Runtime

logger = logging.getLogger(__name__)

# The daily window resets at UTC midnight, matching how the rest of the system stores time. The
# only hard requirement is that Spring derives the same DAY# bucket, so both sides use UTC.

# Daily buckets self-expire a few days after their first write (the window is only a coarse day).
_USAGE_TTL_SECONDS = 3 * 24 * 60 * 60

# Shown to a user who has spent their daily budget; mirrors Spring's message.
_OVER_LIMIT_MESSAGE = "You've reached your AI assistant limit for today, please try again tomorrow."

# Building a boto3 client is comparatively expensive and the client is stateless, so cache one.
_client: Any = None


class UsageLimitError(Exception):
    """Raised when a user has reached their daily AI token budget; surfaced to the caller as HTTP 429."""


def _get_client(settings: Settings) -> Any:
    """Return the shared DynamoDB client, mirroring the checkpointer's creds/endpoint resolution."""
    global _client
    if _client is None:
        session = boto3.Session(region_name=settings.aws_region, profile_name=settings.aws_profile)
        _client = session.client("dynamodb", endpoint_url=settings.ddb_endpoint_url)
    return _client


def record_usage(settings: Settings, user_id: int, tokens: int) -> None:
    """Atomically add one model call's token usage (and a request count) to today's bucket."""
    day = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    expires = int(time.time()) + _USAGE_TTL_SECONDS
    _get_client(settings).update_item(
        TableName=settings.usage_table,
        Key={"PK": {"S": f"USER#{user_id}"}, "SK": {"S": f"DAY#{day}"}},
        UpdateExpression="ADD #tok :tok, #req :one SET #ttl = if_not_exists(#ttl, :ttl)",
        ExpressionAttributeNames={"#tok": "tokens", "#req": "requests", "#ttl": "ttl"},
        ExpressionAttributeValues={
            ":tok": {"N": str(tokens)},
            ":one": {"N": "1"},
            ":ttl": {"N": str(expires)},
        },
    )


def usage_today(settings: Settings, user_id: int) -> int:
    """Read today's token total for a user (UTC day). Fails open (returns 0) on any store error,
    so a DynamoDB hiccup never blocks the assistant — matching Spring's read side."""
    day = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    try:
        response = _get_client(settings).get_item(
            TableName=settings.usage_table,
            Key={"PK": {"S": f"USER#{user_id}"}, "SK": {"S": f"DAY#{day}"}},
        )
        tokens = response.get("Item", {}).get("tokens", {}).get("N")
        return int(tokens) if tokens else 0
    except Exception as exc:  # noqa: BLE001 — never let a usage read take down the assistant
        logger.warning("could not read AI usage for user %s: %s", user_id, exc)
        return 0


def check_allowed(settings: Settings, user_id: int | None, role: str | None) -> None:
    """Block a user who has spent their daily budget by raising UsageLimitError.

    Mirrors Spring's checkAllowed: a missing limit (role not configured) or a negative value means
    "not enforced here", so unknown roles pass through. Both sides read the same daily counter.
    """
    if user_id is None:
        return
    limit = settings.ai_limits.get(role) if role else None
    if limit is None:
        logger.warning("no AI token limit configured for role %s; allowing without enforcement", role)
        return
    if limit < 0:
        return
    used = usage_today(settings, user_id)
    if used >= limit:
        logger.info("AI usage blocked: user=%s used=%s limit=%s", user_id, used, limit)
        raise UsageLimitError(_OVER_LIMIT_MESSAGE)


def usage_snapshot(settings: Settings, user_id: int | None, role: str | None) -> dict:
    """The caller's daily budget meter: tokens used, the role cap, what remains, and the reset time.

    Read-only — never consumes budget or raises. An uncapped role reports limit/remaining as -1,
    mirroring Spring's AgentUsageResponse so the frontend can switch sources without code changes.
    """
    limit = settings.ai_limits.get(role) if role else None
    capped = limit is not None and limit >= 0
    used = usage_today(settings, user_id) if user_id is not None else 0
    now = datetime.now(timezone.utc)
    resets_at = (now + timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
    return {
        "used": used,
        "limit": limit if capped else -1,
        "remaining": max(0, limit - used) if capped else -1,
        "resets_at": resets_at,
    }


class UsageTrackingMiddleware(AgentMiddleware):
    """After every model call, add its token usage to the user's daily counter.

    Built per request with the trusted user id. Runs once per model invocation, so a turn with
    several tool round-trips sums all of its model calls. Writes happen off the event loop and
    never raise into the turn: a usage-store failure must not break the user's chat.
    """

    def __init__(self, settings: Settings, user_id: int | None) -> None:
        super().__init__()
        self._settings = settings
        self._user_id = user_id

    async def aafter_model(self, state: Any, runtime: Runtime) -> dict[str, Any] | None:
        if self._user_id is None:
            return None
        messages = state.get("messages") if isinstance(state, dict) else None
        usage = getattr(messages[-1], "usage_metadata", None) if messages else None
        total = usage.get("total_tokens") if usage else None
        if not total:
            return None
        try:
            loop = asyncio.get_running_loop()
            await loop.run_in_executor(None, record_usage, self._settings, self._user_id, int(total))
        except Exception as exc:  # noqa: BLE001 — usage accounting must never break a turn
            logger.warning("could not record AI usage for user %s: %s", self._user_id, exc)
        return None
