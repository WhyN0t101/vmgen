-- V4__worker_improvements.sql
-- Adds worker tracking columns for atomic job claiming and timeout detection

-- 1. Add worker claim tracking
ALTER TABLE vm_requests 
    ADD COLUMN claimed_by_worker_id VARCHAR(255),
    ADD COLUMN current_worker_stage VARCHAR(100),
    ADD COLUMN worker_last_heartbeat_at TIMESTAMP;

-- 2. Indexes for worker operations
CREATE INDEX idx_vm_requests_worker ON vm_requests(claimed_by_worker_id);
CREATE INDEX idx_vm_requests_timeout ON vm_requests(last_progress_at) 
    WHERE state IN ('building', 'exporting');

-- 3. Add constraint: building/exporting requires worker claim
ALTER TABLE vm_requests 
    ADD CONSTRAINT chk_worker_claim 
    CHECK (
        (state IN ('building', 'exporting') AND claimed_by_worker_id IS NOT NULL)
        OR (state NOT IN ('building', 'exporting'))
    );

-- Note: Flags table already has flag_value_hash for constant-time comparison
-- Validation flow: sha256(submitted) vs stored hash with constantTimeCompare()
