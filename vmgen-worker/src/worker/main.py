import argparse
import concurrent.futures
import logging
import signal
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from worker.config import Config
from proxmox.client import ProxmoxClient
from worker.backend.client import BackendClient
from build.pipeline import BuildPipeline
import urllib3
# Proxmox VE uses a self-signed certificate in the reference deployment — suppress noise
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

    shutdown_requested = False

    def _handle_shutdown(sig, *_):
        nonlocal shutdown_requested
        logger.info(f"Shutdown signal received ({signal.Signals(sig).name}), finishing active builds...")
        shutdown_requested = True

    signal.signal(signal.SIGTERM, _handle_shutdown)
    signal.signal(signal.SIGINT, _handle_shutdown)

    executor = ThreadPoolExecutor(max_workers=config.worker.max_concurrent_builds)
    active: dict = {}  # future → job

    try:
        while not shutdown_requested:
            # Drop completed futures from the tracking dict
            for f in [f for f in active if f.done()]:
                del active[f]

            if len(active) < config.worker.max_concurrent_builds:
                job = backend.claim_job()
                if job:
                    logger.info(f"Claimed job {job['requestId']}")
                    pipeline = BuildPipeline(config, proxmox, backend)
                    active[executor.submit(pipeline.run, job)] = job
                else:
                    time.sleep(config.worker.poll_interval_seconds)
            else:
                # At capacity — check again in 5s without hitting the backend
                time.sleep(5)

    except Exception as e:
        logger.error(f"Worker error: {e}", exc_info=True)

    finally:
        executor.shutdown(wait=False, cancel_futures=False)

        GRACE_SECONDS = 8  # stay within Docker's 10s SIGKILL window
        if active:
            logger.info(f"Shutdown: waiting up to {GRACE_SECONDS}s for {len(active)} active build(s)...")
            _, still_running = concurrent.futures.wait(list(active.keys()), timeout=GRACE_SECONDS)
            for future in still_running:
                job = active[future]
                job_id = str(job["requestId"])
                logger.warning(f"Build {job_id} exceeded grace period — reporting as failed")
                try:
                    backend.fail_job(job_id, "worker_shutdown",
                                     "Worker shutdown: build did not finish within grace period")
                except Exception as report_err:
                    logger.error(f"Could not report {job_id} as failed: {report_err}")

        logger.info("Worker shut down gracefully")

    return 0


if __name__ == "__main__":
    sys.exit(main())