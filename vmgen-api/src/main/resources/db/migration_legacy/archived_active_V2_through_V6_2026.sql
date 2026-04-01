-- =============================================================================
-- ARCHIVED: These incremental migrations were merged into:
--   db/migration/V1__initial_schema.sql
-- Do NOT run this file with Flyway (it is not on the migration path).
-- Kept for history only — contents were folded into the consolidated V1 baseline.
-- =============================================================================

-- ----- Former V2__account_lockout.sql -----
-- Account lockout support (V2)
-- Tracks per-user lockout state and per-build flag attempt counters.

-- Lockout timestamp: set when a lockout is triggered (flag-based).
-- Login brute-force is mitigated at the rate-limiter level (IP-scoped), not here.
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP;

-- Flag-based lockout: per-build failed flag submission counter.
-- When a build reaches the per-build attempt limit (5), it is "exhausted".
-- When 3 builds are exhausted, the account is locked for 1 hour.
ALTER TABLE vm_requests ADD COLUMN failed_flag_attempts INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_users_locked_until ON users(locked_until) WHERE locked_until IS NOT NULL;

-- ----- Former V3__template_ssh_credentials.sql -----
-- Template SSH credentials
-- Stores the actual credential used by the worker to SSH into VMs built from this template.
-- ssh_password  : used when ssh_auth_method = 'password_reset'
-- ssh_private_key: used when ssh_auth_method = 'static_key' (full PEM content, not a file path)
-- cloudinit templates need neither — the worker generates an ephemeral key pair per build.

ALTER TABLE templates
    ADD COLUMN ssh_password     VARCHAR(255),
    ADD COLUMN ssh_private_key  TEXT;

-- ----- Former V4__provenance_enrich.sql -----
-- V4: Enrich provenance_records with runtime metadata and rebuild provenance_scripts
--     with a surrogate PK plus execution result columns.

-- Add worker and timing columns to provenance_records
ALTER TABLE provenance_records
    ADD COLUMN worker_id        VARCHAR(100),
    ADD COLUMN build_started_at TIMESTAMP,
    ADD COLUMN build_finished_at TIMESTAMP;

-- Rebuild provenance_scripts: drop composite PK, add surrogate UUID PK,
-- keep (provenance_id, execution_order) as a unique constraint,
-- and add script_id, exit_code, duration_seconds.
ALTER TABLE provenance_scripts DROP CONSTRAINT provenance_scripts_pkey;

ALTER TABLE provenance_scripts ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE provenance_scripts ADD PRIMARY KEY (id);
ALTER TABLE provenance_scripts ADD UNIQUE (provenance_id, execution_order);

ALTER TABLE provenance_scripts
    ADD COLUMN script_id        UUID,
    ADD COLUMN exit_code        INTEGER,
    ADD COLUMN duration_seconds INTEGER;

-- ----- Former V5__provenance_vm_login.sql -----
-- V5: Store the per-build VM login password (admin-only, never exposed to students)
ALTER TABLE provenance_records
    ADD COLUMN vm_login_password VARCHAR(64);

-- ----- Former V6__telemetry_jsonb.sql -----
-- V6: Store telemetry content inline as JSONB instead of on the filesystem.
-- Makes file_path optional (was NOT NULL) and adds raw_json for the actual payload.

ALTER TABLE telemetry_uploads ALTER COLUMN file_path DROP NOT NULL;
ALTER TABLE telemetry_uploads ADD COLUMN raw_json JSONB;
