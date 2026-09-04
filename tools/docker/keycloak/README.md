# Keycloak (CI / local Docker)

This image is built from `quay.io/keycloak/keycloak:26.4.7` and is wired up by `tools/docker/docker-compose-ci.yaml` to talk to the `postgres` service.

## Realm config as code

The `import/` directory is copied into `/opt/keycloak/data/import/` inside the image. Keycloak starts with `--import-realm`, which imports any `*-realm.json` it finds — but only if the realm does not already exist in the database. This is idempotent and safe to re-run.

**One realm per application stack** — see [`docs/application-stacks.md`](../../../docs/application-stacks.md). Realms are Keycloak's isolation boundary: a token from one stack is not merely under-privileged in another, it is unverifiable there, because the realms do not share signing keys.

| File | Realm | Client | For |
| --- | --- | --- | --- |
| `processpuzzle-testbed-realm.json` | `processpuzzle-testbed` | `processpuzzle-testbed` | The testbed on `http://localhost:9090` (Docker) and `http://localhost:4200` (`nx serve`). Self-registration is on: the stack holds demonstration data only. Seeds a `test-user` / `test`. |
| `processpuzzle-admin-realm.json` | `processpuzzle-admin` | `processpuzzle-admin` | The staff administration app on `http://localhost:9091` / `http://localhost:4201`. Declares the `platform-admin` realm role and seeds a `platform-admin` / `platform-admin` user holding it. |

Three things in these files are easy to get wrong:

- **`default-roles-<realm>` embeds the realm name.** A seed user's `realmRoles` names `default-roles-processpuzzle-admin`, not `default-roles-processpuzzle`. Renaming the realm without renaming that role leaves the user importable but without its default role mappings, and nothing says so.
- **The admin realm's client id is not `keycloak.admin.tenant-client-id`.** The client here (`processpuzzle-admin`) is the public client the staff application authenticates with, named in `apps/processpuzzle-admin-frontend/src/run-time-conf/config.common.json` and `config.prod.json`. `keycloak.admin.tenant-client-id` in the backend's `application.yaml` is a *different* thing — the client created inside each provisioned tenant realm — and it stays `processpuzzle-biz`.
- **`platform-admin` is a role name, not a stack name.** Which authority may reach `/platform/**` is `processpuzzle.security.platform-admin-authority`, unaffected by any realm rename.

The `master` realm's service account is *not* here: see `init/bootstrap-platform-admin-client.sh` and the comment at the top of it for why it cannot be. That script also grants the management roles for both realms above, so their names have to match this table.

- **The management roles for a realm are not `realm-admin`.** `realm-admin` is a composite on the `realm-management` client *inside* a realm, and a `master` service account cannot reach it. What master has is a `<realm>-realm` client carrying the 18 fine-grained roles (`manage-users`, `manage-realm`, `view-clients`, …), and the script grants all of them, read back from Keycloak rather than hard-coded. Asking for `realm-admin` there fails with `Role not found for name: realm-admin` — which the script used to swallow, leaving the admin application unable to manage either stack realm's users with nothing in the log to say so.

## Updating a realm after UI changes

1. Make the change in the Keycloak admin UI against a running container.
2. Export the realm:

   ```
   docker exec testbed-keycloak /opt/keycloak/bin/kc.sh export \
     --dir /tmp/export --realm processpuzzle-testbed --users realm_file
   docker cp testbed-keycloak:/tmp/export/processpuzzle-testbed-realm.json \
     tools/docker/keycloak/import/
   ```

3. Commit the updated JSON.
4. On the next fresh database (or fresh CI run), the new config will be imported automatically.

## Forcing a re-import locally

`--import-realm` skips realms that already exist, and realms live in the `postgres_data` volume — so a **renamed** realm does not appear until the old one is gone. Either:

- drop the realm in the admin UI first, then restart, **or**
- wipe the Postgres volume (`docker compose -f tools/docker/docker-compose-ci.yaml down -v`) and bring the stack back up.

The volume wipe also drops the two application databases, which `tools/docker/postgresql/init-db.sql` then recreates — it likewise runs only on an empty data directory. Since the backends now persist to PostgreSQL rather than H2, that reset is what resets a stack's data.

### A realm that already exists under the right name is the dangerous case

The rename above at least fails visibly — the realm is simply absent. The quiet one is a realm that **already
carries a stack's name but is not the imported realm**. Keycloak logs one line and moves on:

```
Realm 'processpuzzle-testbed' already exists. Import skipped
```

Everything then looks fine while the realm's settings are whatever created it. Observed on a reused volume in
September 2026: `processpuzzle-testbed` existed as a **tenant** realm provisioned earlier through the
platform-admin flow, so it had `registrationAllowed=False` against the file's `true`, client
`processpuzzle-ui` instead of `processpuzzle-testbed`, direct-access grants off, and no users at all — no
testbed login was obtainable, and nothing in the startup log said why. Either drop the realm and restart, or
patch the live realm through the admin API (`PUT /admin/realms/<realm>`) when a wipe is too expensive.

That collision is now prevented at the source, in two places. `processpuzzle-testbed` and
`processpuzzle-admin` are in `ReservedOrganizationKeys.DEFAULTS`, so no *customer* can be provisioned under
a stack's name. And each backend, which does exempt the one key it serves — it has to, or it could not
create its own organization — no longer provisions a realm for that exempted key:
`OrganizationRealmProvisioner` skips both the create and the delete for it, because a stack realm is
imported from this directory rather than owned by the application. Without that skip the exemption merely
moved the problem: the backend would bolt a tenant `processpuzzle-biz` client onto the imported stack realm,
and deleting the stack's organization row would have deleted the realm every user of the stack
authenticates against.

Volumes predating this change still hold a squatting realm, and the fix is per volume. On the local CI
stack, 3 September 2026: the squatting realm held no users, one auto-generated client and two
auto-generated roles, so it was deleted through the admin API and `testbed-keycloak` restarted — the log
then read `Realm 'processpuzzle-testbed' imported` instead of `already exists. Import skipped`, and the
realm came back with the `processpuzzle-testbed` client (redirects to 9090 and 4200), `test-user` and
`registrationAllowed=true`. Deleting a realm also drops the `<realm>-realm` client in `master` that carried
the service account's management roles, so `keycloak-init` has to be re-run afterwards.

## Bootstrap admin

The Compose file sets `KC_BOOTSTRAP_ADMIN_USERNAME=admin` / `KC_BOOTSTRAP_ADMIN_PASSWORD=admin_password`. These are only used on first boot to seed the `master` realm admin and are not part of either imported realm.
