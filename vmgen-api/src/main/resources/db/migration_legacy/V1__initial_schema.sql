-- User Management
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       username VARCHAR(255) UNIQUE NOT NULL,
                       email VARCHAR(255) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       role VARCHAR(50) NOT NULL DEFAULT 'user',
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

-- Template Repository
CREATE TABLE templates (
                           id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           identifier VARCHAR(255) NOT NULL,
                           version VARCHAR(50) NOT NULL,
                           os_family VARCHAR(100) NOT NULL,
                           os_version VARCHAR(100) NOT NULL,
                           architecture VARCHAR(50) NOT NULL,
                           description TEXT,
                           source_type VARCHAR(50) NOT NULL,
                           source_reference TEXT NOT NULL,
                           storage_path TEXT,
                           sha256_hash VARCHAR(64) NOT NULL,
                           disk_size_gb INTEGER NOT NULL,
                           min_ram_mb INTEGER NOT NULL DEFAULT 2048,
                           min_cpu_cores INTEGER NOT NULL DEFAULT 2,
                           is_active BOOLEAN NOT NULL DEFAULT true,
                           validated_at TIMESTAMP,
                           validated_by UUID REFERENCES users(id),
                           validation_notes TEXT,
                           created_by UUID NOT NULL REFERENCES users(id),
                           created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                           deprecated_at TIMESTAMP,
                           UNIQUE(identifier, version)
);

CREATE TABLE template_capabilities (
                                       template_id UUID NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
                                       capability VARCHAR(100) NOT NULL,
                                       PRIMARY KEY (template_id, capability)
);

CREATE INDEX idx_templates_active ON templates(is_active) WHERE is_active = true;
CREATE INDEX idx_templates_family ON templates(os_family);

-- Script Repository
CREATE TABLE scripts (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         identifier VARCHAR(255) NOT NULL,
                         version VARCHAR(50) NOT NULL,
                         name VARCHAR(255) NOT NULL,
                         description TEXT,
                         vulnerability_category VARCHAR(100) NOT NULL,
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

CREATE INDEX idx_scripts_active ON scripts(is_active) WHERE is_active = true;
CREATE INDEX idx_scripts_category ON scripts(vulnerability_category);

-- Build Orchestration
CREATE TABLE vm_requests (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             user_id UUID NOT NULL REFERENCES users(id),
                             template_id UUID NOT NULL REFERENCES templates(id),
                             build_seed VARCHAR(64) NOT NULL,
                             difficulty_override VARCHAR(50),
                             telemetry_enabled BOOLEAN NOT NULL DEFAULT false,
                             state VARCHAR(50) NOT NULL DEFAULT 'queued',
                             ova_filename TEXT,
                             ova_sha256 VARCHAR(64),
                             ova_size_bytes BIGINT,
                             storage_path TEXT,
                             storage_deleted_at TIMESTAMP,
                             last_progress_at TIMESTAMP,
                             current_stage VARCHAR(100),
                             submitted_at TIMESTAMP NOT NULL DEFAULT NOW(),
                             claimed_at TIMESTAMP,
                             building_started_at TIMESTAMP,
                             exporting_started_at TIMESTAMP,
                             completed_at TIMESTAMP,
                             failed_at TIMESTAMP,
                             failure_reason TEXT,
                             archived_at TIMESTAMP
);

CREATE TABLE vm_request_scripts (
                                    vm_request_id UUID NOT NULL REFERENCES vm_requests(id) ON DELETE CASCADE,
                                    script_id UUID NOT NULL REFERENCES scripts(id),
                                    execution_order INTEGER NOT NULL,
                                    PRIMARY KEY (vm_request_id, script_id)
);

CREATE TABLE build_logs (
                            id BIGSERIAL PRIMARY KEY,
                            vm_request_id UUID NOT NULL REFERENCES vm_requests(id) ON DELETE CASCADE,
                            log_level VARCHAR(20) NOT NULL,
                            stage VARCHAR(100),
                            message TEXT NOT NULL,
                            logged_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vm_requests_state ON vm_requests(state);
CREATE INDEX idx_vm_requests_user ON vm_requests(user_id);
CREATE INDEX idx_vm_requests_submitted ON vm_requests(submitted_at);
CREATE INDEX idx_vm_requests_progress ON vm_requests(last_progress_at) WHERE state IN ('building', 'exporting');
CREATE INDEX idx_build_logs_request ON build_logs(vm_request_id, logged_at);

-- Provenance Records
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

-- Flags
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
CREATE INDEX idx_flags_request ON flags(vm_request_id);

-- Telemetry
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

-- Worker Monitoring
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

-- Audit Log
CREATE TABLE audit_log (
                           id BIGSERIAL PRIMARY KEY,
                           user_id UUID REFERENCES users(id),
                           action VARCHAR(100) NOT NULL,
                           resource_type VARCHAR(100),
                           resource_id UUID,
                           metadata JSONB,
                           ip_address INET,
                           user_agent TEXT,
                           occurred_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user ON audit_log(user_id, occurred_at);
CREATE INDEX idx_audit_action ON audit_log(action, occurred_at);



