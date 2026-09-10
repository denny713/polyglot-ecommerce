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
#   KEYCLOAK_CLIENT           public client id          (ecommerce-app)
#   KEYCLOAK_CLIENT_NAME      its display name         (derived: "Ecommerce App")
#   KEYCLOAK_RESOURCE_SERVER_CLIENTS  comma-separated "clientId:Display Name"
#                             confidential clients, one per backend service,
#                             each with its own self-audience mapper and secret
#                             (see RESOURCE_SERVER_CLIENT_SPECS)
#   KEYCLOAK_ACCOUNT_MANAGER_CLIENTS  resource-server clients whose service
#                             account may manage users through the Admin REST
#                             API                       (auth-service)
#   KEYCLOAK_ACCOUNT_MANAGER_ROLES    the realm-management client roles they
#                             get      (manage-users,view-users,view-realm)
#   KEYCLOAK_APP_AUDIENCES    resource-server clients whose audience is added
#                             to tokens issued to the public client
#                                                       (auth-service)
#   KEYCLOAK_PASSWORD_POLICY  realm password policy     (see PASSWORD_POLICY)
#
# Note: bff-service and gateway-service are provisioned with the same "resource
# server only" template as the others (no browser login flow). If either one
# actually terminates an OAuth2 login (BFF-style) rather than just validating
# bearer tokens, give it standardFlowEnabled=true and real redirectUris
# instead — see the client loop below.
#
# Requires curl and jq on the host (macOS: brew install jq).

# Re-exec under a real bash. When this is invoked as `sh app/init/keycloak-init.sh`
# — or reached from a build.sh that was itself started that way — the shell is
# /bin/sh, which is bash in POSIX mode on macOS and dash on most Linux distros.
# Neither runs the arrays and [[ ]] below the way this script expects. Keep this
# block POSIX-clean: it is parsed by whatever shell started the script, before
# the `set -o pipefail` on the next line (which dash does not support).
if [ -z "${BASH_VERSION:-}" ] || [ -n "${POSIXLY_CORRECT:-}" ]; then
  unset POSIXLY_CORRECT
  exec bash "$0" "$@"
fi

set -euo pipefail

# --- configuration -----------------------------------------------------------

KC_URL="${KC_URL:-http://localhost:${KEYCLOAK_PORT:-8080}}"
ADMIN_REALM="${KEYCLOAK_ADMIN_REALM:-master}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-P@ssw0rd}"

REALM_NAME="${KEYCLOAK_REALM:-ecommerce}"
# clientId of the public client — the "Client ID" column in the admin console.
# Its human-readable "Name" column is derived from this below.
CLIENT_NAME="${KEYCLOAK_CLIENT:-ecommerce-app}"

# Confidential, resource-server-only clients — one per backend service. Each
# one gets its own client (never shared) so a token minted for order-service
# is never accepted by recommendation-service: least-privilege / one audience
# per service, same idea as the password policy and brute force settings
# below applied to authentication.
#
# format: "clientId:Display Name", comma-separated. The display name is what
# the admin console lists under "Name"; drop the ":Display Name" part and it is
# derived from the clientId instead (see display_name below). Names therefore
# must not contain a comma or a colon.
#
# Override the whole list with KEYCLOAK_RESOURCE_SERVER_CLIENTS, e.g.:
#   KEYCLOAK_RESOURCE_SERVER_CLIENTS="auth-service:Authentication Service,order-service:Order Service"
IFS=',' read -r -a RESOURCE_SERVER_CLIENT_SPECS \
  <<<"${KEYCLOAK_RESOURCE_SERVER_CLIENTS:-auth-service:Authentication Service,order-service:Order Service,product-service:Product Service,recommendation-service:Recommendation Service,bff-service:Backend for Frontend Service,gateway-service:API Gateway Service}"

# Resource-server clients whose *service account* is allowed to create, update
# and delete users through the Keycloak Admin REST API.
#
# auth-service needs this because POST /api/account/register and friends are a
# thin front for /admin/realms/{realm}/users, and that endpoint answers 403 to
# a service account without the realm-management roles below. Nothing else on
# the platform touches user records, so nothing else is on this list —
# manage-users is close to realm-admin in blast radius.
IFS=',' read -r -a ACCOUNT_MANAGER_CLIENTS \
  <<<"${KEYCLOAK_ACCOUNT_MANAGER_CLIENTS:-auth-service}"

# view-users comes along with manage-users because auth-service reads the
# account back after creating it, to return the id Keycloak assigned.
#
# view-realm is there for one read only: registration grants the new account the
# "user" realm role, and mapping a role needs the role's *id*, which means a
# GET /admin/realms/{realm}/roles/user first. That read is refused with 403 for a
# service account holding only the two roles above. It grants no write of any
# kind — realm configuration is still read-only to auth-service.
IFS=',' read -r -a ACCOUNT_MANAGER_ROLES \
  <<<"${KEYCLOAK_ACCOUNT_MANAGER_ROLES:-manage-users,view-users,view-realm}"

# Resource-server clients whose audience is added to the tokens the *public*
# client issues.
#
# The self-audience mapper in the loop below only puts "aud": "auth-service" on
# tokens minted for the auth-service client itself. A customer logs in through
# ecommerce-app, so without a mapper here their token carries no such audience
# and auth-service — which validates quarkus.oidc.token.audience — rejects it
# with 401 on every /api/account/me endpoint. Add a service to this
# list when the browser app starts calling it directly with a user token.
IFS=',' read -r -a APP_AUDIENCES <<<"${KEYCLOAK_APP_AUDIENCES:-auth-service}"

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
  "adminapp:P@ssw0rd:admin:adminapp@mail.com:Administrator:App"
  "userapp:P@ssw0rd:user:userapp@mail.com:User:App"
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

# Turn a clientId into the human-readable label Keycloak shows in the "Name"
# column of the clients list: "auth-service" -> "Auth Service". Without an
# explicit `name` on the client representation that column stays empty (shown
# as "—" in the console), unlike Keycloak's own built-in clients.
# The '_-' set is deliberately in that order: BSD tr on macOS reads a leading
# '-' as the start of an option and dies with "illegal option -- _".
display_name() {
  printf '%s' "$1" | tr '_-' '  ' |
    awk '{for (i = 1; i <= NF; i++)
            $i = toupper(substr($i, 1, 1)) tolower(substr($i, 2));
          print}'
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

# True when $1 appears in the array whose name is $2. Written with `eval` and a
# positional copy rather than a nameref, because bash 3.2 — the version macOS
# ships, and the one the check below insists on — has neither namerefs nor
# associative arrays.
contains() {
  local needle="$1" array_name="$2" item
  eval "local items=(\"\${${array_name}[@]}\")"
  for item in "${items[@]}"; do
    [[ "$item" == "$needle" ]] && return 0
  done
  return 1
}

# Give a client's service account the realm-management roles it needs to manage
# users. The service account is an ordinary (hidden) user, so this is the same
# role-mapping call as for a real user — only against the realm-management
# client's roles instead of the realm's own.
grant_realm_management_roles() {
  local svc="$1" internal_id="$2" sa_user_id rm_id role role_json

  sa_user_id=$(curl -s \
    "$KC_URL/admin/realms/$REALM_NAME/clients/$internal_id/service-account-user" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.id // empty')
  [[ -n "$sa_user_id" ]] ||
    die "could not resolve the service account user for '$svc' — is serviceAccountsEnabled set?"

  rm_id=$(curl -s --get "$KC_URL/admin/realms/$REALM_NAME/clients" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    --data-urlencode 'clientId=realm-management' | jq -r '.[0].id // empty')
  [[ -n "$rm_id" ]] ||
    die "could not resolve the 'realm-management' client in realm '$REALM_NAME'"

  for role in "${ACCOUNT_MANAGER_ROLES[@]}"; do
    role_json=$(curl -s "$KC_URL/admin/realms/$REALM_NAME/clients/$rm_id/roles/$role" \
      -H "Authorization: Bearer $ADMIN_TOKEN")
    [[ "$(jq -r '.id // empty' <<<"$role_json")" ]] ||
      die "client role 'realm-management:$role' not found in realm '$REALM_NAME'"

    # Re-assigning an existing mapping is a no-op on the Keycloak side.
    post_json "realm-management role '$role' -> service account of '$svc'" \
      "$KC_URL/admin/realms/$REALM_NAME/users/$sa_user_id/role-mappings/clients/$rm_id" \
      "[$role_json]"
  done
}

# --- preflight ---------------------------------------------------------------

command -v curl >/dev/null 2>&1 || die "curl is not installed or not on PATH"
command -v jq >/dev/null 2>&1 || die "jq is not installed or not on PATH"

# The secrets collected below are kept in a plain indexed array, not an
# associative one, precisely so this runs on the bash 3.2 that macOS still
# ships as /bin/bash — no `brew install bash` needed.
((BASH_VERSINFO[0] > 3 || (BASH_VERSINFO[0] == 3 && BASH_VERSINFO[1] >= 2))) ||
  die "bash >= 3.2 is required — current: ${BASH_VERSION}"

# --- 1/6 wait for keycloak ---------------------------------------------------

# The container healthcheck lives on port 9000, which compose does not publish,
# so poll the realm discovery document on the public port instead.
info "[1/7] waiting for Keycloak at $KC_URL (up to ${READY_TIMEOUT}s)"
deadline=$((SECONDS + READY_TIMEOUT))
until curl -sf -o /dev/null \
  "$KC_URL/realms/$ADMIN_REALM/.well-known/openid-configuration"; do
  ((SECONDS < deadline)) ||
    die "Keycloak did not become reachable at $KC_URL within ${READY_TIMEOUT}s"
  sleep 3
done
echo "    reachable."

# --- 2/6 admin token --------------------------------------------------------

info "[2/7] requesting admin token from realm '$ADMIN_REALM'"
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

info "[3/7] creating realm '$REALM_NAME'"
post_json "realm '$REALM_NAME'" "$KC_URL/admin/realms" "$realm_json"

# The POST is a no-op once the realm exists, so push the same settings with a
# PUT as well: that is what makes the password policy and brute force detection
# land on a realm created by an earlier version of this script.
info "      applying password policy and brute force detection"
echo "    policy: $PASSWORD_POLICY"
put_json "security settings for '$REALM_NAME'" \
  "$KC_URL/admin/realms/$REALM_NAME" "$realm_json"

# --- 4/7 client (ecommerce-app) -----------------------------------------------

# Public client with direct access grants: convenient for local development and
# for fetching tokens with curl. Do NOT ship these settings to production —
# tighten redirectUris/webOrigins and use a confidential client there.
#
# `name` is what the admin console lists under "Name"; override it with
# KEYCLOAK_CLIENT_NAME if the derived label is not what you want.
CLIENT_DISPLAY_NAME="${KEYCLOAK_CLIENT_NAME:-$(display_name "$CLIENT_NAME")}"

info "[4/7] creating client '$CLIENT_NAME' (name: '$CLIENT_DISPLAY_NAME')"

app_client_json=$(jq -nc \
  --arg id "$CLIENT_NAME" \
  --arg name "$CLIENT_DISPLAY_NAME" \
  '{
     clientId: $id,
     name: $name,
     enabled: true,
     publicClient: true,
     directAccessGrantsEnabled: true,
     standardFlowEnabled: true,
     redirectUris: ["*"],
     webOrigins: ["*"]
   }')

post_json "client '$CLIENT_NAME'" "$KC_URL/admin/realms/$REALM_NAME/clients" \
  "$app_client_json"

# Same reason as the realm PUT above: the POST is a no-op once the client
# exists, so a client created before `name` was set here would keep an empty
# Name column forever. Push the representation again to converge.
app_internal_id=$(curl -s --get "$KC_URL/admin/realms/$REALM_NAME/clients" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  --data-urlencode "clientId=$CLIENT_NAME" | jq -r '.[0].id // empty')

[[ -n "$app_internal_id" ]] ||
  die "could not resolve the internal id for client '$CLIENT_NAME'"

put_json "settings for client '$CLIENT_NAME'" \
  "$KC_URL/admin/realms/$REALM_NAME/clients/$app_internal_id" "$app_client_json"

# --- 5/7 confidential resource-server clients ---------------------------------

# One confidential client per backend service:
#   - publicClient=false          each has its own secret,
#                                  quarkus.oidc.credentials.secret /
#                                  spring.security.oauth2.resourceserver...
#   - standardFlowEnabled=false   none of these run a browser redirect login
#   - directAccessGrantsEnabled=false  none accept a password grant themselves
#   - serviceAccountsEnabled=true ready for client_credentials calls later,
#                                  e.g. bff-service -> order-service
#
# Adjust the loop body below per-client if one of them needs to differ (see
# the bff-service note in the header comment).

# Split the "clientId:Display Name" specs into two positional arrays —
# RESOURCE_SERVER_NAMES[i] belongs to RESOURCE_SERVER_CLIENTS[i]. Indexed rather
# than associative arrays, again so this runs on the bash 3.2 macOS ships.
RESOURCE_SERVER_CLIENTS=()
RESOURCE_SERVER_NAMES=()

for spec in "${RESOURCE_SERVER_CLIENT_SPECS[@]}"; do
  svc="${spec%%:*}"
  svc_display_name="${spec#*:}"

  [[ -n "$svc" ]] ||
    die "empty clientId in resource-server client spec '$spec'"

  # No ":Display Name" part, or an empty one: derive the label from the id.
  if [[ "$svc_display_name" == "$spec" || -z "$svc_display_name" ]]; then
    svc_display_name="$(display_name "$svc")"
  fi

  RESOURCE_SERVER_CLIENTS+=("$svc")
  RESOURCE_SERVER_NAMES+=("$svc_display_name")
done

info "[5/7] creating resource-server clients: ${RESOURCE_SERVER_CLIENTS[*]}"

# Secrets are collected positionally too: one append per loop iteration below,
# so CLIENT_SECRETS[i] also belongs to RESOURCE_SERVER_CLIENTS[i].
CLIENT_SECRETS=()

for i in "${!RESOURCE_SERVER_CLIENTS[@]}"; do
  svc="${RESOURCE_SERVER_CLIENTS[$i]}"
  svc_display_name="${RESOURCE_SERVER_NAMES[$i]}"
  echo "  - $svc (name: '$svc_display_name')"

  svc_client_json=$(jq -nc \
    --arg id "$svc" \
    --arg name "$svc_display_name" \
    '{
       clientId: $id,
       name: $name,
       enabled: true,
       publicClient: false,
       standardFlowEnabled: false,
       directAccessGrantsEnabled: false,
       serviceAccountsEnabled: true,
       redirectUris: [],
       webOrigins: []
     }')

  post_json "client '$svc'" "$KC_URL/admin/realms/$REALM_NAME/clients" \
    "$svc_client_json"

  # The client-scoped endpoints below (protocol-mappers, client-secret) are
  # keyed by the client's internal UUID, not its clientId, so resolve that
  # first.
  internal_id=$(curl -s --get "$KC_URL/admin/realms/$REALM_NAME/clients" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    --data-urlencode "clientId=$svc" | jq -r '.[0].id // empty')

  [[ -n "$internal_id" ]] || die "could not resolve the internal id for client '$svc'"

  # Converge an already-existing client on the representation above, so a realm
  # provisioned by an earlier run picks up the Name (and any other change here).
  put_json "settings for client '$svc'" \
    "$KC_URL/admin/realms/$REALM_NAME/clients/$internal_id" "$svc_client_json"

  # Without this mapper, Keycloak never puts an "aud" claim for this client on
  # the access token, and audience validation on the receiving service
  # rejects every otherwise-valid token with a 401. This is the single most
  # common gotcha wiring a Quarkus/Spring resource server to Keycloak.
  post_json "audience mapper for '$svc'" \
    "$KC_URL/admin/realms/$REALM_NAME/clients/$internal_id/protocol-mappers/models" \
    "$(jq -nc --arg id "$svc" '{
          name: "audience-self",
          protocol: "openid-connect",
          protocolMapper: "oidc-audience-mapper",
          consentRequired: false,
          config: {
            "included.client.audience": $id,
            "id.token.claim": "false",
            "access.token.claim": "true"
          }
        }')"

  # The mapper above covers tokens minted *for* this client. A user's token is
  # minted for the public client instead, so services the browser app calls
  # directly need their audience added there too, or audience validation on the
  # receiving side rejects every user token.
  if contains "$svc" APP_AUDIENCES; then
    post_json "audience mapper for '$svc' on '$CLIENT_NAME'" \
      "$KC_URL/admin/realms/$REALM_NAME/clients/$app_internal_id/protocol-mappers/models" \
      "$(jq -nc --arg id "$svc" '{
            name: ("audience-" + $id),
            protocol: "openid-connect",
            protocolMapper: "oidc-audience-mapper",
            consentRequired: false,
            config: {
              "included.client.audience": $id,
              "id.token.claim": "false",
              "access.token.claim": "true"
            }
          }')"
  fi

  # Let this service's own service account manage user records, if it is one of
  # the services that needs to.
  if contains "$svc" ACCOUNT_MANAGER_CLIENTS; then
    grant_realm_management_roles "$svc" "$internal_id"
  fi

  # Read back the generated secret so it can be printed in the summary below —
  # saves a trip to the admin console for each service.
  secret=$(curl -s "$KC_URL/admin/realms/$REALM_NAME/clients/$internal_id/client-secret" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.value // empty')
  [[ -n "$secret" ]] || die "could not read the client secret for '$svc'"

  CLIENT_SECRETS+=("$secret")
done

# --- 6/7 realm roles ---------------------------------------------------------

info "[6/7] creating realm roles: ${ROLES[*]}"
for role in "${ROLES[@]}"; do
  post_json "role '$role'" "$KC_URL/admin/realms/$REALM_NAME/roles" \
    "$(jq -nc --arg name "$role" '{name: $name}')"
done

# --- 7/7 users ---------------------------------------------------------------

info "[7/7] creating users, setting passwords, assigning roles"
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

# Build the per-client secret listing once, outside the heredoc, since a
# heredoc can't loop over CLIENT_SECRETS itself. The trailing $'\n' is
# deliberate: command substitution strips it from each printf, so without
# appending it back explicitly all the lines would end up glued together.
resource_clients_summary=""
for i in "${!RESOURCE_SERVER_CLIENTS[@]}"; do
  resource_clients_summary+="$(printf '   %-24s %-32s %s' \
    "${RESOURCE_SERVER_CLIENTS[$i]}" \
    "${RESOURCE_SERVER_NAMES[$i]}" \
    "${CLIENT_SECRETS[$i]}")"$'\n'
done

cat <<EOF

============================================================
 Done. Realm '$REALM_NAME', public client '$CLIENT_NAME',
 resource-server clients ${RESOURCE_SERVER_CLIENTS[*]},
 role(s) ${ROLES[*]}, and ${#USERS[@]} user(s) are provisioned.

 Password policy:  $PASSWORD_POLICY
 Brute force:      on, lockout after $BRUTE_FORCE_FAILURE_FACTOR failures
                   (${BRUTE_FORCE_WAIT_INCREMENT}s, doubling up to ${BRUTE_FORCE_MAX_WAIT}s)
 User management:  service account of ${ACCOUNT_MANAGER_CLIENTS[*]} holds
                   realm-management ${ACCOUNT_MANAGER_ROLES[*]}
 App audiences:    tokens from '$CLIENT_NAME' carry aud ${APP_AUDIENCES[*]}

 Admin console: $KC_URL/admin  ($ADMIN_USER / $ADMIN_PASS)

 Example: request an access token for user '$demo_user'

curl -X POST "$KC_URL/realms/$REALM_NAME/protocol/openid-connect/token" \\
  -H "Content-Type: application/x-www-form-urlencoded" \\
  -d "client_id=$CLIENT_NAME" \\
  -d "grant_type=password" \\
  -d "username=$demo_user" \\
  -d "password=$demo_pass"

 Resource-server clients — client id, name, secret (keep the
 secrets out of version control: .env or a secret store,
 one per service):

$resource_clients_summary
 Drop this into each service's config, swapping CLIENT_ID + SECRET
 for the values above (Quarkus shown; Spring Boot needs the matching
 issuer-uri + audience validator instead):

   %dev.quarkus.keycloak.devservices.enabled=false
   quarkus.oidc.auth-server-url=$KC_URL/realms/$REALM_NAME
   quarkus.oidc.client-id=<CLIENT_ID>
   quarkus.oidc.credentials.secret=<SECRET>
   quarkus.oidc.application-type=service
   quarkus.oidc.token.audience=<CLIENT_ID>

 auth-service additionally calls the Admin REST API with its own
 service account, so it needs the same secret a second time under
 services/auth-service/.env:

   KEYCLOAK_ADMIN_API_CLIENT_ID=auth-service
   KEYCLOAK_ADMIN_API_CLIENT_SECRET=<SECRET for auth-service above>
============================================================
EOF