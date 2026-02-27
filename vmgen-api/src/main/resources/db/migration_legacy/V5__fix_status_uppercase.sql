-- Fix template status constraint to use uppercase (matching Java enum)

-- Drop constraint FIRST (before updating data)
ALTER TABLE templates DROP CONSTRAINT IF EXISTS templates_status_check;

-- Update existing data to uppercase
UPDATE templates SET status = UPPER(status);

-- Recreate constraint with uppercase values
ALTER TABLE templates ADD CONSTRAINT templates_status_check 
    CHECK (status IN ('DRAFT', 'VALIDATING', 'ACTIVE', 'FAILED', 'DEPRECATED'));

-- Update the partial index for active templates
DROP INDEX IF EXISTS idx_templates_active_filter;
CREATE INDEX idx_templates_active_filter ON templates(status) WHERE status = 'ACTIVE';

