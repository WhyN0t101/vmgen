-- V6: Store telemetry content inline as JSONB instead of on the filesystem.
-- Makes file_path optional (was NOT NULL) and adds raw_json for the actual payload.

ALTER TABLE telemetry_uploads ALTER COLUMN file_path DROP NOT NULL;
ALTER TABLE telemetry_uploads ADD COLUMN raw_json JSONB;
