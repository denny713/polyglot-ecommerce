#!/usr/bin/env bash
#
# Scaffold a new Liquibase migration for the `ecommerce` database.
#
# It creates migrations/changelog/<yyyyMMddHHmmss>-<name>.sql from a formatted
# SQL template and appends the matching <include> to
# migrations/db.changelog-master.xml, so the only thing left to do is write the
# SQL itself.
#
# Usage:
#   ./create-migration.sh                      # asks for the name
#   ./create-migration.sh "create table order" # or takes it as an argument
#
# The name is slugified (lowercased, spaces and underscores become hyphens), so
# "Create Table Order", "create_table_order" and "create table order" all end up
# as 20260821143012-create-table-order.sql.
#
# The timestamp prefix is what orders the files on disk; the order Liquibase
# actually runs them in is the <include> order in the master changelog, and this
# script always appends - never inserts - so the two agree.
#
# Environment:
#   MIGRATION_AUTHOR   author recorded in the changeset  (denny.afrizal)

# The blank line above ends the help text that --help prints; what follows is
# implementation detail.
#
# Re-exec under a real bash. `sh create-migration.sh` runs this file with
# /bin/sh, which is bash in POSIX mode on macOS and dash on most Linux distros -
# neither runs the [[ ]] and ${var// /} below the way this script expects. Keep
# this block POSIX-clean: it is parsed by whatever shell started the script,
# before the `set -o pipefail` on the next line (which dash does not support).
if [ -z "${BASH_VERSION:-}" ] || [ -n "${POSIXLY_CORRECT:-}" ]; then
  unset POSIXLY_CORRECT
  exec bash "$0" "$@"
fi

set -euo pipefail

# --- configuration -----------------------------------------------------------

# Resolve paths relative to this script so it works from any working directory.
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-$SCRIPT_DIR/migrations}"
CHANGELOG_DIR="$MIGRATIONS_DIR/changelog"
MASTER_CHANGELOG="$MIGRATIONS_DIR/db.changelog-master.xml"
AUTHOR="${MIGRATION_AUTHOR:-denny.afrizal}"

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

case "${1:-}" in
  -h | --help)
    # Print this file's own header comment as the help text, so the two can
    # never drift apart. awk rather than sed: the nested-brace form BSD sed
    # rejects is not worth the portability argument.
    awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "${BASH_SOURCE[0]}"
    exit 0
    ;;
esac

# --- preflight ---------------------------------------------------------------

[[ -d "$CHANGELOG_DIR" ]] ||
  die "changelog directory not found: $CHANGELOG_DIR"

[[ -f "$MASTER_CHANGELOG" ]] ||
  die "master changelog not found: $MASTER_CHANGELOG"

# --- name ---------------------------------------------------------------------

raw_name="${*:-}"

# Nothing on the command line: ask. Guard on a terminal first - in CI or a
# pipe `read` would see EOF and the script would fail with an empty name
# instead of saying what it wanted.
if [[ -z "${raw_name// /}" ]]; then
  [[ -t 0 ]] ||
    die "no changelog name given and stdin is not a terminal (pass it as an argument)"

  # Keep asking rather than failing on a stray Enter. Ctrl-D still gets out:
  # `read` reports EOF, which is a deliberate cancel, not an empty answer.
  while [[ -z "${raw_name// /}" ]]; do
    printf 'Changelog name (e.g. create table order): '
    IFS= read -r raw_name || die "cancelled - no changelog name given"
    [[ -n "${raw_name// /}" ]] || warn "the name cannot be empty"
  done
fi

# Slugify: lowercase, anything that is not a letter or digit collapses into a
# single hyphen, and hyphens are trimmed off both ends. Keeps the file name
# safe for a shell, a URL and an XML attribute alike.
slug="$(printf '%s' "$raw_name" |
  tr '[:upper:]' '[:lower:]' |
  sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//')"

[[ -n "$slug" ]] ||
  die "changelog name must contain at least one letter or digit, got: '$raw_name'"

# --- generate -----------------------------------------------------------------

timestamp="$(date +%Y%m%d%H%M%S)"
file_name="${timestamp}-${slug}.sql"
file_path="$CHANGELOG_DIR/$file_name"
changeset_id="${timestamp}-${slug}"

# Two runs within the same second would otherwise silently overwrite the first.
[[ ! -e "$file_path" ]] ||
  die "migration already exists: migrations/changelog/$file_name"

# The template ends on a statement that cannot succeed, on purpose. A changeset
# whose body is only comments is perfectly valid to Liquibase: `update` records
# it as EXECUTED and moves on, so an unfinished migration would be silently
# marked as applied - and writing the real SQL afterwards would then fail on the
# changed checksum. The placeholder below makes that state fail immediately
# instead, with an error naming the scaffold.
cat > "$file_path" <<TEMPLATE
--liquibase formatted sql
--
-- ${slug//-/ }
--changeset ${AUTHOR}:${changeset_id}
CREATE TABLE IF NOT EXISTS table_name (
    id          BIGSERIAL       PRIMARY KEY,
    is_active   BOOLEAN        NOT NULL DEFAULT TRUE,
    is_deleted  BOOLEAN        NOT NULL DEFAULT FALSE,
    created_by  BIGINT,
    updated_by  BIGINT,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    -- Default audit and soft-delete fields.
    -- Remove the unused fields when they are not required by this table.

    -- TODO: Describe the purpose of this table and the changes introduced by this changeset.
)

--rollback DROP TABLE IF EXISTS table_name
TEMPLATE

# --- register in the master changelog ----------------------------------------

# Append the <include> as the last one, right before the closing tag. Reusing
# the indentation of the existing includes keeps the file tidy without
# hardcoding a width here.
python3 - "$MASTER_CHANGELOG" "$file_name" <<'PY'
import re
import sys

master_path, file_name = sys.argv[1], sys.argv[2]

with open(master_path) as handle:
    content = handle.read()

include = f'<include file="changelog/{file_name}" relativeToChangelogFile="true"/>'
if include in content:
    sys.exit(0)

existing = list(re.finditer(r'^([ \t]*)<include\b.*$', content, re.MULTILINE))
if existing:
    # After the current last include, matching its indentation.
    last = existing[-1]
    insert_at, indent = last.end(), last.group(1)
else:
    # No includes yet: put it just before </databaseChangeLog>.
    closing = re.search(r'^([ \t]*)</databaseChangeLog>', content, re.MULTILINE)
    if not closing:
        sys.stderr.write('error: no </databaseChangeLog> in %s\n' % master_path)
        sys.exit(1)
    insert_at, indent = closing.start() - 1, '  '

content = content[:insert_at] + '\n' + indent + include + content[insert_at:]

with open(master_path, 'w') as handle:
    handle.write(content)
PY

info "created  migrations/changelog/$file_name"
info "included in migrations/db.changelog-master.xml"
printf '\nNext: write the SQL and its rollback, then apply it with\n\n  ./app/init/migrate.sh updateSQL   # preview\n  ./app/init/migrate.sh            # apply\n\n'
