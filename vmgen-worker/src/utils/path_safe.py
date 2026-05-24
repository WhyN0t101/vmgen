"""Filesystem path helpers — keep worker file access inside allowed directories."""

from __future__ import annotations

import logging
import re
import shutil
from pathlib import Path

log = logging.getLogger(__name__)

_GUEST_PATH_PATTERN = re.compile(r"^[a-zA-Z0-9/_.+-]+$")


def resolve_under_base(base: str | Path, *segments: str) -> Path:
    """
    Join segments under base and ensure the resolved path cannot escape base.
    Raises ValueError on traversal or escape attempts.
    """
    base_path = Path(base).expanduser().resolve()
    target = base_path.joinpath(*segments).resolve()
    _assert_under_base(base_path, target)
    return target


def _assert_under_base(base: Path, target: Path) -> None:
    try:
        target.relative_to(base)
    except ValueError as exc:
        raise ValueError(f"Path {target} escapes allowed directory {base}") from exc


def validate_guest_path(path: str, *, field_name: str = "path") -> str:
    """
    Validate an absolute guest VM path used in remote commands.
    Rejects traversal, whitespace, and shell metacharacters.
    """
    if not path or not path.startswith("/"):
        raise ValueError(f"{field_name} must be an absolute path starting with /")
    if ".." in path:
        raise ValueError(f"{field_name} must not contain path traversal sequences (..)")
    if any(ch.isspace() for ch in path):
        raise ValueError(f"{field_name} must not contain whitespace")
    if not _GUEST_PATH_PATTERN.match(path):
        raise ValueError(f"{field_name} contains invalid characters")
    return path


def cleanup_export_dir(base: str | Path, job_id: str) -> None:
    """
    Remove a build's export directory and any partial OVA artefacts under base.
    Safe no-op if the directory does not exist. Never raises.
    """
    try:
        export_dir = resolve_under_base(base, job_id)
    except ValueError as exc:
        log.warning("Skipping export cleanup for job %s: %s", job_id, exc)
        return

    if not export_dir.exists():
        return

    try:
        if export_dir.is_dir():
            shutil.rmtree(export_dir)
        else:
            export_dir.unlink()
        log.info("Removed export artefacts for job %s at %s", job_id, export_dir)
    except OSError as exc:
        log.error("Failed to remove export artefacts for job %s at %s: %s", job_id, export_dir, exc)
