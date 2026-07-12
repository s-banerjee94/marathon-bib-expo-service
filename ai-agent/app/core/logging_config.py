"""Central logging setup for the agent service.

Mirrors the Spring app's logging so both halves of the system read the same way: a coloured
console line in dev and a plain rolling file in prod, timestamps on a 12-hour clock. Call
configure_logging() once at startup, before the app serves traffic, so our own logs and
uvicorn's request logs share one format instead of uvicorn's separate default.
"""

import logging
import sys
import time
from logging.handlers import RotatingFileHandler
from pathlib import Path

# Console keeps a coloured, unpadded level (padding is applied by the colour formatter so the ANSI
# codes do not throw the alignment off); the file keeps a plain, fixed-width level.
_CONSOLE_FORMAT = "%(asctime)s %(levelname)s [%(name)s] - %(message)s"
_FILE_FORMAT = "%(asctime)s %(levelname)-5s [%(name)s] - %(message)s"

# 10 MB per file, keep 14 rolled files — the same rolling policy as the Spring side (10MB / 14).
_MAX_BYTES = 10 * 1024 * 1024
_BACKUP_COUNT = 14

# ANSI colours per level, emitted only to a real terminal (never into a file or a redirected pipe).
_LEVEL_COLOR = {
    "DEBUG": "\033[36m",     # cyan
    "INFO": "\033[32m",      # green
    "WARNING": "\033[33m",   # yellow
    "ERROR": "\033[31m",     # red
    "CRITICAL": "\033[1;31m",  # bold red
}
_RESET = "\033[0m"


class _TwelveHourFormatter(logging.Formatter):
    """Renders the timestamp as '2026-07-12 03:45:12.123 PM' (12-hour clock, matching Spring)."""

    def formatTime(self, record, datefmt=None):
        ct = self.converter(record.created)
        stamp = time.strftime("%Y-%m-%d %I:%M:%S", ct)
        return f"{stamp}.{int(record.msecs):03d} {time.strftime('%p', ct)}"


class _ColorFormatter(_TwelveHourFormatter):
    """Console formatter: pads the level to 5 chars and tints it, restoring the shared record after."""

    def __init__(self, fmt: str, color: bool):
        super().__init__(fmt)
        self._color = color

    def format(self, record: logging.LogRecord) -> str:
        original = record.levelname
        padded = f"{original:<5}"
        tint = self._color and _LEVEL_COLOR.get(original)
        record.levelname = f"{tint}{padded}{_RESET}" if tint else padded
        try:
            return super().format(record)
        finally:
            record.levelname = original  # leave the record clean for any later (file) handler


def configure_logging(level: str = "INFO", log_file: str | None = None, color: str = "auto") -> None:
    """Install our console (and, in prod, a rolling file) handlers on the root logger.

    level    -- root threshold: INFO in normal use, DEBUG to chase a bug.
    log_file -- when set (prod), also write a rolling file here; None keeps console-only (dev).
    color    -- 'auto' tints only when the console is a real terminal; 'always'/'never' force it.
    """
    use_color = sys.stderr.isatty() if color == "auto" else color == "always"

    console = logging.StreamHandler(sys.stderr)
    console.setFormatter(_ColorFormatter(_CONSOLE_FORMAT, use_color))
    handlers: list[logging.Handler] = [console]

    if log_file:
        path = Path(log_file)
        path.parent.mkdir(parents=True, exist_ok=True)
        rolling = RotatingFileHandler(
            path, maxBytes=_MAX_BYTES, backupCount=_BACKUP_COUNT, encoding="utf-8"
        )
        rolling.setFormatter(_TwelveHourFormatter(_FILE_FORMAT))
        handlers.append(rolling)

    root = logging.getLogger()
    root.setLevel(level.upper())
    root.handlers.clear()  # drop any basicConfig/uvicorn defaults so ours are the only handlers
    for handler in handlers:
        root.addHandler(handler)

    # Let uvicorn's own loggers flow through our handlers instead of printing a second, differently
    # formatted line. Effective only when uvicorn does not reinstall its default config (we pass
    # log_config=None in run()); harmless otherwise.
    for name in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        uv_logger = logging.getLogger(name)
        uv_logger.handlers.clear()
        uv_logger.propagate = True
