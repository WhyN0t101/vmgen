import os
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv

@dataclass
class DatabaseConfig:
    host: str
    port: int
    name: str
    user: str
    password: str

@dataclass
class ProxmoxConfig:
    host: str
    token_id: str
    token_secret: str
    verify_ssl: bool
    node: str

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
    database: DatabaseConfig
    proxmox: ProxmoxConfig
    worker: WorkerConfig
    backend: BackendConfig
    platform_secret: str
    log_level: str = "INFO"

    @classmethod
    def load(cls, env_path: Path = Path(".env")) -> "Config":
        # Load .env file
        load_dotenv(env_path)
        
        # Read values from environment
        database = DatabaseConfig(
            host=os.getenv("DATABASE_HOST", "localhost"),
            port=int(os.getenv("DATABASE_PORT", "5432")),
            name=os.getenv("DATABASE_NAME", "vmgen"),
            user=os.getenv("DATABASE_USER", "vmgen"),
            password=os.getenv("DATABASE_PASSWORD", ""),
        )
        proxmox = ProxmoxConfig(
            host=os.getenv("PROXMOX_HOST", ""),
            token_id=os.getenv("PROXMOX_TOKEN_ID", ""),
            token_secret=os.getenv("PROXMOX_TOKEN_SECRET", ""),
            verify_ssl=os.getenv("PROXMOX_VERIFY_SSL", "true").lower() == "true",
            node=os.getenv("PROXMOX_NODE", "pve"),
        )

        worker = WorkerConfig(
            id=os.getenv("WORKER_ID", "worker-01"),
            poll_interval_seconds=30,
            max_concurrent_builds=3,
        )
        backend = BackendConfig(
            url=os.getenv("BACKEND_URL", "http://localhost:3000"),
            api_key=os.getenv("BACKEND_API_KEY", ""),
        )
        return cls(
            database=database,
            proxmox=proxmox,
            worker=worker,
            platform_secret=os.getenv("PLATFORM_SECRET", ""),
            log_level=os.getenv("LOG_LEVEL", "INFO"),
            backend=backend,
        )
        
    def validate(self) -> list[str]:
        """Return list of validation errors (empty if valid)."""
        errors = []
        
        # Database
        if not self.database.password:
            errors.append("DATABASE_PASSWORD is required")
        
        # Proxmox
        if not self.proxmox.host:
            errors.append("PROXMOX_HOST is required")
        if not self.proxmox.token_id:
            errors.append("PROXMOX_TOKEN_ID is required")
        if not self.proxmox.token_secret:
            errors.append("PROXMOX_TOKEN_SECRET is required")
        
        # Backend
        if not self.backend.url:
            errors.append("BACKEND_URL is required")
        if not self.backend.api_key:
            errors.append("BACKEND_API_KEY is required")
        
        # Security
        if not self.platform_secret:
            errors.append("PLATFORM_SECRET is required")
        elif len(self.platform_secret) < 32:
            errors.append("PLATFORM_SECRET should be at least 32 characters")
        
        return errors
