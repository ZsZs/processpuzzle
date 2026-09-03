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

KCADM=/opt/keycloak/bin/kcadm.sh

# kcadm stores an access token, and Keycloak's default lifespan for it is a minute. This script
# makes enough calls — and waits on enough of them — to outlive one, so the login is a function and
# is re-issued before each step rather than performed once at the top. Symptom when it is not:
# "Session has expired. Login again", mid-run, on whichever step happened to be slowest.
login() {
  "$KCADM" config credentials --server "$KC_URL" --realm master --user "$KC_ADMIN" --password "$KC_ADMIN_PASSWORD" >/dev/null 2>&1
}

echo "Waiting for Keycloak at ${KC_URL} ..."
until login; do
  sleep 2
done
echo "Authenticated against ${KC_URL} as ${KC_ADMIN}."

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
# realm it created, so nothing more is needed for tenant realms. The two realms baked into the image
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
for realm in processpuzzle-testbed processpuzzle-admin; do
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
