#!/usr/bin/env bash
#
# Bring up the local infrastructure stack (PostgreSQL + MinIO + Keycloak) in the
# background.
#
# A cold first boot takes several minutes: Keycloak has to build its schema in
# an empty database before it reports healthy. Once it answers, this script
# provisions the realm by running ./app/init/keycloak-init.sh for you.
#
# Usage:
#   ./build.sh                 # docker compose up -d, then provision Keycloak
#   ./build.sh --wait          # also block until healthchecks pass
#   ./build.sh --no-init       # skip the Keycloak provisioning step
#   ./build.sh postgres        # only bring up selected service(s)
#
# Any other argument is forwarded to `docker compose up -d`.

set -euo pipefail

# Resolve paths relative to this script so it works from any working directory.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/app/docker-compose.yml"
KEYCLOAK_INIT="$SCRIPT_DIR/app/init/keycloak-init.sh"
PROJECT_NAME="polygot-ecommerce"

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

# --- arguments ---------------------------------------------------------------

# --no-init is ours, everything else belongs to `docker compose up -d`. Track
# the bare (non-flag) arguments too: those are service names, and provisioning
# only makes sense when keycloak is actually part of what is being started.
no_init=false
compose_args=()
services=()

for arg in "$@"; do
  case "$arg" in
    --no-init) no_init=true ;;
    -*) compose_args+=("$arg") ;;
    *)
      compose_args+=("$arg")
      services+=("$arg")
      ;;
  esac
done

# With no service list every service starts, keycloak included.
run_init=true
if ((${#services[@]})); then
  # An explicit list that leaves keycloak out means there is nothing to
  # provision — `docker compose up keycloak` is what pulls and starts it.
  run_init=false
  for svc in "${services[@]}"; do
    if [[ "$svc" == keycloak ]]; then
      run_init=true
    fi
  done
fi

if [[ "$no_init" == true ]]; then
  run_init=false
fi

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
"${COMPOSE[@]}" up -d ${compose_args[@]+"${compose_args[@]}"}

info "current state"
"${COMPOSE[@]}" ps

# --- provision keycloak -------------------------------------------------------

# `up -d` returns as soon as the containers are started, which on a cold boot is
# long before Keycloak can answer — the image still has to be pulled and the
# schema built. keycloak-init.sh polls for readiness itself (READY_TIMEOUT), so
# handing over to it here is enough; no extra wait is needed on this side.
if [[ "$run_init" == true ]]; then
  if [[ ! -x "$KEYCLOAK_INIT" ]]; then
    warn "skipping Keycloak provisioning: $KEYCLOAK_INIT is missing or not executable"
  else
    info "provisioning Keycloak (app/init/keycloak-init.sh)"
    # Same env resolution as compose, so the script talks to Keycloak with the
    # credentials the container was actually started with.
    (
      if [[ -f "$SCRIPT_DIR/.env" ]]; then
        set -a
        # shellcheck disable=SC1091
        source "$SCRIPT_DIR/.env"
        set +a
      fi
      exec "$KEYCLOAK_INIT"
    )
  fi
else
  info "skipping Keycloak provisioning (run ./app/init/keycloak-init.sh manually)"
fi
