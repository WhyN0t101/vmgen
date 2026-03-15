-- Template SSH credentials
-- Stores the actual credential used by the worker to SSH into VMs built from this template.
-- ssh_password  : used when ssh_auth_method = 'password_reset'
-- ssh_private_key: used when ssh_auth_method = 'static_key' (full PEM content, not a file path)
-- cloudinit templates need neither — the worker generates an ephemeral key pair per build.

ALTER TABLE templates
    ADD COLUMN ssh_password     VARCHAR(255),
    ADD COLUMN ssh_private_key  TEXT;
