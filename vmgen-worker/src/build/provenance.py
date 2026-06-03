import hashlib
from pathlib import Path


def compute_sha256(file_path: str, *, allowed_base: str | Path | None = None) -> str:
    """SHA-256 of a file, read in chunks to handle large OVAs."""
    path = Path(file_path).expanduser().resolve()
    if allowed_base is not None:
        base = Path(allowed_base).expanduser().resolve()
        try:
            path.relative_to(base)
        except ValueError as exc:
            raise ValueError(f"Path {path} escapes allowed directory {base}") from exc

    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def assemble_provenance(
    job: dict,
    worker_id: str,
    build_started_at: str,
    build_finished_at: str,
    scripts_executed: list[dict],
    flag_derivation_timestamp: str,
    template_sha256: str,
) -> dict:
    
    """
    Build the provenance payload to send with complete_job().
    job = the dict returned by backend.claim_job()
    scripts_executed = [{scriptId, scriptIdentifier, exitCode, durationSeconds}, ...]
    """
    # Build lookup from scriptId → full script metadata from the job spec
    script_meta = {str(s["scriptId"]): s for s in job.get("scripts", [])}

    scripts = []
    for se in scripts_executed:
        sid = str(se["scriptId"])
        meta = script_meta.get(sid, {})
        scripts.append({
            "scriptId": sid,
            "identifier": meta.get("identifier", se.get("scriptIdentifier", "")),
            "version": meta.get("version", ""),
            "sha256": meta.get("sha256", ""),
            "executionOrder": meta.get("executionOrder", 0),
            "exitCode": se["exitCode"],
            "durationSeconds": se["durationSeconds"],
        })

    return {
        "workerId": worker_id,
        "templateIdentifier": job["templateIdentifier"],
        "templateVersion": job["templateVersion"],
        "templateSha256": template_sha256,
        "buildStartedAt": build_started_at,
        "buildFinishedAt": build_finished_at,
        "flagDerivationTimestamp": flag_derivation_timestamp,
        "scripts": scripts,
    }
