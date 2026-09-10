#!/usr/bin/env bash
#
# Tear down the local infrastructure stack started by build.sh.
#
# Usage:
#   ./down.sh                   # stop and remove containers + network
#   ./down.sh -v                # ALSO delete data volumes (destroys the DB)
#   ./down.sh --rmi local       # also remove locally built images
#
# Any extra arguments are forwarded to `docker compose down`. Volumes are kept
# unless you explicitly ask for them to be removed.

set -euo pipefail

# Resolve paths relative to this script so it works from any working directory.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/app/docker-compose.yml"
PROJECT_NAME="app"

die() {
  printf '\033[31merror:\033[0m %s\n' "$1" >&2
  exit 1
}

info() {
  printf '\033[36m==>\033[0m %s\n' "$1"
}

warn() {
  printf '\033[33mwarning:\033[0m %s\n' "$1" >&2
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

# Keep env resolution identical to build.sh, otherwise compose interpolates
# different values here and can address a different set of resources.
if [[ -f "$SCRIPT_DIR/.env" ]]; then
  info "using env file: .env"
  COMPOSE+=(--env-file "$SCRIPT_DIR/.env")
fi

# --- guard destructive volume removal ----------------------------------------

# `down -v` deletes postgres-data and minio-data for good, so make the caller
# confirm. Set FORCE=1 to skip the prompt in non-interactive use (CI, Makefile).
removes_volumes=false
for arg in "$@"; do
  case "$arg" in
    -v | --volumes) removes_volumes=true ;;
  esac
done

if [[ "$removes_volumes" == true && "${FORCE:-0}" != 1 ]]; then
  warn "this will permanently delete the postgres-data and minio-data volumes"
  if [[ ! -t 0 ]]; then
    die "refusing to delete volumes without a terminal — re-run with FORCE=1"
  fi
  read -r -p "Type 'yes' to continue: " reply
  [[ "$reply" == yes ]] || die "aborted, nothing was removed"
fi

# --- teardown ----------------------------------------------------------------

info "stopping stack from app/docker-compose.yml"
"${COMPOSE[@]}" down "$@"

info "remaining containers for this project"
"${COMPOSE[@]}" ps --all
