-- V5: Store the per-build VM login password (admin-only, never exposed to students)
ALTER TABLE provenance_records
    ADD COLUMN vm_login_password VARCHAR(64);
