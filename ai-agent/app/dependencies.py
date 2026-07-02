"""Request authentication: turn the Bearer access token into a verified Session.

The browser calls the chat API cross-origin with the user's access token in an
'Authorization: Bearer' header. These helpers verify that token (RS256, public key only) and
hand back the trusted identity, so a caller can only ever act as — and touch the memory of —
the user their token names.
"""

import logging

import jwt
from fastapi import HTTPException, Request

from .core.auth import Session, verify_token
from .core.settings import settings

logger = logging.getLogger(__name__)


def _bearer(request: Request) -> str | None:
    """The token from an 'Authorization: Bearer <jwt>' header, or None when absent."""
    header = request.headers.get("authorization", "")
    if header[:7].lower() == "bearer ":
        return header[7:].strip() or None
    return None


def _verified(token: str) -> Session:
    """Verify a JWT or fail the request with 401 (the crypto reason is logged, never returned)."""
    try:
        return verify_token(token, settings)
    except jwt.InvalidTokenError as exc:
        logger.info("token rejected: %s", exc)
        raise HTTPException(status_code=401, detail="Your session is invalid or has expired. Please log in again.")


def authenticate(request: Request) -> Session:
    """Identity for a chat call, from the VERIFIED Bearer access token in the Authorization header."""
    bearer = _bearer(request)
    if not bearer:
        raise HTTPException(status_code=401, detail="Missing credentials.")
    return _verified(bearer)


def authenticated_user_id(request: Request) -> int:
    """Trusted user id for a thread read/delete, from the verified Bearer token, so a user can
    only ever touch their own conversation."""
    return authenticate(request).user_id
