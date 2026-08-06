#!/usr/bin/env bash
#
# Bring up the local infrastructure stack (PostgreSQL + MinIO) in the background.
#
# Usage:
#   ./build.sh                 # docker compose up -d
#   ./build.sh --wait          # also block until healthchecks pass
#   ./build.sh postgres        # only bring up selected service(s)
#
# Any extra arguments are forwarded to `docker compose up -d`.

set -euo pipefail

# Resolve paths relative to this script so it works from any working directory.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/app/docker-compose.yml"
PROJECT_NAME="polygot-ecommerce"

die() {
  printf '\033[31merror:\033[0m %s\n' "$1" >&2
  exit 1
}

info() {
  printf '\033[36m==>\033[0m %s\n' "$1"
}

# --- preflight ---------------------------------------------------------------

command -v docker >/dev/null 2>&1 ||
  die "docker is not installed or not on PATH"

# Compose v2 ships as a docker plugin; fall back to the standalone v1 binary.
if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  die "docker compose is not available (install the Compose plugin)"
fi

docker info >/dev/null 2>&1 ||
  die "cannot reach the Docker daemon — is Docker running?"

[[ -f "$COMPOSE_FILE" ]] ||
  die "compose file not found: $COMPOSE_FILE"

COMPOSE+=(--project-name "$PROJECT_NAME" --file "$COMPOSE_FILE")

# Compose auto-loads a .env sitting next to the compose file. Honour one at the
# repo root too, since that is where it is most natural to keep it.
if [[ -f "$SCRIPT_DIR/.env" ]]; then
  info "using env file: .env"
  COMPOSE+=(--env-file "$SCRIPT_DIR/.env")
fi

# --- build ------------------------------------------------------------------

info "starting stack from app/docker-compose.yml"
"${COMPOSE[@]}" up -d "$@"

info "current state"
"${COMPOSE[@]}" ps
