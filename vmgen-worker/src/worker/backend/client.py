import requests

class BackendClient:
    def __init__(self, url: str, api_key: str, worker_id: str):
        self.url = url.rstrip('/')
        self.worker_id = worker_id
        self._headers = {'X-Worker-Api-Key': api_key}

    def claim_job(self) -> dict | None: #Returns the Job or None
        resp = requests.post(
            f"{self.url}/api/worker/jobs/claim",
            json={"workerId": self.worker_id},
            headers=self._headers,
        )
        if resp.status_code == 204:
            return None   # Queue is empty, nothing to do
        resp.raise_for_status()
        return resp.json()
    
    def update_progress(self, job_id: str, stage: str, percent: int, message: str = ""):
        resp = requests.patch(
            f"{self.url}/api/worker/jobs/{job_id}/progress",
            json={
                "workerId": self.worker_id,
                "stage": stage,
                "percent": percent,
                "message": message,
            },
            headers=self._headers,
        )
        resp.raise_for_status()

    def complete_job(
        self,
        job_id: str,
        ova_path: str,
        ova_checksum: str,
        ova_size_bytes: int,
        provenance: dict,
        flags: list[dict],
    ):
        # flags = [{"scriptId": ..., "flagValueHash": ..., "placementLocation": ...}]
        # flagValueHash = SHA-256(raw_flag) — raw flag is NEVER sent here
        resp = requests.post(
            f"{self.url}/api/worker/jobs/{job_id}/complete",
            json={
                "workerId": self.worker_id,
                "ovaPath": ova_path,
                "ovaChecksum": ova_checksum,
                "ovaSizeBytes": ova_size_bytes,
                "provenance": provenance,
                "flags": flags,
            },
            headers=self._headers,
        )
        resp.raise_for_status()

    def fail_job(self, job_id: str, error_type: str, message: str, logs: str = ""):
        # error_type: "script_failed" | "timeout" | "proxmox_error"
        resp = requests.post(
            f"{self.url}/api/worker/jobs/{job_id}/fail",
            json={
                "workerId": self.worker_id,
                "errorType": error_type,
                "message": message,
                "logs": logs,
            },
            headers=self._headers,
        )
        resp.raise_for_status()