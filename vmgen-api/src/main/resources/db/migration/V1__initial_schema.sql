-- VMGen consolidated baseline schema (V1–V10)
-- This file represents the end state after applying the historical migrations that
-- previously lived in this folder (V1..V10). Those files are now archived under:
--   db/migration_legacy/

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================
-- Users / Sessions
-- =========================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    email_verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_activity_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sessions_token ON sessions(token_hash);
CREATE INDEX idx_sessions_user ON sessions(user_id);
CREATE INDEX idx_sessions_expiry ON sessions(expires_at);

-- =========================
-- Audit Log
-- =========================
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id UUID,
    metadata JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    occurred_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user ON audit_log(user_id, occurred_at);
CREATE INDEX idx_audit_action ON audit_log(action, occurred_at);

-- =========================
-- Templates
-- =========================
CREATE TABLE templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identifier VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    os_family VARCHAR(100) NOT NULL,
    os_version VARCHAR(100) NOT NULL,
    architecture VARCHAR(50) NOT NULL,
    description TEXT,

    -- Integrity (computed during validation; nullable at creation)
    sha256_hash VARCHAR(64),

    disk_size_gb INTEGER NOT NULL,
    min_ram_mb INTEGER NOT NULL DEFAULT 2048,
    min_cpu_cores INTEGER NOT NULL DEFAULT 2,

    validated_at TIMESTAMP,
    validated_by UUID REFERENCES users(id),
    validation_notes TEXT,

    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    deprecated_at TIMESTAMP,
    deprecated_by UUID REFERENCES users(id),

    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT templates_status_check CHECK (status IN ('DRAFT', 'VALIDATING', 'ACTIVE', 'FAILED', 'DEPRECATED')),

    -- Proxmox linkage
    proxmox_vmid INTEGER NOT NULL,
    proxmox_node VARCHAR(100) NOT NULL,
    disk_storage VARCHAR(100),
    proxmox_template_name VARCHAR(255),

    -- Validation metadata
    validation_logs JSONB,
    last_validated_at TIMESTAMP,
    expected_ssh_user VARCHAR(50) NOT NULL DEFAULT 'root',
    expected_ssh_port INTEGER NOT NULL DEFAULT 22,
    network_bridge VARCHAR(100),

    ssh_auth_method VARCHAR(20) NOT NULL DEFAULT 'password_reset',
    CONSTRAINT chk_templates_ssh_auth_method CHECK (ssh_auth_method IN ('cloudinit', 'password_reset', 'static_key')),

    last_validation_attempt TIMESTAMP,
    validation_duration_seconds INTEGER,

    UNIQUE(identifier, version)
);

CREATE TABLE template_capabilities (
    template_id UUID NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    capability VARCHAR(100) NOT NULL,
    PRIMARY KEY (template_id, capability)
);

CREATE INDEX idx_templates_family ON templates(os_family);
CREATE INDEX idx_templates_status ON templates(status);
CREATE INDEX idx_templates_active_filter ON templates(status) WHERE status = 'ACTIVE';

-- =========================
-- Vulnerability Categories (V8)
-- =========================
CREATE TABLE vulnerability_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identifier VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,

    color VARCHAR(7),
    icon VARCHAR(50),

    owasp_mapping VARCHAR(50),
    severity_range VARCHAR(20),

    is_active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER DEFAULT 0,

    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deprecated_at TIMESTAMP,

    CONSTRAINT chk_vuln_category_identifier_format CHECK (identifier ~ '^[a-z0-9-]+$'),
    CONSTRAINT chk_vuln_category_color_format CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE INDEX idx_vuln_categories_active ON vulnerability_categories(is_active) WHERE is_active = true;
CREATE INDEX idx_vuln_categories_order ON vulnerability_categories(display_order, name);

-- =========================
-- Scripts (with category FK, V8)
-- =========================
CREATE TABLE scripts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identifier VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,

    category_id UUID REFERENCES vulnerability_categories(id),

    difficulty_level VARCHAR(50) NOT NULL,
    script_content TEXT NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,

    flag_location TEXT NOT NULL,
    flag_ownership VARCHAR(100),
    flag_permissions VARCHAR(10),

    is_active BOOLEAN NOT NULL DEFAULT true,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deprecated_at TIMESTAMP,

    UNIQUE(identifier, version)
);

CREATE INDEX idx_scripts_active ON scripts(is_active) WHERE is_active = true;
CREATE INDEX idx_scripts_category_fk ON scripts(category_id);

-- Legacy script metadata tables (present historically, not currently used by the app)
CREATE TABLE script_requirements (
    script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    requirement_type VARCHAR(50) NOT NULL,
    requirement_value TEXT NOT NULL,
    PRIMARY KEY (script_id, requirement_type, requirement_value)
);

CREATE TABLE script_capabilities (
    script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    capability VARCHAR(100) NOT NULL,
    PRIMARY KEY (script_id, capability)
);

CREATE TABLE script_compatibility (
    script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    template_id UUID NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    verified_at TIMESTAMP,
    verified_by UUID REFERENCES users(id),
    PRIMARY KEY (script_id, template_id)
);

-- Script conflicts/dependencies (V9)
CREATE TABLE script_conflicts (
    script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    conflicts_with_script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES users(id),
    PRIMARY KEY (script_id, conflicts_with_script_id),
    CONSTRAINT chk_script_conflicts_no_self_conflict CHECK (script_id <> conflicts_with_script_id)
);

CREATE TABLE script_dependencies (
    script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    requires_script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID REFERENCES users(id),
    PRIMARY KEY (script_id, requires_script_id),
    CONSTRAINT chk_script_dependencies_no_self_dependency CHECK (script_id <> requires_script_id)
);

CREATE INDEX idx_script_conflicts_script ON script_conflicts(script_id);
CREATE INDEX idx_script_conflicts_with ON script_conflicts(conflicts_with_script_id);
CREATE INDEX idx_script_dependencies_script ON script_dependencies(script_id);
CREATE INDEX idx_script_dependencies_requires ON script_dependencies(requires_script_id);

-- Script parameters (V10)
CREATE TABLE script_parameters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    script_id UUID NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,

    parameter_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    display_order INTEGER DEFAULT 0,

    parameter_type VARCHAR(20) NOT NULL,
    value_source VARCHAR(30) NOT NULL DEFAULT 'USER_INPUT',

    default_value TEXT,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    validation_rules JSONB,
    random_config JSONB,
    user_can_override BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    UNIQUE (script_id, parameter_name),

    CONSTRAINT chk_script_parameters_value_source CHECK (
        value_source IN ('USER_INPUT', 'AUTO_RANDOM', 'AUTO_CHOICE', 'USER_INPUT_OR_RANDOM')
    ),
    CONSTRAINT chk_script_parameters_type CHECK (
        parameter_type IN ('STRING', 'INTEGER', 'CHOICE', 'BOOLEAN', 'PATH')
    ),
    CONSTRAINT chk_script_parameters_name_format CHECK (
        parameter_name ~ '^[A-Z_]+$'
    )
);

CREATE TABLE script_parameter_choices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parameter_id UUID NOT NULL REFERENCES script_parameters(id) ON DELETE CASCADE,
    choice_value VARCHAR(255) NOT NULL,
    choice_label VARCHAR(255) NOT NULL,
    description TEXT,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (parameter_id, choice_value)
);

CREATE INDEX idx_script_parameters_script ON script_parameters(script_id, display_order);
CREATE INDEX idx_parameter_choices_param ON script_parameter_choices(parameter_id, display_order);

-- =========================
-- Build Orchestration
-- =========================
CREATE TABLE vm_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    template_id UUID NOT NULL REFERENCES templates(id),

    build_seed VARCHAR(64) NOT NULL,
    difficulty_override VARCHAR(50),
    telemetry_enabled BOOLEAN NOT NULL DEFAULT false,

    state VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    CONSTRAINT chk_vm_requests_state CHECK (state IN ('QUEUED','BUILDING','EXPORTING','READY','FAILED','ARCHIVED')),

    -- Artifact fields
    ova_filename TEXT,
    ova_sha256 VARCHAR(64),
    ova_size_bytes BIGINT,
    storage_path TEXT,
    storage_deleted_at TIMESTAMP,

    -- Progress tracking
    last_progress_at TIMESTAMP,
    current_worker_stage VARCHAR(100),

    -- Timestamps
    submitted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    claimed_at TIMESTAMP,
    building_started_at TIMESTAMP,
    exporting_started_at TIMESTAMP,
    completed_at TIMESTAMP,
    failed_at TIMESTAMP,
    failure_reason TEXT,
    archived_at TIMESTAMP,

    -- Worker tracking (V4)
    claimed_by_worker_id VARCHAR(255),
    worker_last_heartbeat_at TIMESTAMP,

    -- Constraint: building/exporting requires worker claim (V4 semantics, adjusted to uppercase states)
    CONSTRAINT chk_worker_claim CHECK (
        (state IN ('BUILDING', 'EXPORTING') AND claimed_by_worker_id IS NOT NULL)
        OR (state NOT IN ('BUILDING', 'EXPORTING'))
    )
);

CREATE INDEX idx_vm_requests_state ON vm_requests(state);
CREATE INDEX idx_vm_requests_user ON vm_requests(user_id);
CREATE INDEX idx_vm_requests_submitted ON vm_requests(submitted_at);
CREATE INDEX idx_vm_requests_progress ON vm_requests(last_progress_at) WHERE state IN ('BUILDING', 'EXPORTING');
CREATE INDEX idx_vm_requests_worker ON vm_requests(claimed_by_worker_id);
CREATE INDEX idx_vm_requests_timeout ON vm_requests(last_progress_at) WHERE state IN ('BUILDING', 'EXPORTING');

CREATE TABLE vm_request_scripts (
    vm_request_id UUID NOT NULL REFERENCES vm_requests(id) ON DELETE CASCADE,
    script_id UUID NOT NULL REFERENCES scripts(id),
    execution_order INTEGER NOT NULL,
    auto_added BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (vm_request_id, script_id)
);

CREATE TABLE vm_request_script_parameters (
    vm_request_id UUID NOT NULL REFERENCES vm_requests(id) ON DELETE CASCADE,
    script_id UUID NOT NULL REFERENCES scripts(id),
    parameter_name VARCHAR(100) NOT NULL,
    parameter_value TEXT NOT NULL,
    was_auto_generated BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (vm_request_id, script_id, parameter_name)
);

CREATE INDEX idx_vm_request_script_params_request ON vm_request_script_parameters(vm_request_id);

CREATE TABLE build_logs (
    id BIGSERIAL PRIMARY KEY,
    vm_request_id UUID NOT NULL REFERENCES vm_requests(id) ON DELETE CASCADE,
    log_level VARCHAR(20) NOT NULL,
    stage VARCHAR(100),
    message TEXT NOT NULL,
    logged_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_build_logs_request ON build_logs(vm_request_id, logged_at);

-- =========================
-- Provenance
-- =========================
CREATE TABLE provenance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vm_request_id UUID UNIQUE NOT NULL REFERENCES vm_requests(id),
    template_identifier VARCHAR(255) NOT NULL,
    template_version VARCHAR(50) NOT NULL,
    template_sha256 VARCHAR(64) NOT NULL,
    build_seed VARCHAR(64) NOT NULL,
    flag_derivation_timestamp TIMESTAMP NOT NULL,
    difficulty_level VARCHAR(50),
    telemetry_enabled BOOLEAN NOT NULL,
    ova_sha256 VARCHAR(64) NOT NULL,
    finalised_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE provenance_scripts (
    provenance_id UUID NOT NULL REFERENCES provenance_records(id),
    script_identifier VARCHAR(255) NOT NULL,
    script_version VARCHAR(50) NOT NULL,
    script_sha256 VARCHAR(64) NOT NULL,
    execution_order INTEGER NOT NULL,
    PRIMARY KEY (provenance_id, execution_order)
);

CREATE TABLE provenance_parameters (
    provenance_id UUID NOT NULL REFERENCES provenance_records(id),
    parameter_name VARCHAR(100) NOT NULL,
    parameter_value TEXT NOT NULL,
    PRIMARY KEY (provenance_id, parameter_name)
);

CREATE TABLE provenance_dependencies (
    id BIGSERIAL PRIMARY KEY,
    provenance_id UUID NOT NULL REFERENCES provenance_records(id),
    dependency_type VARCHAR(50) NOT NULL,
    dependency_name VARCHAR(255) NOT NULL,
    dependency_version VARCHAR(100),
    source_url TEXT,
    sha256_hash VARCHAR(64),
    retrieved_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_provenance_dependencies ON provenance_dependencies(provenance_id);
CREATE INDEX idx_provenance_dep_type ON provenance_dependencies(dependency_type, dependency_name);

-- =========================
-- Flags / Submissions
-- =========================
CREATE TABLE flags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vm_request_id UUID NOT NULL REFERENCES vm_requests(id) ON DELETE CASCADE,
    script_id UUID NOT NULL REFERENCES scripts(id),
    flag_value_hash VARCHAR(64) NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1,
    flag_derivation_timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(vm_request_id, script_id)
);

CREATE INDEX idx_flags_request ON flags(vm_request_id);

CREATE TABLE submission_attempts (
    id BIGSERIAL PRIMARY KEY,
    flag_id UUID NOT NULL REFERENCES flags(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    submitted_value_hash VARCHAR(64) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_submissions_flag ON submission_attempts(flag_id);
CREATE INDEX idx_submissions_user ON submission_attempts(user_id, submitted_at);

-- =========================
-- Telemetry Uploads
-- =========================
CREATE TABLE telemetry_uploads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vm_request_id UUID NOT NULL REFERENCES vm_requests(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX idx_telemetry_request ON telemetry_uploads(vm_request_id);
CREATE INDEX idx_telemetry_user ON telemetry_uploads(user_id, uploaded_at);

-- =========================
-- Worker Monitoring
-- =========================
CREATE TABLE worker_heartbeats (
    worker_id VARCHAR(255) PRIMARY KEY,
    hostname VARCHAR(255) NOT NULL,
    current_job_id UUID REFERENCES vm_requests(id),
    current_stage VARCHAR(100),
    state VARCHAR(50) NOT NULL,
    last_heartbeat_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_worker_heartbeat_job ON worker_heartbeats(current_job_id) WHERE current_job_id IS NOT NULL;
