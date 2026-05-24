import os
import sys
from dataclasses import dataclass
from pathlib import Path
from dotenv import load_dotenv # type: ignore


@dataclass
class ProxmoxConfig:
    host: str
    token_id: str
    token_secret: str
    verify_ssl: bool
    node: str
    isolated_bridge: str
    ssh_user: str
    ssh_key_path: str
    known_hosts_path: str    # path to SSH known_hosts file for Proxmox host verification

@dataclass
class BackendConfig:
    url: str
    api_key: str

@dataclass
class WorkerConfig:
    id: str
    poll_interval_seconds: int
    max_concurrent_builds: int

@dataclass
class Config:
    proxmox: ProxmoxConfig
    worker: WorkerConfig
    backend: BackendConfig
    export_path: str           # filesystem path where OVAs are written (must be NFS-accessible from worker)
    log_level: str = "INFO"

    @classmethod
    def load(cls, env_path: Path = Path(".env")) -> "Config":
        # Load .env file
        load_dotenv(env_path)
        
        # Read values from environment
        proxmox = ProxmoxConfig(
            host=os.getenv("PROXMOX_HOST", ""),
            token_id=os.getenv("PROXMOX_TOKEN_ID", ""),
            token_secret=os.getenv("PROXMOX_TOKEN_SECRET", ""),
            verify_ssl=os.getenv("PROXMOX_VERIFY_SSL", "true").lower() == "true",
            node=os.getenv("PROXMOX_NODE", "pve"),
            isolated_bridge=os.getenv("PROXMOX_ISOLATED_BRIDGE", "vmbr1"),
            ssh_user=os.getenv("PROXMOX_SSH_USER", "root"),
            ssh_key_path=os.getenv("PROXMOX_SSH_KEY_PATH", ""),
            known_hosts_path=os.getenv("PROXMOX_KNOWN_HOSTS_PATH",
                                       str(Path.home() / ".ssh" / "known_hosts")),
        )

        worker = WorkerConfig(
            id=os.getenv("WORKER_ID", "worker-01"),
            poll_interval_seconds=int(os.getenv("WORKER_POLL_INTERVAL_SECONDS", "30")),
            max_concurrent_builds=int(os.getenv("WORKER_MAX_CONCURRENT_BUILDS", "3")),
        )
        backend = BackendConfig(
            url=os.getenv("BACKEND_URL", "http://localhost:3000"),
            api_key=os.getenv("BACKEND_API_KEY", ""),
        )
        return cls(
            proxmox=proxmox,
            worker=worker,
            backend=backend,
            export_path=os.getenv("EXPORT_PATH", "/var/lib/vmgen/exports"),
            log_level=os.getenv("LOG_LEVEL", "INFO"),
        )
        
    def validate(self) -> list[str]:
        """Return list of validation errors (empty if valid)."""
        errors = []

        # Proxmox
        if not self.proxmox.host:
            errors.append("PROXMOX_HOST is required")
        if not self.proxmox.token_id:
            errors.append("PROXMOX_TOKEN_ID is required")
        if not self.proxmox.token_secret:
            errors.append("PROXMOX_TOKEN_SECRET is required")
        if not self.proxmox.ssh_key_path:
            errors.append("PROXMOX_SSH_KEY_PATH is required")
        else:
            ssh_key = Path(self.proxmox.ssh_key_path)
            if not ssh_key.exists():
                errors.append(f"SSH key file not found: {self.proxmox.ssh_key_path}")
            elif sys.platform != "win32" and ssh_key.stat().st_mode & 0o077:
                errors.append(f"SSH key has insecure permissions (expected 0600): {self.proxmox.ssh_key_path}")
        if not Path(self.proxmox.known_hosts_path).exists():
            errors.append(
                f"PROXMOX_KNOWN_HOSTS_PATH not found: {self.proxmox.known_hosts_path}. "
                "Run: ssh-keyscan -H <proxmox-host> >> ~/.ssh/known_hosts"
            )

        # Worker
        if not self.worker.id or not self.worker.id.strip():
            errors.append("WORKER_ID is required and must not be blank")

        # Backend
        if not self.backend.url:
            errors.append("BACKEND_URL is required")
        if not self.backend.api_key:
            errors.append("BACKEND_API_KEY is required")
        
        # Build output
        if not self.export_path:
            errors.append("EXPORT_PATH is required")
        else:
            export = Path(self.export_path).expanduser()
            if not export.is_absolute():
                errors.append("EXPORT_PATH must be an absolute path")

        return errors
