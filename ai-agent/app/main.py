"""HTTP entry point for the agent: the browser sends a user's message here directly.

The browser calls these endpoints cross-origin with the user's access token in an
'Authorization: Bearer' header. We verify that token (RS256, public key only), read the
trusted identity (role / id) from its signed claims, and build a per-request agent for that
user, so each caller gets their own role-filtered tools and persistent memory.

Because writes pause through the checkpointer (not a blocking console prompt), an approval
is returned to the caller and resumed later via /chat/resume on the same conversation.

Run it with:  uv run uvicorn app.main:app      (or: uv run python -m app.main)
"""

import logging
from contextlib import asynccontextmanager

import openai
import uvicorn
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .agent.usage import UsageLimitError
from .core.logging_config import configure_logging
from .core.settings import settings
from .routers import chat

# One logging setup for the whole service, applied at import so it covers every entry point
# (`python -m app.main`, `uvicorn app.main:app`, and the bibexpo-agent console script). Coloured
# console in dev, plus a rolling file when BIBEXPO_LOG_FILE is set in prod.
configure_logging(level=settings.log_level, log_file=settings.log_file, color=settings.log_color)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(_: FastAPI):
    """Log a clear line on startup and shutdown (modern replacement for on_event hooks)."""
    logger.info(
        "AI agent starting (host=%s port=%s model=%s)",
        settings.api_host,
        settings.api_port,
        settings.openai_model,
    )
    yield
    logger.info("AI agent shutting down")


app = FastAPI(
    title="Marathon Bib Expo AI Agent",
    description=(
        "AI agent chat API. The browser calls it directly with a Bearer access token, which is "
        "verified here (RS256, public key only) before any tools run."
    ),
    version="1.0.0",
    lifespan=lifespan,
    openapi_tags=[
        {"name": "chat", "description": "Send a message and resume after an approval pause."},
        {"name": "meta", "description": "Health and diagnostics."},
    ],
)

# Browser-direct calls are cross-origin (local dev, and the Amplify site vs the api host in prod). Auth
# rides in the Authorization header, NOT a cookie, so we do not use credentialed CORS — which lets dev
# use a wildcard origin (set BIBEXPO_CORS_ALLOWED_ORIGINS='*' to test from a phone on the same Wi-Fi).
# In prod set it to the exact Amplify origin. The frontend must stream with fetch() (EventSource cannot
# send headers), which triggers the CORS preflight this handles.
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_allowed_origins,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(openai.RateLimitError)
async def _rate_limited(request: Request, exc: openai.RateLimitError) -> JSONResponse:
    """Turn OpenAI's 429 into an intentional 'busy' reply instead of a generic 500.

    The OpenAI SDK already retries a 429 a few times with backoff; this fires only once those are
    exhausted. Two cases share the 429: transient throughput throttling (TPM/RPM), which clears on
    its own within seconds, and an exhausted account quota, which needs a credit top-up and will not
    fix itself. We answer 429 for both but word them differently and log the quota case louder.
    """
    if getattr(exc, "code", None) == "insufficient_quota":
        logger.error("OpenAI quota exhausted on %s %s: %s", request.method, request.url.path, exc)
        detail = "The AI assistant is temporarily unavailable. Please try again later."
    else:
        logger.warning("OpenAI rate limit on %s %s: %s", request.method, request.url.path, exc)
        detail = "The assistant is busy right now, please try again in a few seconds."
    return JSONResponse(status_code=429, content={"detail": detail})


@app.exception_handler(UsageLimitError)
async def _usage_limited(request: Request, exc: UsageLimitError) -> JSONResponse:
    """Turn a spent daily budget into a 429 with the user-facing message (checked before streaming)."""
    return JSONResponse(status_code=429, content={"detail": str(exc)})


@app.exception_handler(Exception)
async def _unhandled_exception(request: Request, exc: Exception) -> JSONResponse:
    """Last-resort handler: log the full traceback and return a clean 500 (never a stack trace)."""
    logger.exception("unhandled error on %s %s", request.method, request.url.path)
    return JSONResponse(status_code=500, content={"detail": "The AI assistant hit an unexpected error."})


@app.get("/health", tags=["meta"], summary="Liveness check")
async def health() -> dict[str, str]:
    return {"status": "ok"}


app.include_router(chat.router)


def run() -> None:
    """Console-script entry point (bibexpo-agent): start the ASGI server."""
    # log_config=None -> uvicorn keeps the logging we set up in configure_logging() instead of
    # reinstalling its own, so request logs share our format and rolling file.
    uvicorn.run(app, host=settings.api_host, port=settings.api_port, log_config=None)


if __name__ == "__main__":
    run()
