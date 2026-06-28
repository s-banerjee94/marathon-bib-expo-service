"""Approval policy: decide which tool calls must pause for a human's sign-off.

Three modes control how much the agent does on its own:
  * AUTO  -> never pause; run every tool immediately.
  * AGENT -> pause only for *critical* writes (destructive / mass / reaches real people).
  * ASK   -> pause before *every* write.

Reads (get_/list_/search_/count_/find_) never pause; every other tool is treated as a write,
so an unrecognised tool fails closed (gated, not auto-run). This is the "stop and ask a human"
gate; the Spring server still enforces the real access check on every call as the backstop.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import TYPE_CHECKING

if TYPE_CHECKING:  # imported only for type hints, never at runtime
    from collections.abc import Callable

    from langchain.agents.middleware.human_in_the_loop import InterruptOnConfig
    from langchain.agents.middleware.types import ToolCallRequest
    from langchain_core.tools import BaseTool


class ApprovalMode(str, Enum):
    """How freely the agent may act without asking a human first."""

    AUTO = "auto"
    AGENT = "agent"
    ASK = "ask"


@dataclass
class ModeState:
    """Holds the current mode in one mutable place.

    The agent is built once; changing this field flips behaviour on the very next
    tool call, so the REPL can switch modes live without rebuilding or logging in again.
    """

    mode: ApprovalMode = ApprovalMode.AGENT


# A tool whose name starts with one of these only reads data; it never needs approval.
# Anything that is NOT a read is treated as a write (fail closed).
_READ_PREFIXES = ("get_", "list_", "search_", "count_", "find_")

# A tool whose name starts with one of these changes data (a known "write"). Several match no
# tool today (delete_/update_/collect_/undo_/distribute_/import_/send_/bulk_); they are kept so
# future tools are classified automatically.
_WRITE_PREFIXES = (
    "create_",
    "update_",
    "delete_",
    "collect_",
    "undo_",
    "distribute_",
    "import_",
    "send_",
    "test_",
    "invite_",
    "reassign_",
)

# The subset of writes that are destructive, mass-scale, or reach real people.
# These pause even in AGENT mode.
_CRITICAL_PREFIXES = ("delete_", "send_", "import_", "test_", "bulk_", "invite_")

# Writes that do not match a write prefix above but still change data.
_WRITE_NAMES = frozenset({"reassign_distributor_event"})

# Critical writes that a prefix cannot single out: campaign "create_"s that blast messages
# to real participants, plus account/tenant creation, versus a harmless create_event.
_CRITICAL_NAMES = frozenset(
    {
        "create_sms_campaign",
        "create_whatsapp_campaign",
        "create_organization",
        "create_user",
    }
)


def _is_read(name: str) -> bool:
    return name.startswith(_READ_PREFIXES)


def _is_known_write(name: str) -> bool:
    return name.startswith(_WRITE_PREFIXES) or name in _WRITE_NAMES


def _is_critical(name: str) -> bool:
    return name.startswith(_CRITICAL_PREFIXES) or name in _CRITICAL_NAMES


def _should_pause(tool_name: str, mode_state: ModeState) -> Callable[[ToolCallRequest], bool]:
    """Build the per-tool predicate the middleware asks right before running a tool.

    It reads the *current* mode each time it is called, so flipping ModeState takes
    effect immediately.
    """
    # Fail closed: a tool we do not recognise as a known write is treated as critical, so
    # AGENT mode pauses for it too (a new write tool is gated until it's classified above).
    pause_in_agent = _is_critical(tool_name) or not _is_known_write(tool_name)

    def predicate(_request: ToolCallRequest) -> bool:
        mode = mode_state.mode
        if mode is ApprovalMode.AUTO:
            return False  # never pause
        if mode is ApprovalMode.ASK:
            return True  # pause for any write
        return pause_in_agent  # AGENT: critical or unrecognised writes

    return predicate


def build_interrupt_on(
    tools: list[BaseTool], mode_state: ModeState
) -> dict[str, InterruptOnConfig]:
    """Map each non-read tool to its pause rule, for HumanInTheLoopMiddleware.

    Fail closed: only tools whose names clearly mark them as reads are left out (they always
    run without interruption). Every other tool — known writes and any unrecognised tool — gets
    a pause rule, so a new write can never slip through un-gated.
    """
    interrupt_on: dict[str, InterruptOnConfig] = {}
    for tool in tools:
        if _is_read(tool.name):
            continue
        interrupt_on[tool.name] = {
            "allowed_decisions": ["approve", "edit", "reject"],
            "when": _should_pause(tool.name, mode_state),
        }
    return interrupt_on
