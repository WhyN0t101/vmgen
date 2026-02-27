-- V3__fix_templates.sql

-- Add status field
ALTER TABLE templates
    ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'draft'
        CHECK (status IN ('draft', 'validating', 'active', 'failed', 'deprecated'));

-- Drop abstract source fields
ALTER TABLE templates
DROP COLUMN source_type,
    DROP COLUMN source_reference,
    DROP COLUMN storage_path;

-- Add concrete Proxmox fields
ALTER TABLE templates
    ADD COLUMN proxmox_vmid INTEGER,
    ADD COLUMN proxmox_node VARCHAR(100),
    ADD COLUMN disk_storage VARCHAR(100);

-- Add validation metadata
ALTER TABLE templates
    ADD COLUMN validation_logs JSONB,
    ADD COLUMN last_validated_at TIMESTAMP,
    ADD COLUMN expected_ssh_user VARCHAR(50) DEFAULT 'root',
    ADD COLUMN expected_ssh_port INTEGER DEFAULT 22,
    ADD COLUMN network_bridge VARCHAR(100),
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT NOW();

-- Fix hash nullability (computed during validation, not at creation)
ALTER TABLE templates
    ALTER COLUMN sha256_hash DROP NOT NULL;

-- Drop is_active since we have status
ALTER TABLE templates
DROP COLUMN is_active;

-- Update indexes
DROP INDEX IF EXISTS idx_templates_active;
CREATE INDEX idx_templates_status ON templates(status);
CREATE INDEX idx_templates_active_filter ON templates(status) WHERE status = 'active';
