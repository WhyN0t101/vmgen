import argparse      # CLI argument parsing
import logging       # Structured logging
import sys           # Exit codes
import time          # Sleep between polls
from pathlib import Path

from worker.config import Config

def setup_logging(level: str, worker_id: str) -> logging.Logger:
    logging.basicConfig(
        level=getattr(logging, level.upper()),
        format=f"%(asctime)s - [{worker_id}] - %(levelname)s - %(message)s",
        handlers=[logging.StreamHandler(sys.stdout)],
    )
    return logging.getLogger("vmgen-worker")

def main() -> int:
    parser = argparse.ArgumentParser(description="VMGen Worker")
    parser.add_argument("--config", type=Path, default=Path("config.yaml"))
    parser.add_argument("--env", type=Path, default=Path(".env"))
    parser.add_argument("--validate", action="store_true")
    parser.add_argument("--worker-id", type=str, help="Override WORKER_ID")
    args = parser.parse_args()

    # Load configuration
    try:
        config = Config.load(args.env)
    except Exception as e:
        print(f"Error loading config: {e}", file=sys.stderr)
        return 1

    # Validate configuration
    errors = config.validate()
    if errors:
        print("Configuration errors:", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    # Override worker ID if specified
    if args.worker_id:
        config.worker.id = args.worker_id

    # Setup logging
    logger = setup_logging(config.log_level, config.worker.id)

    # Handle validation-only mode
    if args.validate:
        logger.info("Configuration validated successfully")
        return 0

    # Main loop
    logger.info("Worker started")
    logger.info(f"Worker ID: {config.worker.id}")
    logger.info(f"Poll interval: {config.worker.poll_interval_seconds}s")
    logger.info(f"Max concurrent builds: {config.worker.max_concurrent_builds}")

    try:
        while True:
            logger.info("Polling for jobs...")
            # TODO: Implement job polling and processing
            time.sleep(config.worker.poll_interval_seconds)
    except KeyboardInterrupt:
        logger.info("Worker stopped by user")
        return 0
    except Exception as e:
        logger.error(f"Worker error: {e}", exc_info=True)
        return 1    

if __name__ == "__main__":
    sys.exit(main())
