#!/bin/sh
#
# Create the object storage buckets this stack needs inside MinIO:
#
#   ecommerce  - product images uploaded by product-service
#
# This runs inside the throwaway `minio-init` container of
# app/docker-compose.yml (image quay.io/minio/mc), which compose starts once
# MinIO reports healthy and then lets exit. Services that need the bucket wait
# for that exit via `depends_on: { minio-init: service_completed_successfully }`.
#
# Every step is validated first and only applied when missing, so the script is
# safe to re-run. Unlike the Postgres entrypoint, compose re-runs this on every
# `docker compose up`, so it must stay idempotent.
#
# To run it by hand against a stack that is already up:
#
#   docker compose run --rm minio-init
#
# Configuration comes from the environment so it shares the same .env as
# app/docker-compose.yml:
#
#   MINIO_URL             MinIO API endpoint            (http://minio:9000)
#   MINIO_ROOT_USER       access key                    (admin)
#   MINIO_ROOT_PASSWORD   secret key                    (password)
#   MINIO_BUCKETS         buckets to create, space or comma separated (ecommerce)
#   MINIO_BUCKET_POLICY   anonymous access policy       (download)
#                         download = public read, needed because
#                         product-service hands out direct object URLs
#                         (see configuration.MinioObjectURL). Use "none" to
#                         keep the buckets private.
#   MINIO_READY_RETRIES   readiness attempts, 2s apart  (30)
set -eu

MINIO_URL="${MINIO_URL:-http://minio:9000}"
MINIO_ROOT_USER="${MINIO_ROOT_USER:-admin}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-password}"
MINIO_BUCKETS="${MINIO_BUCKETS:-ecommerce}"
MINIO_BUCKET_POLICY="${MINIO_BUCKET_POLICY:-download}"
MINIO_READY_RETRIES="${MINIO_READY_RETRIES:-30}"

ALIAS=stack

log() {
  echo "init-bucket: $1"
}

# --- wait for MinIO ----------------------------------------------------------

# The healthcheck already gates this container, but a healthy MinIO can still
# refuse the very first call while it finishes loading its IAM config, and
# `docker compose run minio-init` bypasses the gate entirely. Registering the
# alias is itself an authenticated round trip, so retrying it doubles as the
# readiness probe.
attempt=1
until mc --quiet alias set "$ALIAS" "$MINIO_URL" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do
  if [ "$attempt" -ge "$MINIO_READY_RETRIES" ]; then
    log "giving up: $MINIO_URL did not accept credentials after $attempt attempts" >&2
    # Surface the real error instead of the swallowed one above.
    mc alias set "$ALIAS" "$MINIO_URL" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
    exit 1
  fi

  log "waiting for $MINIO_URL ($attempt/$MINIO_READY_RETRIES)"
  attempt=$((attempt + 1))
  sleep 2
done

log "connected to $MINIO_URL"

# --- create the buckets ------------------------------------------------------

# Accept "a,b" as well as "a b" so the compose value can be written either way.
buckets="$(echo "$MINIO_BUCKETS" | tr ',' ' ')"

for bucket in $buckets; do
  [ -n "$bucket" ] || continue

  if mc --quiet ls "$ALIAS/$bucket" >/dev/null 2>&1; then
    log "bucket '$bucket' already exists, skipping"
  else
    log "creating bucket '$bucket'"
    mc --quiet mb "$ALIAS/$bucket"
  fi

  if [ "$MINIO_BUCKET_POLICY" = none ]; then
    log "bucket '$bucket' left private (MINIO_BUCKET_POLICY=none)"
    continue
  fi

  # `mc anonymous` replaced `mc policy` in 2022; fall back so an older pinned
  # mc image keeps working. Setting the same policy twice is a no-op.
  log "setting anonymous policy '$MINIO_BUCKET_POLICY' on '$bucket'"
  mc --quiet anonymous set "$MINIO_BUCKET_POLICY" "$ALIAS/$bucket" >/dev/null 2>&1 ||
    mc --quiet policy set "$MINIO_BUCKET_POLICY" "$ALIAS/$bucket"
done

log "done"
