-- Bootstrap script to promote the first administrator.
-- Usage (inside your postgres container):
--   psql -U vmgen -d vmgen -f /path/to/promote_initial_admin.sql
--
-- IMPORTANT:
-- 1) First register a normal user via the API (e.g. email 'admin@example.com').
-- 2) Edit the EMAIL_HERE placeholder below to match that email.
-- Steps:
-- Copy the script into the container (or mount the repo). Easiest if your compose already mounts the project; otherwise:
--   docker cp vmgen/vmgen-api/db/bootstrap/promote_initial_admin.sql vmgen-postgres:/tmp/promote_initial_admin.sql
-- Exec into the container and run psql:
--    docker exec -it vmgen-postgres \  psql -U vmgen -d vmgen -f /tmp/promote_initial_admin.sql-- Verify:
--   SELECT username, email, role FROM users WHERE role = 'admin';
-- After that, log in with that account via your frontend/API and you’ll have a proper admin that can use the /api/admin/** endpoints to manage others.

BEGIN;

-- Replace this with the email of the account you want to promote
-- Example: admin@example.com
DO $$
DECLARE
    admin_count integer;
BEGIN
    SELECT COUNT(*) INTO admin_count FROM users WHERE role = 'admin';

    IF admin_count = 0 THEN
        UPDATE users
        SET role = 'admin'
        WHERE lower(email) = lower('admin@example.com');
        -- You can also match by username instead of email if you prefer:
        -- WHERE lower(username) = lower('thesis-admin');
    END IF;
END $$;

COMMIT;


