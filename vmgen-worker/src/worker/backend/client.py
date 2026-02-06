import requests

class BackendClient:
    def __init__(self, url: str, api_key: str):
        self.url = url.rstrip('/')
        self.headers = {'X-Worker-Api-Key': api_key}
    
    def update_progress(self, job_id: str, stage: str, percent: int):
        response = requests.patch(
            f"{self.url}/api/worker/jobs/{job_id}/progress",
            json={"stage": stage, "percent": percent},
            headers=self.headers
        )
        response.raise_for_status()
