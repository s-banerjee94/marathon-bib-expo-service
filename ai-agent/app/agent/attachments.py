"""Chat attachments: store an uploaded image/PDF in S3 and feed it to the model for one call.

The conversation is checkpointed to DynamoDB, whose 400 KB item cap means a 1-2 MB file can never
live inside a saved message. So the bytes go to S3 keyed by the uploader, the saved message keeps
only a tiny reference (`additional_kwargs["attachments"]`), and `AttachmentMiddleware` loads the
bytes back and attaches them to just the one model call (transient context) — never persisting
them. That also bills the file once (the turn it is sent), not on every later turn.

Trust boundary: the S3 key is always rebuilt from the VERIFIED user id plus an opaque id, never
from anything the client can shape, so one user can never reference another user's object.
"""

from __future__ import annotations

import asyncio
import base64
import logging
import uuid
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from typing import Any

import boto3
from langchain.agents.middleware import AgentMiddleware, ModelRequest, ModelResponse
from langchain_core.messages import HumanMessage

from ..core.settings import Settings
from .card import text_content

logger = logging.getLogger(__name__)

# --- Accepted types and size caps (all validated server-side). ---
PDF_MIME = "application/pdf"
ALLOWED_IMAGE_MIMES = {"image/png", "image/jpeg", "image/webp", "image/gif"}
ALLOWED_MIMES = ALLOWED_IMAGE_MIMES | {PDF_MIME}

MAX_IMAGE_BYTES = 2 * 1024 * 1024   # 2 MB per image
MAX_PDF_BYTES = 1 * 1024 * 1024     # 1 MB per PDF
MAX_FILES = 2                       # at most two files per message (no combined-size cap)

# Presigned GET URLs handed to the frontend to show a stored attachment in the chat transcript.
# Short-lived and consumed at render time; the file itself may already be gone (expired/deleted).
_PRESIGN_TTL_SECONDS = 60 * 60      # 1 hour

# Magic-byte signatures so a mislabeled upload (a .exe renamed .pdf) is rejected, not trusted
# on its claimed MIME alone. WEBP is a RIFF container with "WEBP" at offset 8.
_PDF_MAGIC = b"%PDF-"
_IMAGE_MAGIC: dict[str, list[bytes]] = {
    "image/png": [b"\x89PNG\r\n\x1a\n"],
    "image/jpeg": [b"\xff\xd8\xff"],
    "image/gif": [b"GIF87a", b"GIF89a"],
}


class AttachmentError(Exception):
    """Base for attachment validation failures (surfaced to the caller as an HTTP error)."""


class AttachmentTypeError(AttachmentError):
    """Unsupported, mislabeled, or missing file -> HTTP 400."""


class AttachmentTooLargeError(AttachmentError):
    """A file, the file count, or the combined payload exceeds its cap -> HTTP 413."""


@dataclass(frozen=True)
class AttachmentRef:
    """A tiny pointer to one uploaded file in S3 — small enough to save in a checkpoint.

    Carries no bytes: the middleware loads those from S3 only for the model call. `id` is the
    opaque handle the client echoes back in `attachmentIds`; `key` is the full S3 object key.
    """

    key: str        # full S3 object key: user-<id>/<uuid>
    id: str         # the opaque <uuid> the client sends back in attachmentIds
    kind: str       # "image" or "pdf"
    mime: str       # exact MIME (image/png, application/pdf, ...)
    filename: str   # original file name (OpenAI needs it for PDFs)
    size: int       # byte length

    def to_dict(self) -> dict[str, Any]:
        """As a plain dict for storing in a message's additional_kwargs (JSON-serializable)."""
        return {"key": self.key, "id": self.id, "kind": self.kind,
                "mime": self.mime, "filename": self.filename, "size": self.size}


# Building a boto3 client is comparatively expensive and the client is stateless, so cache one.
_s3: Any = None


def _s3_client(settings: Settings) -> Any:
    """Return the shared S3 client, mirroring the checkpointer's creds/endpoint resolution.

    endpoint_url points at LocalStack locally and is None (real AWS) in production, where the
    profile is also None (EC2 instance role) — exactly like usage.py builds its DynamoDB client.
    """
    global _s3
    if _s3 is None:
        session = boto3.Session(region_name=settings.aws_region, profile_name=settings.aws_profile)
        _s3 = session.client("s3", endpoint_url=settings.s3_endpoint_url)
    return _s3


def _ensure_bucket(settings: Settings, client: Any) -> None:
    """For local dev only, create the attachments bucket if it is missing.

    LocalStack does not persist across restarts, so the bucket can vanish on a machine reboot;
    recreating it on demand means a daily LocalStack reset needs no manual step (the app's media
    bucket comes back the same way, via its init hook). In real AWS (no custom endpoint) the bucket
    is provisioned by infrastructure and the app never tries to create it — the instance role has no
    CreateBucket permission — so this is a no-op there. Best-effort: an already-exists race is fine.
    """
    if settings.s3_endpoint_url is None:  # real AWS: infra owns the bucket
        return
    try:
        client.head_bucket(Bucket=settings.s3_bucket)
        return
    except Exception:  # noqa: BLE001 — missing (or any head failure): try to create it
        pass
    try:
        client.create_bucket(Bucket=settings.s3_bucket)
        logger.info("created local attachments bucket %s", settings.s3_bucket)
    except Exception as exc:  # noqa: BLE001 — concurrent create / already exists is harmless
        logger.info("attachments bucket %s not created (%s); assuming it exists", settings.s3_bucket, exc)


def _detect_kind(mime: str) -> str:
    return "pdf" if mime == PDF_MIME else "image"


def _magic_ok(mime: str, data: bytes) -> bool:
    """True when the file's leading bytes match what its MIME claims to be."""
    if mime == PDF_MIME:
        return data[:5] == _PDF_MAGIC
    if mime == "image/webp":
        return data[:4] == b"RIFF" and data[8:12] == b"WEBP"
    return any(data.startswith(sig) for sig in _IMAGE_MAGIC.get(mime, []))


def _safe_name(name: str) -> str:
    """A printable-ASCII file name safe for an S3 metadata header (which must be ASCII)."""
    cleaned = "".join(c for c in (name or "") if 32 <= ord(c) < 127).strip()
    return cleaned or "attachment"


def _mb(n: int) -> int:
    return n // (1024 * 1024)


def store_uploads(
    settings: Settings, user_id: int, uploads: list[tuple[str, str, bytes]]
) -> list[AttachmentRef]:
    """Validate the uploaded files and put each in S3, returning one small ref per file.

    `uploads` are (filename, mime, data) tuples already read into memory by the caller (reading
    an UploadFile is async; the S3 puts here are blocking, so run this off the event loop).
    Validation runs fully before any upload: the file count, then per file its MIME + magic bytes
    + per-file size cap (image 2 MB / PDF 1 MB; there is no combined-size cap). Raises
    AttachmentTypeError (bad type) or AttachmentTooLargeError (too big) — nothing is stored on failure.
    """
    if not uploads:
        raise AttachmentTypeError("Attach at least one file.")
    if len(uploads) > MAX_FILES:
        raise AttachmentTooLargeError(f"You can attach at most {MAX_FILES} files to a message.")

    prepared: list[tuple[str, str, str, bytes]] = []  # (mime, kind, filename, data)
    for filename, mime, data in uploads:
        mime = (mime or "").split(";")[0].strip().lower()
        if mime not in ALLOWED_MIMES:
            raise AttachmentTypeError("You can only attach PNG, JPEG, WEBP or GIF images, or PDF files.")
        if not _magic_ok(mime, data):
            raise AttachmentTypeError("A file's contents do not match its type.")
        cap = MAX_PDF_BYTES if mime == PDF_MIME else MAX_IMAGE_BYTES
        if len(data) > cap:
            unit = "PDF" if mime == PDF_MIME else "image"
            raise AttachmentTooLargeError(f"Each {unit} must be {_mb(cap)} MB or smaller.")
        prepared.append((mime, _detect_kind(mime), _safe_name(filename), data))

    client = _s3_client(settings)
    _ensure_bucket(settings, client)  # local dev: (re)create the bucket so a daily reset needs no manual step
    refs: list[AttachmentRef] = []
    for mime, kind, filename, data in prepared:
        att_id = uuid.uuid4().hex
        key = f"user-{user_id}/{att_id}"
        client.put_object(
            Bucket=settings.s3_bucket,
            Key=key,
            Body=data,
            ContentType=mime,
            Metadata={"kind": kind, "filename": filename, "mime": mime},
        )
        refs.append(AttachmentRef(key=key, id=att_id, kind=kind, mime=mime, filename=filename, size=len(data)))
    logger.info("stored %d attachment(s) for user %s", len(refs), user_id)
    return refs


def resolve_attachment_ids(settings: Settings, user_id: int, ids: list[str]) -> list[AttachmentRef]:
    """Rebuild refs for the ids a client sent with a message, confirming each object exists.

    The S3 key is rebuilt from the VERIFIED user id + the opaque id (never any client-shaped
    path), so cross-user access is impossible by construction. head_object confirms each object
    exists and supplies its stored size/metadata without downloading the bytes; the file-count cap
    is re-checked (there is no combined-size cap). Raises AttachmentTypeError (unknown/expired id)
    or AttachmentTooLargeError (too many files).
    """
    if not ids:
        return []
    if len(ids) > MAX_FILES:
        raise AttachmentTooLargeError(f"You can attach at most {MAX_FILES} files to a message.")

    client = _s3_client(settings)
    refs: list[AttachmentRef] = []
    for raw_id in ids:
        att_id = (raw_id or "").strip()
        if not att_id or "/" in att_id:  # an id is an opaque token, never a path
            raise AttachmentTypeError("An attachment reference is not valid.")
        key = f"user-{user_id}/{att_id}"
        try:
            head = client.head_object(Bucket=settings.s3_bucket, Key=key)
        except Exception as exc:  # noqa: BLE001 — any miss (gone/expired/denied) is "not available"
            logger.warning("attachment %s not found for user %s: %s", att_id, user_id, exc)
            raise AttachmentTypeError("An attachment was not found or has expired.") from exc
        meta = head.get("Metadata") or {}
        size = int(head.get("ContentLength") or 0)
        mime = meta.get("mime") or head.get("ContentType") or ""
        refs.append(AttachmentRef(
            key=key,
            id=att_id,
            kind=meta.get("kind") or _detect_kind(mime),
            mime=mime,
            filename=meta.get("filename") or "attachment",
            size=size,
        ))
    return refs


def presign_get(settings: Settings, key: str, expires: int = _PRESIGN_TTL_SECONDS) -> str | None:
    """A short-lived presigned GET URL for one stored attachment, for the frontend to display it.

    Signing is local (no network round trip) and does NOT verify the object still exists — an
    expired or already-deleted file yields a URL that 404s, which the frontend shows as a
    placeholder. Returns None only when signing itself fails (then no thumbnail is shown).
    """
    if not key:
        return None
    try:
        return _s3_client(settings).generate_presigned_url(
            "get_object", Params={"Bucket": settings.s3_bucket, "Key": key}, ExpiresIn=expires
        )
    except Exception as exc:  # noqa: BLE001 — a presign failure just means no thumbnail, not an error
        logger.warning("could not presign attachment %s: %s", key, exc)
        return None


def delete_user_attachments(settings: Settings, user_id: int) -> None:
    """Delete every stored attachment for a user (their whole `user-<id>/` prefix). Best-effort.

    Called when a user wipes their conversation memory, so their uploaded files go with it instead of
    lingering until the bucket's lifecycle expiry. Pages through the listing and batch-deletes. Never
    raises: a cleanup failure must not fail the memory-delete it rides with.
    """
    client = _s3_client(settings)
    prefix = f"user-{user_id}/"
    deleted = 0
    try:
        token: str | None = None
        while True:
            kwargs: dict[str, Any] = {"Bucket": settings.s3_bucket, "Prefix": prefix}
            if token:
                kwargs["ContinuationToken"] = token
            page = client.list_objects_v2(**kwargs)
            keys = [{"Key": obj["Key"]} for obj in page.get("Contents", [])]
            if keys:
                client.delete_objects(Bucket=settings.s3_bucket, Delete={"Objects": keys, "Quiet": True})
                deleted += len(keys)
            token = page.get("NextContinuationToken")
            if not token:
                break
        if deleted:
            logger.info("deleted %d attachment(s) for user %s", deleted, user_id)
    except Exception as exc:  # noqa: BLE001 — attachment cleanup must never break a memory delete
        logger.warning("could not delete attachments for user %s: %s", user_id, exc)


def _last_human_index(messages: list) -> int | None:
    """Index of the newest HumanMessage in a message list, or None if there is none."""
    for i in range(len(messages) - 1, -1, -1):
        if isinstance(messages[i], HumanMessage):
            return i
    return None


class AttachmentMiddleware(AgentMiddleware):
    """Attach an uploaded image/PDF to a single model call, without persisting the bytes.

    Only the newest human message can carry attachments (they arrive with a new message). When it
    does, the bytes are loaded from S3, turned into multimodal content blocks (text + image/file),
    and swapped in for that one message via `request.override(messages=...)`. Saved state keeps the
    original text-only message, so the checkpoint stays tiny and the file is billed once. A missing
    object (e.g. a file that reached the bucket's 30-day expiry, or was cleared with the chat memory)
    degrades to a short `[attachment expired]` note rather than failing the turn. No-op when nothing carries attachments,
    so it is safe to include in the chat-only (no-tools) build too.
    """

    def __init__(self, settings: Settings) -> None:
        super().__init__()
        self._settings = settings

    async def awrap_model_call(
        self, request: ModelRequest, handler: Callable[[ModelRequest], Awaitable[ModelResponse]]
    ) -> ModelResponse:
        messages = request.messages
        idx = _last_human_index(messages)
        if idx is None:
            return await handler(request)
        original = messages[idx]
        refs = (original.additional_kwargs or {}).get("attachments")
        if not refs:
            return await handler(request)

        blocks = await asyncio.to_thread(self._build_blocks, original, refs)
        swapped = list(messages)
        # Keep the original id and additional_kwargs so this stays the "same" message; only its
        # content (transient, for this call) gains the file blocks. State is never overwritten.
        swapped[idx] = HumanMessage(
            content=blocks, additional_kwargs=original.additional_kwargs, id=original.id
        )
        return await handler(request.override(messages=swapped))

    def _build_blocks(self, message: HumanMessage, refs: list[dict]) -> list[dict]:
        """Build the multimodal content: the message text, then one block per attachment (from S3)."""
        text = text_content(message.content)
        blocks: list[dict] = [{"type": "text", "text": text}] if text.strip() else []
        client = _s3_client(self._settings)
        for ref in refs:
            key = ref.get("key")
            mime = ref.get("mime") or ""
            filename = ref.get("filename") or "attachment"
            try:
                data = client.get_object(Bucket=self._settings.s3_bucket, Key=key)["Body"].read()
            except Exception as exc:  # noqa: BLE001 — a gone/expired object must not break the turn
                logger.warning("attachment %s unavailable for the model call: %s", key, exc)
                blocks.append({"type": "text", "text": f"[attachment {filename} expired]"})
                continue
            b64 = base64.b64encode(data).decode("ascii")
            if ref.get("kind") == "pdf":
                blocks.append({"type": "file", "base64": b64, "mime_type": mime or PDF_MIME, "filename": filename})
            else:
                blocks.append({"type": "image", "base64": b64, "mime_type": mime})
        return blocks
