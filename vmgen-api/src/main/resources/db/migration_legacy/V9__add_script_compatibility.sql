-- Script conflicts (mutual exclusion) and dependencies
-- as well as metadata on vm_request_scripts for dependency tracking.

-- Scripts that cannot be selected together (mutual exclusion).
CREATE TABLE script_conflicts (
    script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    conflicts_with_script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES users(id),

    PRIMARY KEY (script_id, conflicts_with_script_id),

    -- Prevent self-conflict
    CONSTRAINT chk_script_conflicts_no_self_conflict CHECK (script_id <> conflicts_with_script_id)
);

-- Scripts that must be included when another script is selected.
CREATE TABLE script_dependencies (
    script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    requires_script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES users(id),

    PRIMARY KEY (script_id, requires_script_id),

    -- Prevent self-dependency
    CONSTRAINT chk_script_dependencies_no_self_dependency CHECK (script_id <> requires_script_id)
);

-- Indexes for performance when resolving compatibility.
CREATE INDEX idx_script_conflicts_script ON script_conflicts(script_id);
CREATE INDEX idx_script_conflicts_with ON script_conflicts(conflicts_with_script_id);
CREATE INDEX idx_script_dependencies_script ON script_dependencies(script_id);
CREATE INDEX idx_script_dependencies_requires ON script_dependencies(requires_script_id);

-- Track whether a script was auto-added via dependency resolution
-- on a given VM request.
ALTER TABLE vm_request_scripts
    ADD COLUMN auto_added BOOLEAN NOT NULL DEFAULT FALSE;

