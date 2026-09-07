#!/usr/bin/env bash
#
# Bring up the local infrastructure stack (PostgreSQL + MinIO + Keycloak) in the
# background.
#
# A cold first boot takes several minutes: Keycloak has to build its schema in
# an empty database before it reports healthy. Once it answers, this script
# provisions the realm by running ./app/init/keycloak-init.sh for you.
#
# It also migrates the `ecommerce` database by running ./app/init/migrate.sh
# (Liquibase, changelogs in ./migrations). Keycloak's own database is left
# alone — it migrates itself with its bundled changelogs.
#
# Usage:
#   ./build.sh                 # up -d, migrate ecommerce, provision Keycloak
#   ./build.sh --wait          # also block until healthchecks pass
#   ./build.sh --no-init       # skip the Keycloak provisioning step
#   ./build.sh --no-migrate    # skip the database migration step
#   ./build.sh postgres        # only bring up selected service(s)
#
# Any other argument is forwarded to `docker compose up -d`.

# Re-exec under a real bash. `sh build.sh` runs this file with /bin/sh, which is
# bash in POSIX mode on macOS and dash on most Linux distros — neither runs the
# arrays and [[ ]] below the way this script expects. Keep this block
# POSIX-clean: it is parsed by whatever shell started the script, before the
# `set -o pipefail` on the next line (which dash does not support).
if [ -z "${BASH_VERSION:-}" ] || [ -n "${POSIXLY_CORRECT:-}" ]; then
  unset POSIXLY_CORRECT
  exec bash "$0" "$@"
fi

set -euo pipefail

# Resolve paths relative to this script so it works from any working directory.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/app/docker-compose.yml"
KEYCLOAK_INIT="$SCRIPT_DIR/app/init/keycloak-init.sh"
DB_MIGRATE="$SCRIPT_DIR/app/init/migrate.sh"
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

# --- arguments ---------------------------------------------------------------

# --no-init and --no-migrate are ours, everything else belongs to
# `docker compose up -d`. Track the bare (non-flag) arguments too: those are
# service names, and each provisioning step only makes sense when the service
# it targets is actually part of what is being started.
no_init=false
no_migrate=false
compose_args=()
services=()

for arg in "$@"; do
  case "$arg" in
    --no-init) no_init=true ;;
    --no-migrate) no_migrate=true ;;
    -*) compose_args+=("$arg") ;;
    *)
      compose_args+=("$arg")
      services+=("$arg")
      ;;
  esac
done

# With no service list every service starts — keycloak and postgres included.
run_init=true
run_migrate=true
if ((${#services[@]})); then
  # An explicit list that leaves keycloak out means there is nothing to
  # provision — `docker compose up keycloak` is what pulls and starts it. Same
  # for postgres and the migration: no database container, nothing to migrate.
  run_init=false
  run_migrate=false
  for svc in "${services[@]}"; do
    case "$svc" in
      keycloak) run_init=true ;;
      postgres) run_migrate=true ;;
    esac
  done
fi

if [[ "$no_init" == true ]]; then
  run_init=false
fi

if [[ "$no_migrate" == true ]]; then
  run_migrate=false
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

# --- migrate the ecommerce database ------------------------------------------

# Runs before the Keycloak step so the schema the business services need exists
# as early as possible: on a cold boot keycloak-init.sh sits and waits several
# minutes for Keycloak, and there is no reason for the migration to queue
# behind it. migrate.sh waits for Postgres itself (READY_TIMEOUT).
if [[ "$run_migrate" == true ]]; then
  if [[ ! -x "$DB_MIGRATE" ]]; then
    warn "skipping database migration: $DB_MIGRATE is missing or not executable"
  else
    info "migrating the ecommerce database (app/init/migrate.sh)"
    # Same env resolution as compose, so Liquibase connects with the
    # credentials the Postgres container was actually started with.
    migrate_status=0
    (
      if [[ -f "$SCRIPT_DIR/.env" ]]; then
        set -a
        # shellcheck disable=SC1091
        source "$SCRIPT_DIR/.env"
        set +a
      fi
      # Keep the migration on the same compose project as the stack above, so
      # it attaches the Liquibase container to the network that was created
      # here instead of guessing one from the directory name.
      export COMPOSE_PROJECT_NAME="$PROJECT_NAME"
      exec "$DB_MIGRATE"
    ) || migrate_status=$?

    # Stop here on failure instead of continuing: every business service starts
    # against a schema that is now in an unknown state, and a half-migrated
    # database is far easier to diagnose now than through the errors the
    # services would throw later.
    if ((migrate_status != 0)); then
      warn "database migration FAILED (exit $migrate_status) — the schema was not applied"
      warn "the containers are still running; fix the error above, then re-run:"
      warn "  ./app/init/migrate.sh"
      exit "$migrate_status"
    fi
  fi
else
  info "skipping database migration (run ./app/init/migrate.sh manually)"
fi

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
    init_status=0
    (
      if [[ -f "$SCRIPT_DIR/.env" ]]; then
        set -a
        # shellcheck disable=SC1091
        source "$SCRIPT_DIR/.env"
        set +a
      fi
      exec "$KEYCLOAK_INIT"
    ) || init_status=$?

    # Don't let a provisioning failure scroll past as "build finished": by this
    # point the containers are already up, so the stack looks fine while the
    # realm, clients, roles and users silently do not exist.
    if ((init_status != 0)); then
      warn "Keycloak provisioning FAILED (exit $init_status) — the realm was not created"
      warn "the containers are still running; fix the error above, then re-run:"
      warn "  ./app/init/keycloak-init.sh"
      exit "$init_status"
    fi
  fi
else
  info "skipping Keycloak provisioning (run ./app/init/keycloak-init.sh manually)"
fi
