#!/usr/bin/env bash
#
# Creates the confidential client whose service account platform-admin-backend uses to create,
# enable, disable and delete tenant realms.
#
# WHY THIS IS A SCRIPT AND NOT A REALM IMPORT
#
# The client has to live in the `master` realm: `create-realm` is a realm role of `master`, not a
# realm-scoped permission, so a client in any other realm cannot create a realm however it is
# configured. And `master` already exists by the time Keycloak reads /opt/keycloak/data/import —
# `--import-realm` skips a realm that is present rather than merging into it, so there is no import
# file that can add a client to it. The Admin CLI is the only declarative route left.
#
# Idempotent: every step checks first, so re-running after a partial failure completes the rest.
# Safe to run on every container start, which is how the compose service invokes it.
set -euo pipefail

KC_URL="${KC_URL:-http://keycloak:8080}"
KC_ADMIN="${KC_ADMIN:-admin}"
KC_ADMIN_PASSWORD="${KC_ADMIN_PASSWORD:-admin_password}"
CLIENT_ID="${PLATFORM_ADMIN_CLIENT_ID:-platform-admin-service}"
CLIENT_SECRET="${PLATFORM_ADMIN_CLIENT_SECRET:?PLATFORM_ADMIN_CLIENT_SECRET must be set}"

# The browser-facing URLs of the two frontends that authenticate against the `processpuzzle-admin`
# realm. They are environment-specific, and this is the ONLY place a deployment declares them.
#
# WHY NOT THE REALM IMPORT: `--import-realm` skips a realm that already exists rather than merging
# into it, so tools/docker/keycloak/import/processpuzzle-admin-realm.json only ever describes a
# realm on an EMPTY database. Every deployment past the first keeps whatever the first import
# wrote — which is why stage ran with `http://localhost:9091` as its only allowed origin long
# after it was reachable at admin.stage.processpuzzle.de, and why editing that JSON would not
# have fixed it. These are reconciled on every start instead.
#
# WHAT GOES WRONG WHEN AN ORIGIN IS MISSING, in the order you meet it:
#   1. GET /realms/<realm>/protocol/openid-connect/login-status-iframe.html/init?...&origin=<origin>
#      answers 403, keycloak-js rejects init with "Error while checking login iframe", and the app
#      initializer logs "Authentication initialization failed". The app boots unauthenticated.
#   2. The login redirect is refused with "Invalid parameter: redirect_uri".
#   3. The token request is blocked by CORS — `webOrigins=["+"]` below means "derive the allowed
#      origins from redirectUris", so this list is the CORS allowlist too.
# (1) fails first and hides the other two, so fixing only the iframe does not produce a login.
#
# COMMA-SEPARATED, not JSON, because Coolify supplies these through a web form and its parser is
# not to be trusted with embedded quotes — the same reason `${VAR:?msg}` is unusable in the
# compose files. to_json_array below builds what kcadm actually wants.
#
# Defaults are the local compose ports, so `npm run stack-up-admin-build` still needs no
# environment. The `/*` suffix is required: it has to cover the silent SSO probe at
# <root>/assets/auth/silent-check-sso.html that KeycloakAuthService configures.
ADMIN_CLIENT_ROOT_URL="${ADMIN_CLIENT_ROOT_URL:-http://localhost:9091}"
ADMIN_CLIENT_REDIRECT_URIS="${ADMIN_CLIENT_REDIRECT_URIS:-http://localhost:9091/*,http://localhost:4201/*}"
BIZ_CLIENT_ROOT_URL="${BIZ_CLIENT_ROOT_URL:-http://localhost:9092}"
BIZ_CLIENT_REDIRECT_URIS="${BIZ_CLIENT_REDIRECT_URIS:-http://localhost:9092/*,http://localhost:4202/*}"
CUSTOM_CLIENT_ROOT_URL="${CUSTOM_CLIENT_ROOT_URL:-http://localhost:9093}"
CUSTOM_CLIENT_REDIRECT_URIS="${CUSTOM_CLIENT_REDIRECT_URIS:-http://localhost:9093/*,http://localhost:4203/*}"
# This remains Mailpit by default so a local/CI sign-up can never mail a real customer. Stage and
# production override the values through the infrastructure resource and this script reconciles
# them on every start because realm imports are deliberately one-shot.
CUSTOM_SMTP_HOST="${KEYCLOAK_CUSTOM_SMTP_HOST:-mailpit}"
CUSTOM_SMTP_PORT="${KEYCLOAK_CUSTOM_SMTP_PORT:-1025}"
CUSTOM_SMTP_FROM="${KEYCLOAK_CUSTOM_SMTP_FROM:-noreply@processpuzzle.com}"
CUSTOM_SMTP_FROM_DISPLAY_NAME="${KEYCLOAK_CUSTOM_SMTP_FROM_DISPLAY_NAME:-ProcessPuzzle}"
CUSTOM_SMTP_REPLY_TO="${KEYCLOAK_CUSTOM_SMTP_REPLY_TO:-support@processpuzzle.com}"
CUSTOM_SMTP_AUTH="${KEYCLOAK_CUSTOM_SMTP_AUTH:-false}"
CUSTOM_SMTP_USERNAME="${KEYCLOAK_CUSTOM_SMTP_USERNAME:-}"
CUSTOM_SMTP_PASSWORD="${KEYCLOAK_CUSTOM_SMTP_PASSWORD:-}"
CUSTOM_SMTP_STARTTLS="${KEYCLOAK_CUSTOM_SMTP_STARTTLS:-false}"
CUSTOM_SMTP_SSL="${KEYCLOAK_CUSTOM_SMTP_SSL:-false}"
# Overridable so the argument construction below can be exercised against a stub; a container
# never sets it.
KCADM="${KCADM:-/opt/keycloak/bin/kcadm.sh}"

# kcadm stores an access token, and Keycloak's default lifespan for it is a minute. This script
# makes enough calls — and waits on enough of them — to outlive one, so the login is a function and
# is re-issued before each step rather than performed once at the top. Symptom when it is not:
# "Session has expired. Login again", mid-run, on whichever step happened to be slowest.
login() {
  "$KCADM" config credentials --server "$KC_URL" --realm master --user "$KC_ADMIN" --password "$KC_ADMIN_PASSWORD" >/dev/null 2>&1
}

# BOUNDED, because this loop is now the only thing waiting for Keycloak: the compose service
# depends on it with `condition: service_started` rather than `service_healthy`, so a Keycloak that
# never comes up would otherwise leave this container spinning silently forever.
KC_WAIT_TIMEOUT="${KC_WAIT_TIMEOUT:-600}"
deadline=$(( $(date +%s) + KC_WAIT_TIMEOUT ))

echo "Waiting up to ${KC_WAIT_TIMEOUT}s for Keycloak at ${KC_URL} ..."
until login; do
  if [ "$(date +%s)" -ge "${deadline}" ]; then
    echo "ERROR: Keycloak at ${KC_URL} did not accept an admin login for ${KC_ADMIN} within ${KC_WAIT_TIMEOUT}s." >&2
    echo "       Read the keycloak container's log: this script cannot tell a slow start from a bad" >&2
    echo "       KC_DB_PASSWORD, a wrong KC_ADMIN_PASSWORD or a realm import failure." >&2
    exit 1
  fi
  sleep 2
done
echo "Authenticated against ${KC_URL} as ${KC_ADMIN}."

# Turns "https://a/*,https://b/*" into ["https://a/*","https://b/*"], which is the form kcadm
# needs for a list-valued field. Blank entries and stray whitespace are dropped, so a trailing
# comma or a space after one — both easy to leave in a web form — do not produce an empty
# redirect URI, which Keycloak accepts and which then matches nothing.
to_json_array() {
  local csv="$1" item out="" glob_was_already_off=1
  # Pathname expansion OFF around the split. The split has to be unquoted to break on IFS, and
  # every entry ends in `/*` — with globbing live, bash would be free to rewrite an entry into
  # matching filenames if the init container ever ran somewhere such a path existed. It does not
  # today, which is exactly what would make the failure baffling later.
  case "$-" in
    *f*) ;;
    *) glob_was_already_off=0; set -f ;;
  esac
  local IFS=","
  for item in $csv; do
    item="$(printf '%s' "$item" | tr -d '[:space:]')"
    if [ -n "${item}" ]; then
      out="${out:+${out},}\"${item}\""
    fi
  done
  if [ "${glob_was_already_off}" -eq 0 ]; then set +f; fi
  printf '[%s]' "${out}"
}

frame_ancestors_from_redirect_uris() {
  local csv="$1" item origin out="" glob_was_already_off=1
  case "$-" in
    *f*) ;;
    *) glob_was_already_off=0; set -f ;;
  esac
  local IFS=","
  for item in $csv; do
    item="$(printf '%s' "$item" | tr -d '[:space:]')"
    if [ -z "${item}" ]; then
      continue
    fi
    if [[ ! "${item}" =~ ^https?://[A-Za-z0-9.-]+(:[0-9]+)?/\*$ ]]; then
      echo "ERROR: Customer realm client redirect URI '${item}' must be an HTTP(S) origin followed by /*." >&2
      exit 1
    fi
    origin="${item%/*}"
    out="${out:+${out} }${origin}"
  done
  if [ "${glob_was_already_off}" -eq 0 ]; then set +f; fi
  if [ -z "${out}" ]; then
    echo "ERROR: At least one Custom client redirect URI is required to set frame ancestors." >&2
    exit 1
  fi
  printf '%s' "${out}"
}

ensure_public_client() {
  local realm="$1"
  local client_id="$2"
  local client_name="$3"
  local root_url="$4"
  # Accepts the comma-separated form the environment supplies; kcadm needs a JSON array.
  local redirect_uris
  redirect_uris="$(to_json_array "$5")"
  local client_uuid

  login
  client_uuid="$("$KCADM" get clients -r "${realm}" --query "clientId=${client_id}" --fields id --format csv --noquotes 2>/dev/null | head -1 || true)"

  if [ -z "${client_uuid}" ]; then
    echo "Creating public client '${client_id}' in '${realm}' ..."
    "$KCADM" create clients -r "${realm}" \
      -s "clientId=${client_id}" \
      -s "name=${client_name}" \
      -s 'enabled=true' \
      -s 'publicClient=true' \
      -s 'standardFlowEnabled=true' \
      -s 'directAccessGrantsEnabled=false' \
      -s 'implicitFlowEnabled=false' \
      -s 'serviceAccountsEnabled=false' \
      -s "rootUrl=${root_url}" \
      -s 'baseUrl=/' \
      -s "redirectUris=${redirect_uris}" \
      -s 'webOrigins=["+"]' \
      -s 'attributes={"post.logout.redirect.uris":"+","pkce.code.challenge.method":"S256"}' \
      -s 'protocol=openid-connect' \
      -s 'fullScopeAllowed=true'
  else
    echo "Reconciling public client '${client_id}' in '${realm}' ..."
    "$KCADM" update "clients/${client_uuid}" -r "${realm}" \
      -s "name=${client_name}" \
      -s 'enabled=true' \
      -s 'publicClient=true' \
      -s 'standardFlowEnabled=true' \
      -s 'directAccessGrantsEnabled=false' \
      -s 'implicitFlowEnabled=false' \
      -s 'serviceAccountsEnabled=false' \
      -s "rootUrl=${root_url}" \
      -s 'baseUrl=/' \
      -s "redirectUris=${redirect_uris}" \
      -s 'webOrigins=["+"]' \
      -s 'attributes={"post.logout.redirect.uris":"+","pkce.code.challenge.method":"S256"}' \
      -s 'protocol=openid-connect' \
      -s 'fullScopeAllowed=true'
  fi
}

# Makes a client scope one of the client's DEFAULT scopes, so its claims are in every token rather
# than only when the client asks for them. A client created by `ensure_public_client` gets the
# realm's default scopes, and Keycloak attaches `organization` as an OPTIONAL one.
ensure_default_client_scope() {
  local realm="$1" client_id="$2" scope_name="$3" client_uuid scope_uuid

  login
  client_uuid="$("$KCADM" get clients -r "${realm}" --query "clientId=${client_id}" --fields id --format csv --noquotes | head -1)"
  # grep and cut rather than awk: the Keycloak image this runs in ships no awk.
  scope_uuid="$("$KCADM" get client-scopes -r "${realm}" --fields id,name --format csv --noquotes | grep ",${scope_name}\$" | cut -d, -f1 | head -1 || true)"
  if [ -z "${scope_uuid}" ]; then
    echo "  WARNING: realm '${realm}' has no client scope '${scope_name}'; '${client_id}' tokens will not carry it."
    return 0
  fi
  echo "Ensuring '${scope_name}' is a default scope of '${client_id}' in '${realm}' ..."
  # Out of the OPTIONAL list first: while the scope is optional, the PUT below answers 204 and
  # changes nothing. The DELETE fails harmlessly when it is not optional; the PUT is a no-op when it
  # is already a default. Idempotent both ways.
  "$KCADM" delete "clients/${client_uuid}/optional-client-scopes/${scope_uuid}" -r "${realm}" >/dev/null 2>&1 || true
  "$KCADM" update "clients/${client_uuid}/default-client-scopes/${scope_uuid}" -r "${realm}" -n -b '{}'
}

# Adds a protocol mapper to a client scope unless one of that TYPE is already there. Keyed on the
# mapper type rather than its name, so a mapper someone added by hand under another name is not
# duplicated; its configuration is then left as they set it.
ensure_scope_mapper() {
  local realm="$1" scope_name="$2" mapper_name="$3" mapper_type="$4" mapper_config="$5" scope_uuid

  login
  scope_uuid="$("$KCADM" get client-scopes -r "${realm}" --fields id,name --format csv --noquotes | grep ",${scope_name}\$" | cut -d, -f1 | head -1 || true)"
  if [ -z "${scope_uuid}" ]; then
    echo "  WARNING: realm '${realm}' has no client scope '${scope_name}'; cannot add '${mapper_name}'."
    return 0
  fi
  if "$KCADM" get "client-scopes/${scope_uuid}/protocol-mappers/models" -r "${realm}" --fields protocolMapper --format csv --noquotes | grep -qx "${mapper_type}"; then
    echo "Scope '${scope_name}' in '${realm}' already has a ${mapper_type}."
    return 0
  fi
  echo "Adding '${mapper_name}' to scope '${scope_name}' in '${realm}' ..."
  "$KCADM" create "client-scopes/${scope_uuid}/protocol-mappers/models" -r "${realm}" \
    -b "{\"name\":\"${mapper_name}\",\"protocol\":\"openid-connect\",\"protocolMapper\":\"${mapper_type}\",\"config\":${mapper_config}}"
}

# Realm imports do not merge into existing realms, so both browser-facing clients of the
# `processpuzzle-admin` realm are reconciled here on every container start instead. That is what
# lets an origin be added to a realm that was imported months ago.
#
# The admin client was previously NOT reconciled — it was described only by the realm import — so
# its redirect URIs were frozen at the localhost pair the first import wrote, and no redeploy
# could widen them. That is the defect this call fixes; the Biz one below was already reconciled,
# but against a hardcoded http://localhost:9092, so every deploy actively re-broke any origin
# added by hand in the admin console.
ensure_public_client \
  processpuzzle-admin \
  processpuzzle-admin \
  'ProcessPuzzle Admin' \
  "${ADMIN_CLIENT_ROOT_URL}" \
  "${ADMIN_CLIENT_REDIRECT_URIS}"

# The Biz frontend's fallback client, so it can initialize on its own root URL before a tenant —
# and therefore a tenant realm — has been selected.
ensure_public_client \
  processpuzzle-admin \
  processpuzzle-biz \
  'ProcessPuzzle Biz' \
  "${BIZ_CLIENT_ROOT_URL}" \
  "${BIZ_CLIENT_REDIRECT_URIS}"

# The customer application's client, in the realm every customer shares.
#
# Reconciled here for the reason the two above are: a realm import is skipped for a realm that
# already exists, so on any Keycloak that has run before, the clients described in
# processpuzzle-custom-realm.json were never created.
#
# These redirect URIs are the customer application's own login, and have to cover
# platform-subscription.application-url-template in processpuzzle-biz-backend -- the link Customer
# Home offers. The activation mail names the `processpuzzle-biz` client below instead.
ensure_public_client \
  processpuzzle-custom \
  processpuzzle-custom \
  'ProcessPuzzle Custom' \
  "${CUSTOM_CLIENT_ROOT_URL}" \
  "${CUSTOM_CLIENT_REDIRECT_URIS}"

# The Biz frontend's client in the customers' realm: Customer Home logs the subscriber in here, with
# the account the activation mail set a password for. Same origins as its `processpuzzle-admin` twin
# above.
#
# NOT optional. platform-admin's sendActivationEmail passes `client_id=processpuzzle-biz` to
# execute-actions-email, and Keycloak refuses an unknown client -- so without it a customer's seed
# job fails at PROVISIONING_IDENTITY on a Keycloak that looks perfectly healthy, and the customer is
# never told their account exists. The redirect to Customer Home is validated against these URIs,
# and one that does not match leaves the customer on Keycloak's own "account updated" page.
#
# One realm for Customer Home and the customer application is what makes the link between them
# single sign-on: Keycloak's session cookie is per realm, so the second application finds the
# session the first one started.
ensure_public_client \
  processpuzzle-custom \
  processpuzzle-biz \
  'ProcessPuzzle Biz' \
  "${BIZ_CLIENT_ROOT_URL}" \
  "${BIZ_CLIENT_REDIRECT_URIS}"
# Customer Home's API authorizes on the token's `organization` claim; without the scope there is none.
ensure_default_client_scope processpuzzle-custom processpuzzle-biz organization

# A customer's workflow roles are Keycloak Organization GROUPS — one hierarchy per organization, so
# two customers' `reviewer` roles are two different groups (Keycloak 26.6+). This mapper nests them
# into the claim the backends already read, under the organization they belong to:
#
#   "organization": { "acme": { "groups": ["/reviewer"] } }
#
# It changes the claim from a list of aliases to an object keyed by alias, which the custom backend's
# OrganizationClaim reads either way. Reconciled here rather than in the realm import: that file does
# not describe client scopes (Keycloak creates `organization` itself), and an import is skipped for a
# realm that already exists anyway.
ensure_scope_mapper processpuzzle-custom organization 'organization groups' \
  oidc-organization-group-membership-mapper \
  '{"id.token.claim":"true","access.token.claim":"true","userinfo.token.claim":"true","introspection.token.claim":"true"}'

# Keycloak applies frame-ancestors per realm. Keeping it in lockstep with the redirect origins of
# both clients in the realm lets each environment frame Keycloak's login-status and third-party-cookie
# probes, while still preventing unlisted sites from embedding the realm. Biz's origins are in the
# list because Customer Home logs in here too: without them its silent SSO check is refused and
# keycloak-js init times out, so the login-guarded portal never renders.
custom_frame_ancestors="$(frame_ancestors_from_redirect_uris "${CUSTOM_CLIENT_REDIRECT_URIS},${BIZ_CLIENT_REDIRECT_URIS}")"
custom_content_security_policy="frame-src 'self'; frame-ancestors 'self' ${custom_frame_ancestors}; object-src 'none';"
echo "Reconciling frame ancestors for the customer realm ..."
login
"$KCADM" update realms/processpuzzle-custom \
  -s "browserSecurityHeaders={\"contentSecurityPolicy\":\"${custom_content_security_policy}\",\"xFrameOptions\":\"\"}"

# `--import-realm` does not merge changes into an existing realm. Without this explicit update,
# every deployed customer realm keeps the import's Mailpit endpoint and accepts activation emails
# without ever delivering them to the new administrator.
json_string() {
  case "$1" in
    *$'\n'*|*$'\r'*)
      echo "ERROR: Keycloak SMTP values must not contain newlines." >&2
      exit 1
      ;;
  esac
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

if [[ ! "${CUSTOM_SMTP_PORT}" =~ ^[0-9]+$ ]]; then
  echo "ERROR: KEYCLOAK_CUSTOM_SMTP_PORT must be a numeric SMTP port." >&2
  exit 1
fi

case "${CUSTOM_SMTP_AUTH}" in
  true|false) ;;
  *)
    echo "ERROR: KEYCLOAK_CUSTOM_SMTP_AUTH must be true or false." >&2
    exit 1
    ;;
esac

case "${CUSTOM_SMTP_STARTTLS}" in
  true|false) ;;
  *)
    echo "ERROR: KEYCLOAK_CUSTOM_SMTP_STARTTLS must be true or false." >&2
    exit 1
    ;;
esac

case "${CUSTOM_SMTP_SSL}" in
  true|false) ;;
  *)
    echo "ERROR: KEYCLOAK_CUSTOM_SMTP_SSL must be true or false." >&2
    exit 1
    ;;
esac

if [ "${CUSTOM_SMTP_AUTH}" = "true" ] && { [ -z "${CUSTOM_SMTP_USERNAME}" ] || [ -z "${CUSTOM_SMTP_PASSWORD}" ]; }; then
  echo "ERROR: authenticated Keycloak SMTP requires username and password." >&2
  exit 1
fi

smtp_server="$(printf '{"host":"%s","port":"%s","from":"%s","fromDisplayName":"%s","replyTo":"%s","auth":"%s","user":"%s","password":"%s","starttls":"%s","ssl":"%s"}' \
  "$(json_string "${CUSTOM_SMTP_HOST}")" \
  "$(json_string "${CUSTOM_SMTP_PORT}")" \
  "$(json_string "${CUSTOM_SMTP_FROM}")" \
  "$(json_string "${CUSTOM_SMTP_FROM_DISPLAY_NAME}")" \
  "$(json_string "${CUSTOM_SMTP_REPLY_TO}")" \
  "${CUSTOM_SMTP_AUTH}" \
  "$(json_string "${CUSTOM_SMTP_USERNAME}")" \
  "$(json_string "${CUSTOM_SMTP_PASSWORD}")" \
  "${CUSTOM_SMTP_STARTTLS}" \
  "${CUSTOM_SMTP_SSL}")"

echo "Reconciling SMTP delivery for the customer realm via ${CUSTOM_SMTP_HOST}:${CUSTOM_SMTP_PORT} ..."
login
"$KCADM" update realms/processpuzzle-custom \
  -s "verifyEmail=true" \
  -s "actionTokenGeneratedByAdminLifespan=43200" \
  -s "smtpServer=${smtp_server}"

# --- the client -------------------------------------------------------------------------------
existing_id="$("$KCADM" get clients -r master --query "clientId=${CLIENT_ID}" --fields id --format csv --noquotes 2>/dev/null | tail -n +1 | head -1 || true)"

if [ -z "${existing_id}" ]; then
  echo "Creating client '${CLIENT_ID}' in master ..."
  # No standard flow and no direct grants: this account is never used by a browser or by a human.
  # Only the client-credentials grant, which is what makes the secret the whole credential.
  "$KCADM" create clients -r master \
    -s "clientId=${CLIENT_ID}" \
    -s 'name=ProcessPuzzle Platform Admin Service' \
    -s 'enabled=true' \
    -s 'publicClient=false' \
    -s 'serviceAccountsEnabled=true' \
    -s 'standardFlowEnabled=false' \
    -s 'directAccessGrantsEnabled=false' \
    -s "secret=${CLIENT_SECRET}"
  existing_id="$("$KCADM" get clients -r master --query "clientId=${CLIENT_ID}" --fields id --format csv --noquotes | head -1)"
else
  echo "Client '${CLIENT_ID}' already exists; refreshing its secret."
  "$KCADM" update "clients/${existing_id}" -r master -s "secret=${CLIENT_SECRET}"
fi

service_account_user="service-account-${CLIENT_ID}"

# --- create-realm, the one permission that cannot be realm-scoped ------------------------------
echo "Granting create-realm to ${service_account_user} ..."
"$KCADM" add-roles -r master --uusername "${service_account_user}" --rolename create-realm || \
  echo "  (already granted)"

# --- realm-management on every realm this account will administer -------------------------------
# `create-realm` lets it make a realm; administering the realm afterwards — clients, roles, users —
# needs the management roles for that realm. Keycloak grants the creating account full admin on a
# realm it created, so nothing more is needed for tenant realms. The four realms baked into the image
# were not created by this account, so they are granted explicitly here.
#
# The roles live on the `<realm>-realm` client IN MASTER, and that client carries only the
# fine-grained roles (manage-users, manage-realm, view-clients, …) — NOT the `realm-admin` composite,
# which exists solely on the `realm-management` client inside the realm itself and is unreachable
# from master. So the grant enumerates whatever roles the client actually has, read back from
# Keycloak rather than hard-coded, which also keeps it correct across Keycloak versions.
#
# These are the application stacks realms — see docs/application-stacks.md. The names have to match
# tools/docker/keycloak/import/*-realm.json exactly: a realm named here that does not exist is merely
# skipped below, so a stale name is a silent loss of admin rights rather than an error.
for realm in processpuzzle-testbed processpuzzle-biz processpuzzle-admin processpuzzle-custom; do
  login
  if ! "$KCADM" get "realms/${realm}" >/dev/null 2>&1; then
    echo "Realm '${realm}' is not present; skipping its realm-management grant."
    continue
  fi

  management_client_id="$("$KCADM" get clients -r master --query "clientId=${realm}-realm"     --fields id --format csv --noquotes 2>/dev/null | head -1 || true)"
  if [ -z "${management_client_id}" ]; then
    echo "  WARNING: master has no '${realm}-realm' client; ${service_account_user} cannot administer that realm."
    continue
  fi

  # Read the role names, then pass them as repeated --rolename arguments in one call.
  role_args=()
  role_count=0
  while read -r role_name; do
    if [ -n "${role_name}" ]; then
      role_args+=(--rolename "${role_name}")
      role_count=$((role_count + 1))
    fi
  done < <("$KCADM" get "clients/${management_client_id}/roles" -r master     --fields name --format csv --noquotes 2>/dev/null || true)

  if [ "${role_count}" -eq 0 ]; then
    echo "  WARNING: '${realm}-realm' exposes no roles; nothing granted."
    continue
  fi

  echo "Granting ${role_count} management roles on '${realm}' to ${service_account_user} ..."
  # add-roles is additive and tolerates a role the user already has, so re-running is a no-op.
  "$KCADM" add-roles -r master --uusername "${service_account_user}"     --cclientid "${realm}-realm" "${role_args[@]}" ||     echo "  WARNING: the grant on '${realm}' failed; the admin app cannot manage that realm's users."
done

echo "platform-admin-service is ready."
