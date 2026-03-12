import argparse
import logging
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from worker.config import Config
from proxmox.client import ProxmoxClient
from worker.backend.client import BackendClient
from build.pipeline import BuildPipeline
import urllib3
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

def setup_logging(level: str, worker_id: str) -> logging.Logger:
    logging.basicConfig(
        level=getattr(logging, level.upper()),
        format=f"%(asctime)s - [{worker_id}] - %(levelname)s - %(message)s",
        handlers=[logging.StreamHandler(sys.stdout)],
    )
    return logging.getLogger("vmgen-worker")


def main() -> int:
    parser = argparse.ArgumentParser(description="VMGen Worker")
    parser.add_argument("--env", type=Path, default=Path(".env"))
    parser.add_argument("--validate", action="store_true")
    parser.add_argument("--worker-id", type=str, help="Override WORKER_ID")
    args = parser.parse_args()

    try:
        config = Config.load(args.env)
    except Exception as e:
        print(f"Error loading config: {e}", file=sys.stderr)
        return 1

    errors = config.validate()
    if errors:
        print("Configuration errors:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    if args.worker_id:
        config.worker.id = args.worker_id

    logger = setup_logging(config.log_level, config.worker.id)

    if args.validate:
        logger.info("Configuration validated successfully")
        return 0

    # Initialise clients
    proxmox = ProxmoxClient(config.proxmox)
    backend = BackendClient(config.backend.url, config.backend.api_key, config.worker.id)

    # Startup check — refuse to run if the isolated bridge has a gateway
    logger.info(f"Checking network isolation on bridge {config.proxmox.isolated_bridge}...")
    if not proxmox.verify_network_isolation(config.proxmox.isolated_bridge):
        logger.error(
            f"Bridge {config.proxmox.isolated_bridge} failed isolation check. "
            "Ensure it has no gateway before running the worker."
        )
        return 1
    logger.info("Network isolation check passed")

    logger.info(f"Worker ID: {config.worker.id}")
    logger.info(f"Poll interval: {config.worker.poll_interval_seconds}s")
    logger.info(f"Max concurrent builds: {config.worker.max_concurrent_builds}")

    try:
        with ThreadPoolExecutor(max_workers=config.worker.max_concurrent_builds) as executor:
            active = set()
            while True:
                # Drop completed futures from the tracking set
                active = {f for f in active if not f.done()}

                if len(active) < config.worker.max_concurrent_builds:
                    job = backend.claim_job()
                    if job:
                        logger.info(f"Claimed job {job['requestId']}")
                        pipeline = BuildPipeline(config, proxmox, backend)
                        active.add(executor.submit(pipeline.run, job))
                    else:
                        time.sleep(config.worker.poll_interval_seconds)
                else:
                    # At capacity — check again in 5s without hitting the backend
                    time.sleep(5)

    except KeyboardInterrupt:
        logger.info("Worker stopped by user")
        return 0
    except Exception as e:
        logger.error(f"Worker error: {e}", exc_info=True)
        return 1


if __name__ == "__main__":
    sys.exit(main())