ALTER TABLE templates
    ADD COLUMN ssh_auth_method VARCHAR(20) NOT NULL DEFAULT 'password_reset'
        CHECK (ssh_auth_method IN ('cloudinit', 'password_reset', 'static_key')),
    ADD COLUMN last_validation_attempt TIMESTAMP,
    ADD COLUMN validation_duration_seconds INTEGER,
    ADD COLUMN proxmox_template_name VARCHAR(255);

