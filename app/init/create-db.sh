#!/bin/bash
#
# Create the databases this stack needs inside the shared Postgres instance:
#
#   ecommerce  - the application schema
#   keycloak   - Keycloak manages ~90 tables of its own, so it gets its own
#                database rather than sharing the application schema
#
# Each database is validated first and only created when it is missing, so the
# script is safe to re-run.
#
# Postgres only runs this on FIRST initialisation of the data volume. If the
# volume already holds data, run it by hand instead:
#
#   docker compose exec postgres /docker-entrypoint-initdb.d/create-db.sh
#
set -euo pipefail

databases=(
  "${ECOMMERCE_DB:-${POSTGRES_DB:-ecommerce}}"
  "${KEYCLOAK_DB:-keycloak}"
)

for db in "${databases[@]}"; do
  exists="$(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -tAc "SELECT 1 FROM pg_database WHERE datname = '${db}'")"

  if [[ "$exists" == 1 ]]; then
    echo "init-db: database '${db}' already exists, skipping"
  else
    echo "init-db: creating database '${db}'"
    createdb --username "$POSTGRES_USER" --owner "$POSTGRES_USER" "$db"
  fi
done
