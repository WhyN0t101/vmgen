#!/bin/bash
# =============================================================================
# Creates the restricted runtime database user used by the API for normal
# operation (DML only: SELECT/INSERT/UPDATE/DELETE). Schema migrations (Flyway)
# continue to run as the owner role, which holds DDL rights.
#
# OPT-IN: this only acts when DB_API_PASSWORD is set in the environment. When it
# is unset (the default), the API connects as the owner role exactly as before,
# so local dev and the default deployment are unchanged.
#
# Runs once, on first database initialisation (empty data dir). Enabling the
# restricted user on an EXISTING volume requires a fresh DB (docker compose
# down -v) or creating the role manually.
#
# NOTE: written to be safe whether the postgres entrypoint executes OR sources
# this file — no `set -e` / `exit`, both of which would leak into the entrypoint.
# =============================================================================

if [ -n "${DB_API_PASSWORD:-}" ]; then
  API_USER="${DB_API_USERNAME:-vmgen_api}"
  echo "[initdb] Creating restricted runtime user '${API_USER}' (DML-only)."

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
CREATE ROLE ${API_USER} LOGIN PASSWORD '${DB_API_PASSWORD}';

GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO ${API_USER};
GRANT USAGE ON SCHEMA public TO ${API_USER};

-- Tables/sequences are created later by Flyway (as ${POSTGRES_USER}). Default
-- privileges ensure the restricted user automatically receives DML on every
-- table the owner creates, including future migrations — no manual re-grant.
ALTER DEFAULT PRIVILEGES FOR ROLE ${POSTGRES_USER} IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${API_USER};
ALTER DEFAULT PRIVILEGES FOR ROLE ${POSTGRES_USER} IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO ${API_USER};
EOSQL

  echo "[initdb] Restricted user '${API_USER}' ready (DML-only; migrations stay on owner)."
else
  echo "[initdb] DB_API_PASSWORD not set — API uses the owner role. Skipping restricted user."
fi
