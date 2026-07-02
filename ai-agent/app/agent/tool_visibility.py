"""Decide which MCP tools each role is allowed to SEE.

The Spring MCP server hands every client the full tool list (it cannot filter per
user), so we trim that list here based on the signed-in user's role — taken from the
trusted login response, not guessed. The server still enforces real access on every
call, so this is the "don't even show it" layer, not the security boundary.

Policy:
  * ROOT only        -> system / platform-default message-provider tools
  * ROOT and ADMIN   -> organization management and platform-wide org statistics
  * everyone else    -> every other tool (each already self-scopes on the server)

Only the exceptions are listed in _MIN_ROLE below; any tool not listed is open to all.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:  # imported only by type-checkers/IDEs, never at runtime
    from langchain_core.tools import BaseTool

ROOT = "ROOT"
ADMIN = "ADMIN"
ORGANIZER_ADMIN = "ORGANIZER_ADMIN"
ORGANIZER_USER = "ORGANIZER_USER"

# Platform privilege ranking; a higher number means more privilege.
_ROLE_RANK = {
    ORGANIZER_USER: 0,
    ORGANIZER_ADMIN: 1,
    ADMIN: 2,
    ROOT: 3,
}

# Minimum role needed to SEE a tool. A tool not listed here is visible to everyone.
_MIN_ROLE: dict[str, str] = {
    # ROOT only — message-provider setup (secrets / platform-wide defaults).
    # NOTE: these tools also cover an organization's OWN providers, so keeping them
    # ROOT also hides that org-level use from ADMIN / ORGANIZER_ADMIN. Change the
    # value to ADMIN here if/when org-level provider management should be allowed.
    "list_campaign_providers": ROOT,
    "get_campaign_provider": ROOT,
    "test_campaign_provider": ROOT,
    # ROOT and ADMIN — organization management and platform-wide org statistics.
    "search_organizations": ADMIN,
    "create_organization": ADMIN,
    "get_organization_statistics": ADMIN,
}


def visible_tools(role: str, all_tools: list[BaseTool]) -> list[BaseTool]:
    """Return only the tools the given role is allowed to see.

    Fails closed: a role we do not recognise (e.g. DISTRIBUTOR, which has no AI
    access) gets an empty list rather than accidentally seeing tools.
    """
    if role not in _ROLE_RANK:
        return []
    user_rank = _ROLE_RANK[role]

    allowed: list[BaseTool] = []
    for tool in all_tools:
        required_role = _MIN_ROLE.get(tool.name)  # None means "open to everyone"
        if required_role is None or user_rank >= _ROLE_RANK[required_role]:
            allowed.append(tool)
    return allowed
