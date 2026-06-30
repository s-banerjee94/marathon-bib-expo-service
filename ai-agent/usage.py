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
from datetime import datetime, timezone
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

# Building a boto3 client is comparatively expensive and the client is stateless, so cache one.
_client: Any = None


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
