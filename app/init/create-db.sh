#!/bin/bash
#
# Create the extra databases this stack needs inside the shared Postgres
# instance:
#
#   keycloak   - Keycloak manages ~90 tables of its own, so it gets its own
#                database rather than sharing the application schema
#   ecommerce  - the application schema shared by the business services
#                (product-service and the services that follow it)
#
# The database named by POSTGRES_DB is created by the Postgres entrypoint
# itself, so it is deliberately not listed here.
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
  "${KEYCLOAK_DB:-keycloak}"
  "${ECOMMERCE_DB:-ecommerce}"
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
