#!/usr/bin/env bash
#
# Provision Keycloak for local development: realm (with password policy and
# brute force detection), client, realm roles, users.
#
# ./build.sh runs this automatically once Keycloak is up. To run it by hand:
#
#   ./app/init/keycloak-init.sh
#
# It waits for Keycloak itself, so it is fine to start it right after
# `docker compose up -d`.
#
# Every step tolerates HTTP 409 (already exists), so the script is safe to
# re-run against a Keycloak that is already provisioned.
#
# Configuration is taken from the environment so it can share the same .env
# file as app/docker-compose.yml. Defaults match the compose defaults:
#
#   KC_URL                    base URL of Keycloak      (http://localhost:$KEYCLOAK_PORT)
#   KEYCLOAK_PORT             published port            (8080)
#   KEYCLOAK_ADMIN            bootstrap admin user      (admin)
#   KEYCLOAK_ADMIN_PASSWORD   bootstrap admin password  (P@ssw0rd)
#   KEYCLOAK_REALM            realm to create           (ecommerce)
#   KEYCLOAK_CLIENT           client id to create       (ecommerce-app)
#   KEYCLOAK_PASSWORD_POLICY  realm password policy     (see PASSWORD_POLICY)
#
# Requires curl and jq on the host (macOS: brew install jq).

set -euo pipefail

# --- configuration -----------------------------------------------------------

KC_URL="${KC_URL:-http://localhost:${KEYCLOAK_PORT:-8080}}"
ADMIN_REALM="${KEYCLOAK_ADMIN_REALM:-master}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-P@ssw0rd}"

REALM_NAME="${KEYCLOAK_REALM:-ecommerce}"
CLIENT_NAME="${KEYCLOAK_CLIENT:-ecommerce-app}"
ROLES=("admin" "user")

# format: "username:password:role:email:firstName:lastName"
#
# Email and both names are mandatory, not decoration: Keycloak 26 enables the
# "Verify Profile" required action by default, and a user missing any of those
# fields is rejected at login with "Account is not fully set up".
#
# The passwords here must satisfy PASSWORD_POLICY below — Keycloak validates
# admin-set passwords against the realm policy too, so a weak value fails the
# reset-password call with HTTP 400.
USERS=(
  "adminapp:P@ssw0rd:admin:adminapp@ecommerce.local:Administrator:App"
  "userapp:P@ssw0rd:user:userapp@ecommerce.local:User:App"
)

# Realm password policy: at least 8 characters, with at least one upper-case
# letter, one lower-case letter and one special character.
#
# Keycloak expects this as a single "name(arg) and name(arg)" string; the
# argument is the *minimum count* required for each character class.
PASSWORD_POLICY="${KEYCLOAK_PASSWORD_POLICY:-length(8) and upperCase(1) and lowerCase(1) and specialChars(1)}"

# Brute force detection. Temporary lockout (permanentLockout=false) is the safer
# default for a shared dev realm: a locked account frees itself instead of
# needing an admin to re-enable it.
#
#   failureFactor                5 failed logins arm the lockout
#   waitIncrementSeconds        60 first lockout, doubling on each further trip
#   maxFailureWaitSeconds      900 cap on that back-off (15 minutes)
#   maxDeltaTimeSeconds      43200 failure counter resets after 12 idle hours
#   quickLoginCheckMilliSeconds / minimumQuickLoginWaitSeconds
#                                two logins closer than 1s look scripted and
#                                earn an immediate 60s wait
BRUTE_FORCE_FAILURE_FACTOR="${KEYCLOAK_BRUTE_FORCE_FAILURE_FACTOR:-5}"
BRUTE_FORCE_WAIT_INCREMENT="${KEYCLOAK_BRUTE_FORCE_WAIT_INCREMENT:-60}"
BRUTE_FORCE_MAX_WAIT="${KEYCLOAK_BRUTE_FORCE_MAX_WAIT:-900}"
BRUTE_FORCE_MAX_DELTA="${KEYCLOAK_BRUTE_FORCE_MAX_DELTA:-43200}"
BRUTE_FORCE_QUICK_LOGIN_MS="${KEYCLOAK_BRUTE_FORCE_QUICK_LOGIN_MS:-1000}"
BRUTE_FORCE_QUICK_LOGIN_WAIT="${KEYCLOAK_BRUTE_FORCE_QUICK_LOGIN_WAIT:-60}"

# Seconds to wait for Keycloak to answer. A cold first boot against an empty
# database spends ~5 minutes on Quarkus augmentation and the Liquibase schema
# build, so this has to outlast that, not just a warm restart.
READY_TIMEOUT="${READY_TIMEOUT:-360}"

# --- helpers -----------------------------------------------------------------

die() {
  printf '\033[31merror:\033[0m %s\n' "$1" >&2
  exit 1
}

info() {
  printf '\033[36m==>\033[0m %s\n' "$1"
}

# POST a JSON body to the admin API. Treats 409 as success so the script stays
# idempotent, and fails loudly on anything else.
post_json() {
  local label="$1" url="$2" body="$3" code
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$url" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "$body")
  case "$code" in
    200 | 201 | 204) printf '    %s: created (HTTP %s)\n' "$label" "$code" ;;
    409) printf '    %s: already exists, skipping (HTTP 409)\n' "$label" ;;
    *) die "$label: unexpected HTTP $code" ;;
  esac
}

put_json() {
  local label="$1" url="$2" body="$3" code
  code=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$url" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "$body")
  case "$code" in
    200 | 204) printf '    %s: ok (HTTP %s)\n' "$label" "$code" ;;
    *) die "$label: unexpected HTTP $code" ;;
  esac
}

# --- preflight ---------------------------------------------------------------

command -v curl >/dev/null 2>&1 || die "curl is not installed or not on PATH"
command -v jq >/dev/null 2>&1 || die "jq is not installed or not on PATH"

# --- 1/6 wait for keycloak ---------------------------------------------------

# The container healthcheck lives on port 9000, which compose does not publish,
# so poll the realm discovery document on the public port instead.
info "[1/6] waiting for Keycloak at $KC_URL (up to ${READY_TIMEOUT}s)"
deadline=$((SECONDS + READY_TIMEOUT))
until curl -sf -o /dev/null \
  "$KC_URL/realms/$ADMIN_REALM/.well-known/openid-configuration"; do
  ((SECONDS < deadline)) ||
    die "Keycloak did not become reachable at $KC_URL within ${READY_TIMEOUT}s"
  sleep 3
done
echo "    reachable."

# --- 2/6 admin token --------------------------------------------------------

info "[2/6] requesting admin token from realm '$ADMIN_REALM'"
ADMIN_TOKEN=$(curl -s -X POST \
  "$KC_URL/realms/$ADMIN_REALM/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=admin-cli' \
  -d 'grant_type=password' \
  --data-urlencode "username=$ADMIN_USER" \
  --data-urlencode "password=$ADMIN_PASS" | jq -r '.access_token // empty')

[[ -n "$ADMIN_TOKEN" ]] ||
  die "could not obtain an admin token — check KC_URL, KEYCLOAK_ADMIN and KEYCLOAK_ADMIN_PASSWORD"
echo "    ok."

# --- 3/6 realm + security policy ----------------------------------------------

# One representation, used for both the create and the update below, so a first
# run and a re-run end up with exactly the same realm settings.
realm_json=$(jq -nc \
  --arg realm "$REALM_NAME" \
  --arg policy "$PASSWORD_POLICY" \
  --argjson failureFactor "$BRUTE_FORCE_FAILURE_FACTOR" \
  --argjson waitIncrement "$BRUTE_FORCE_WAIT_INCREMENT" \
  --argjson maxWait "$BRUTE_FORCE_MAX_WAIT" \
  --argjson maxDelta "$BRUTE_FORCE_MAX_DELTA" \
  --argjson quickLoginMs "$BRUTE_FORCE_QUICK_LOGIN_MS" \
  --argjson quickLoginWait "$BRUTE_FORCE_QUICK_LOGIN_WAIT" \
  '{
     realm: $realm,
     enabled: true,
     passwordPolicy: $policy,
     bruteForceProtected: true,
     permanentLockout: false,
     failureFactor: $failureFactor,
     waitIncrementSeconds: $waitIncrement,
     maxFailureWaitSeconds: $maxWait,
     maxDeltaTimeSeconds: $maxDelta,
     quickLoginCheckMilliSeconds: $quickLoginMs,
     minimumQuickLoginWaitSeconds: $quickLoginWait
   }')

info "[3/6] creating realm '$REALM_NAME'"
post_json "realm '$REALM_NAME'" "$KC_URL/admin/realms" "$realm_json"

# The POST is a no-op once the realm exists, so push the same settings with a
# PUT as well: that is what makes the password policy and brute force detection
# land on a realm created by an earlier version of this script.
info "      applying password policy and brute force detection"
echo "    policy: $PASSWORD_POLICY"
put_json "security settings for '$REALM_NAME'" \
  "$KC_URL/admin/realms/$REALM_NAME" "$realm_json"

# --- 4/6 client --------------------------------------------------------------

# Public client with direct access grants: convenient for local development and
# for fetching tokens with curl. Do NOT ship these settings to production —
# tighten redirectUris/webOrigins and use a confidential client there.
info "[4/6] creating client '$CLIENT_NAME'"
post_json "client '$CLIENT_NAME'" "$KC_URL/admin/realms/$REALM_NAME/clients" \
  "$(jq -nc --arg id "$CLIENT_NAME" '{
        clientId: $id,
        enabled: true,
        publicClient: true,
        directAccessGrantsEnabled: true,
        standardFlowEnabled: true,
        redirectUris: ["*"],
        webOrigins: ["*"]
      }')"

# --- 5/6 realm roles ---------------------------------------------------------

info "[5/6] creating realm roles: ${ROLES[*]}"
for role in "${ROLES[@]}"; do
  post_json "role '$role'" "$KC_URL/admin/realms/$REALM_NAME/roles" \
    "$(jq -nc --arg name "$role" '{name: $name}')"
done

# --- 6/6 users ---------------------------------------------------------------

info "[6/6] creating users, setting passwords, assigning roles"
for entry in "${USERS[@]}"; do
  IFS=":" read -r username password role email first last <<<"$entry"

  profile=$(jq -nc \
    --arg u "$username" --arg e "$email" --arg f "$first" --arg l "$last" \
    '{username: $u, email: $e, firstName: $f, lastName: $l,
      enabled: true, emailVerified: true, requiredActions: []}')

  echo "  - $username"
  post_json "user '$username'" "$KC_URL/admin/realms/$REALM_NAME/users" \
    "$profile"

  user_id=$(curl -s --get "$KC_URL/admin/realms/$REALM_NAME/users" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    --data-urlencode "username=$username" \
    --data-urlencode 'exact=true' | jq -r '.[0].id // empty')

  [[ -n "$user_id" ]] || die "could not resolve the user id for '$username'"
  echo "    id: $user_id"

  # The POST above is skipped when the user already exists, which would leave an
  # older, possibly incomplete profile in place. Write it again so a re-run
  # converges on the configuration declared above either way.
  put_json "profile for '$username'" \
    "$KC_URL/admin/realms/$REALM_NAME/users/$user_id" "$profile"

  put_json "password for '$username'" \
    "$KC_URL/admin/realms/$REALM_NAME/users/$user_id/reset-password" \
    "$(jq -nc --arg p "$password" \
      '{type: "password", value: $p, temporary: false}')"

  # The realm role-mapping endpoint wants the full role representation, not just
  # a name, so fetch it first and make sure it really came back.
  role_json=$(curl -s "$KC_URL/admin/realms/$REALM_NAME/roles/$role" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
  [[ "$(jq -r '.id // empty' <<<"$role_json")" ]] ||
    die "realm role '$role' not found in realm '$REALM_NAME'"

  # Re-assigning an existing mapping is a no-op on the Keycloak side.
  post_json "role '$role' -> '$username'" \
    "$KC_URL/admin/realms/$REALM_NAME/users/$user_id/role-mappings/realm" \
    "[$role_json]"
done

# --- summary -----------------------------------------------------------------

# Derive the example from the first USERS entry so it cannot drift out of sync
# when the list above is edited.
IFS=":" read -r demo_user demo_pass _ <<<"${USERS[0]}"

cat <<EOF

============================================================
 Done. Realm '$REALM_NAME', client '$CLIENT_NAME',
 role(s) ${ROLES[*]}, and ${#USERS[@]} user(s) are provisioned.

 Password policy:  $PASSWORD_POLICY
 Brute force:      on, lockout after $BRUTE_FORCE_FAILURE_FACTOR failures
                   (${BRUTE_FORCE_WAIT_INCREMENT}s, doubling up to ${BRUTE_FORCE_MAX_WAIT}s)

 Admin console: $KC_URL/admin  ($ADMIN_USER / $ADMIN_PASS)

 Example: request an access token for user '$demo_user'

curl -X POST "$KC_URL/realms/$REALM_NAME/protocol/openid-connect/token" \\
  -H "Content-Type: application/x-www-form-urlencoded" \\
  -d "client_id=$CLIENT_NAME" \\
  -d "grant_type=password" \\
  -d "username=$demo_user" \\
  -d "password=$demo_pass"
============================================================
EOF