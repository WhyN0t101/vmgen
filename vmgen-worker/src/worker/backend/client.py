import logging
import time
from datetime import datetime, timezone

import requests

log = logging.getLogger(__name__)


_DEFAULT_TIMEOUT = 30  # seconds — all backend HTTP calls must fail fast


class BackendClient:
    def __init__(self, url: str, api_key: str, worker_id: str):
        self.url = url.rstrip('/')
        self.worker_id = worker_id
        self._headers = {'X-Worker-Api-Key': api_key}

    def claim_job(self) -> dict | None:
        log.debug("Polling for job...")
        resp = requests.post(
            f"{self.url}/api/worker/jobs/claim",
            json={"workerId": self.worker_id},
            headers=self._headers,
            timeout=_DEFAULT_TIMEOUT,
        )
        if resp.status_code == 204:
            log.debug("No jobs available")
            return None
        resp.raise_for_status()
        job = resp.json()
        log.info(f"Claimed job {job['requestId']} (template: {job.get('templateIdentifier')}, scripts: {len(job.get('scripts', []))})")
        return job

    def update_progress(self, job_id: str, stage: str, percent: int, message: str = ""):
        log.debug(f"[{job_id}] Progress update: {stage} ({percent}%)")
        resp = requests.patch(
            f"{self.url}/api/worker/jobs/{job_id}/progress",
            json={
                "workerId": self.worker_id,
                "stage": stage,
                "percent": percent,
                "message": message,
            },
            headers=self._headers,
            timeout=_DEFAULT_TIMEOUT,
        )
        resp.raise_for_status()

    def complete_job(
        self,
        job_id: str,
        ova_path: str,
        ova_checksum: str,
        ova_size_bytes: int,
        provenance: dict,
        vm_login_password: str,
        flags: list[dict],
    ):
        # flags = [{"scriptId": ..., "flagValueHash": ..., "placementLocation": ...}]
        # flagValueHash = SHA-256(raw_flag) — raw flag is NEVER sent here
        log.info(f"[{job_id}] Registering completion (ova_size: {ova_size_bytes // (1024 * 1024)}MB, flags: {len(flags)})")
        payload = {
            "workerId": self.worker_id,
            "ovaPath": ova_path,
            "ovaChecksum": ova_checksum,
            "ovaSizeBytes": ova_size_bytes,
            "provenance": provenance,
            "vmLoginPassword": vm_login_password,
            "flags": flags,
        }
        for attempt in range(3):
            try:
                resp = requests.post(
                    f"{self.url}/api/worker/jobs/{job_id}/complete",
                    json=payload,
                    headers=self._headers,
                    timeout=_DEFAULT_TIMEOUT,
                )
                resp.raise_for_status()
                log.info(f"[{job_id}] Backend acknowledged completion")
                return
            except Exception as e:
                if attempt == 2:
                    raise
                log.warning(f"[{job_id}] complete_job attempt {attempt + 1} failed, retrying in 5s: {e}")
                time.sleep(5)

    def append_log(
        self,
        job_id: str,
        level: str,
        message: str,
        stage: str | None = None,
    ) -> None:
        """
        Fire-and-forget build log entry. A failure here must never abort the build —
        the caller should not propagate exceptions from this method.
        """
        try:
            resp = requests.post(
                f"{self.url}/api/worker/jobs/{job_id}/logs",
                json={
                    "workerId": self.worker_id,
                    "logLevel": level,
                    "stage": stage,
                    "message": message,
                    "loggedAt": datetime.now(timezone.utc).isoformat(),
                },
                headers=self._headers,
                timeout=_DEFAULT_TIMEOUT,
            )
            resp.raise_for_status()
        except Exception as e:
            log.warning("[%s] Failed to write build log to backend (non-fatal): %s", job_id, e)

    def heartbeat(self, active_job_ids: list[str], cpu_percent: float, memory_percent: float) -> None:
        """
        Report worker liveness to the backend every 60 seconds.
        Non-fatal — a failure here must never crash the poll loop.
        """
        try:
            resp = requests.post(
                f"{self.url}/api/worker/heartbeat",
                json={
                    "workerId": self.worker_id,
                    "activeJobIds": active_job_ids,
                    "cpuPercent": cpu_percent,
                    "memoryPercent": memory_percent,
                },
                headers=self._headers,
                timeout=_DEFAULT_TIMEOUT,
            )
            resp.raise_for_status()
        except Exception as e:
            log.warning("Heartbeat failed (non-fatal): %s", e)

    def fail_job(self, job_id: str, error_type: str, message: str, logs: str = ""):
        # error_type: "script_failed" | "timeout" | "proxmox_error"
        log.info(f"[{job_id}] Reporting failure: type={error_type}")
        resp = requests.post(
            f"{self.url}/api/worker/jobs/{job_id}/fail",
            json={
                "workerId": self.worker_id,
                "errorType": error_type,
                "message": message,
                "logs": logs,
            },
            headers=self._headers,
            timeout=_DEFAULT_TIMEOUT,
        )
        resp.raise_for_status()