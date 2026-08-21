#!/usr/bin/env bash
#
# Apply the Liquibase migrations in ./migrations to the `ecommerce` database.
#
# ./build.sh runs this automatically once Postgres is healthy. To run it by
# hand:
#
#   ./app/init/migrate.sh                  # apply everything that is pending
#   ./app/init/migrate.sh status           # what is pending, nothing applied
#   ./app/init/migrate.sh history          # what has already been applied
#   ./app/init/migrate.sh validate         # check the changelog for problems
#   ./app/init/migrate.sh updateSQL        # print the SQL instead of running it
#   ./app/init/migrate.sh rollbackCount 1  # undo the last changeset
#
# Any argument is forwarded verbatim to the Liquibase CLI, so every other
# Liquibase command works too. With no argument it runs `update`.
#
# Only the `ecommerce` database is migrated here. Keycloak owns its own
# schema in the `keycloak` database and migrates itself on boot with its own
# bundled changelogs - it must not be touched from the outside.
#
# Liquibase itself is run in a throwaway container (no local install needed),
# attached to the compose network so it reaches Postgres under its service
# name. Set LIQUIBASE_RUNNER=local to use a `liquibase` binary on the host
# instead - then DB_HOST defaults to localhost.
#
# Configuration comes from the environment so it can share the same .env file
# as app/docker-compose.yml. Defaults match the compose defaults:
#
#   POSTGRES_USER        database user                (postgres)
#   POSTGRES_PASSWORD    its password                 (p@ssw0rd)
#   ECOMMERCE_DB         database to migrate          (ecommerce)
#   DB_HOST              host Liquibase connects to   (postgres | localhost)
#   DB_PORT              its port                     (5432)
#   LIQUIBASE_RUNNER     docker | local               (docker)
#   LIQUIBASE_IMAGE      image used by the runner      (liquibase/liquibase:4.33-alpine)
#   COMPOSE_PROJECT_NAME compose project name         (ecommerce)
#   COMPOSE_NETWORK      network to attach to         (<project>_polygot)
#   POSTGRES_CONTAINER   container polled for readiness (postgres)
#   CHANGELOG_FILE       master changelog, relative to ./migrations
#                                                     (db.changelog-master.xml)
#   READY_TIMEOUT        seconds to wait for Postgres (120)
#
# Re-exec under a real bash. When this is invoked as `sh app/init/migrate.sh`
# - or reached from a build.sh that was itself started that way - the shell is
# /bin/sh, which is bash in POSIX mode on macOS and dash on most Linux
# distros. Keep this block POSIX-clean: it is parsed by whatever shell started
# the script, before the `set -o pipefail` on the next line (which dash does
# not support).
if [ -z "${BASH_VERSION:-}" ] || [ -n "${POSIXLY_CORRECT:-}" ]; then
  unset POSIXLY_CORRECT
  exec bash "$0" "$@"
fi

set -euo pipefail

# --- configuration -----------------------------------------------------------

# Resolve paths relative to this script so it works from any working directory.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-$REPO_ROOT/migrations}"

RUNNER="${LIQUIBASE_RUNNER:-docker}"
LIQUIBASE_IMAGE="${LIQUIBASE_IMAGE:-liquibase/liquibase:4.33-alpine}"
# Must match the network `docker compose up` actually created. Compose prefixes
# it with the project name, and build.sh/down.sh pass `--project-name ecommerce`
# explicitly - so the default here is derived from the same name rather than
# hardcoded, otherwise the Liquibase container starts off the compose network
# and cannot resolve Postgres by its service name.
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-ecommerce}"
COMPOSE_NETWORK="${COMPOSE_NETWORK:-${COMPOSE_PROJECT_NAME}_polygot}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-postgres}"

DB_USER="${POSTGRES_USER:-postgres}"
DB_PASSWORD="${POSTGRES_PASSWORD:-p@ssw0rd}"
DB_NAME="${ECOMMERCE_DB:-ecommerce}"
DB_PORT="${DB_PORT:-5432}"
# Inside the compose network Postgres answers to its service name; a host-side
# Liquibase has to go through the published port instead.
if [[ "$RUNNER" == docker ]]; then
  DB_HOST="${DB_HOST:-$POSTGRES_CONTAINER}"
else
  DB_HOST="${DB_HOST:-localhost}"
fi

CHANGELOG_FILE="${CHANGELOG_FILE:-db.changelog-master.xml}"
READY_TIMEOUT="${READY_TIMEOUT:-120}"

# `update` is the only sensible default: it is what a fresh checkout and a
# `./build.sh` both need.
liquibase_args=("$@")
if ((${#liquibase_args[@]} == 0)); then
  liquibase_args=(update)
fi

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

[[ -d "$MIGRATIONS_DIR" ]] ||
  die "migrations directory not found: $MIGRATIONS_DIR"

[[ -f "$MIGRATIONS_DIR/$CHANGELOG_FILE" ]] ||
  die "changelog not found: $MIGRATIONS_DIR/$CHANGELOG_FILE"

case "$RUNNER" in
  docker)
    command -v docker >/dev/null 2>&1 ||
      die "docker is not installed or not on PATH (set LIQUIBASE_RUNNER=local to use a host liquibase)"
    docker info >/dev/null 2>&1 ||
      die "cannot reach the Docker daemon - is Docker running?"
    ;;
  local)
    command -v liquibase >/dev/null 2>&1 ||
      die "liquibase is not installed or not on PATH (macOS: brew install liquibase)"
    ;;
  *)
    die "LIQUIBASE_RUNNER must be 'docker' or 'local', got: $RUNNER"
    ;;
esac

# --- wait for postgres -------------------------------------------------------

# `docker compose up -d` returns as soon as the container is started, which on
# a first boot is before Postgres has finished initialising its data directory
# and running create-db.sh - so the `ecommerce` database does not exist yet and
# Liquibase would fail on connect. Poll from inside the container: that answers
# for both runners and needs no client on the host.
if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$POSTGRES_CONTAINER"; then
  info "waiting for Postgres database '$DB_NAME' (up to ${READY_TIMEOUT}s)"
  deadline=$((SECONDS + READY_TIMEOUT))
  until docker exec "$POSTGRES_CONTAINER" \
    pg_isready -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1; do
    ((SECONDS < deadline)) ||
      die "database '$DB_NAME' was not ready within ${READY_TIMEOUT}s"
    sleep 2
  done
else
  # Either the stack is not managed by this compose project, or Postgres runs
  # somewhere else entirely. Not fatal - Liquibase reports the real problem if
  # the connection then fails.
  warn "container '$POSTGRES_CONTAINER' is not running - skipping the readiness wait"
fi

# --- migrate -----------------------------------------------------------------

JDBC_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"

info "migrating $DB_NAME (${liquibase_args[*]})"

# The password goes in via LIQUIBASE_COMMAND_PASSWORD rather than --password so
# it never shows up in `ps` output or in the command echoed on failure.
# The search path is the mount point itself, not its parent: Liquibase stores
# each changeset under its path *relative to the search path*, and that path is
# part of the changeset's identity in DATABASECHANGELOG. Rooting it here keeps
# the recorded names identical to the local runner's - otherwise switching
# LIQUIBASE_RUNNER would make Liquibase see every changeset as new and try to
# re-apply the whole changelog.
if [[ "$RUNNER" == docker ]]; then
  # Fall back to whatever network Postgres is actually attached to: it keeps
  # this working when the stack was brought up under a different project name.
  if ! docker network inspect "$COMPOSE_NETWORK" >/dev/null 2>&1; then
    detected="$(docker inspect --format \
      '{{range $n, $_ := .NetworkSettings.Networks}}{{$n}}{{"\n"}}{{end}}' \
      "$POSTGRES_CONTAINER" 2>/dev/null | head -n 1 || true)"
    if [[ -n "$detected" ]]; then
      warn "network '$COMPOSE_NETWORK' does not exist - using '$detected' (from container '$POSTGRES_CONTAINER')"
      COMPOSE_NETWORK="$detected"
    fi
  fi

  network_args=()
  if docker network inspect "$COMPOSE_NETWORK" >/dev/null 2>&1; then
    network_args=(--network "$COMPOSE_NETWORK")
  elif [[ "$DB_HOST" != localhost && "$DB_HOST" != 127.0.0.1 && "$DB_HOST" != host.docker.internal ]]; then
    # Without the network, '$DB_HOST' is just an unresolvable name inside the
    # throwaway container - Liquibase would fail with a confusing
    # UnknownHostException. Say what is actually wrong instead.
    die "network '$COMPOSE_NETWORK' does not exist, so '$DB_HOST' cannot be resolved
       start the stack first (./build.sh), set COMPOSE_NETWORK to the right network,
       or run against the published port with: DB_HOST=localhost $0 ${liquibase_args[*]}"
  else
    warn "network '$COMPOSE_NETWORK' does not exist - running without it"
  fi

  docker run --rm \
    ${network_args[@]+"${network_args[@]}"} \
    --volume "$MIGRATIONS_DIR:/liquibase/changelog:ro" \
    --env LIQUIBASE_COMMAND_PASSWORD="$DB_PASSWORD" \
    "$LIQUIBASE_IMAGE" \
    --url="$JDBC_URL" \
    --username="$DB_USER" \
    --changelog-file="$CHANGELOG_FILE" \
    --search-path=/liquibase/changelog \
    "${liquibase_args[@]}"
else
  LIQUIBASE_COMMAND_PASSWORD="$DB_PASSWORD" liquibase \
    --url="$JDBC_URL" \
    --username="$DB_USER" \
    --changelog-file="$CHANGELOG_FILE" \
    --search-path="$MIGRATIONS_DIR" \
    "${liquibase_args[@]}"
fi

info "migration finished: $DB_NAME"
