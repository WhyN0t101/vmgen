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
