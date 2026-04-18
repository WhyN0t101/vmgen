-- Drop tables that were defined in V1 but are never written to by the application.
-- worker_heartbeats: WorkerService keeps heartbeats in-memory (ConcurrentHashMap) only.
-- submission_attempts: FlagService tracks attempts via vm_requests.failed_flag_attempts; this table is unused.
DROP TABLE IF EXISTS submission_attempts;
DROP TABLE IF EXISTS worker_heartbeats;

-- Drop duplicate partial index on vm_requests.last_progress_at.
-- idx_vm_requests_progress and idx_vm_requests_timeout were identical (same column, same WHERE clause).
DROP INDEX IF EXISTS idx_vm_requests_timeout;
