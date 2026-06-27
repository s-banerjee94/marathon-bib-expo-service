from dataclasses import dataclass

import httpx

from settings import Settings


@dataclass(frozen=True)
class Session:
    """Who the signed-in user is, as told to us by the server at login.

    The role comes straight from the Spring login response (not decoded from the
    token on our side), so it is trustworthy. It is what we use to decide which
    tools the agent is even allowed to see.
    """

    token: str                    # JWT sent on every MCP call
    user_id: int | None           # signed-in user's id (keys their conversation memory)
    role: str                     # ROOT, ADMIN, ORGANIZER_ADMIN or ORGANIZER_USER
    organization_id: int | None   # the user's org, when they belong to one


def login(settings: Settings) -> Session:
    """Authenticate against the Spring API and return the session (token + role).

    The token is later sent to the MCP server so every tool call runs with this
    user's role-based access, exactly as the REST API enforces it.
    """
    resp = httpx.post(
        f"{settings.api_base_url}/api/auth/login",
        json={"username": settings.username, "password": settings.password},
        timeout=15.0,
    )
    resp.raise_for_status()
    data = resp.json()
    token = data.get("accessToken")
    if not token:
        raise RuntimeError("Login succeeded but no accessToken was returned.")
    return Session(
        token=token,
        user_id=data.get("userId"),
        role=data.get("role"),
        organization_id=data.get("organizationId"),
    )
