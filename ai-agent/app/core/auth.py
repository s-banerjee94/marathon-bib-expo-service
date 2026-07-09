from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import httpx
import jwt

from .settings import Settings

# Token types Spring mints that the agent will act on: a user's own access token (browser-direct)
# or the short-lived MCP token Spring forwards server-to-server. Anything else (e.g. refresh) is refused.
_ACCEPTED_TOKEN_TYPES = {"access", "mcp"}


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


@lru_cache(maxsize=1)
def _public_key_pem(path: str) -> str:
    """Read the RSA public key PEM once. PyJWT accepts the PEM text directly for RS256."""
    return Path(path).read_text(encoding="utf-8")


def verify_token(token: str, settings: Settings) -> Session:
    """Verify an RS256 JWT with the public key and return the identity it carries.

    Trusts ONLY the signed claims, never caller-supplied fields. The algorithm is pinned to RS256
    so a forged 'alg' (e.g. 'none', or HS256 using the public key as a secret) is rejected. Raises
    a ``jwt.InvalidTokenError`` subclass on a bad signature, wrong issuer, expiry, or token type.
    """
    claims = jwt.decode(
        token,
        _public_key_pem(settings.jwt_public_key_path),
        algorithms=["RS256"],
        issuer=settings.jwt_issuer,
        options={"require": ["exp", "sub"]},
    )
    token_type = claims.get("type")
    if token_type not in _ACCEPTED_TOKEN_TYPES:
        raise jwt.InvalidTokenError(f"unexpected token type: {token_type!r}")
    return Session(
        token=token,
        user_id=claims.get("userId"),
        role=claims.get("role"),
        organization_id=claims.get("organizationId"),
    )
