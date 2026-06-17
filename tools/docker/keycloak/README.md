# Keycloak (CI / local Docker)

This image is built from `quay.io/keycloak/keycloak:26.4.7` and is wired up by `tools/docker/docker-compose-ci.yaml` to talk to the `postgres` service.

## Realm config as code

The `import/` directory is copied into `/opt/keycloak/data/import/` inside the image. Keycloak starts with `--import-realm`, which imports any `*-realm.json` it finds — but only if the realm does not already exist in the database. This is idempotent and safe to re-run.

The committed realm is `processpuzzle`. It contains the `processpuzzle-testbed` public client with the redirect URIs, web origins, and CSP `frame-ancestors` override needed for the testbed to authenticate against `http://localhost:9090` (Docker) and `http://localhost:4200` (`nx serve`).

## Updating the realm after UI changes

1. Make the change in the Keycloak admin UI against a running container.
2. Export the realm:

   ```
   docker exec testbed-keycloak /opt/keycloak/bin/kc.sh export \
     --dir /tmp/export --realm processpuzzle --users realm_file
   docker cp testbed-keycloak:/tmp/export/processpuzzle-realm.json \
     tools/docker/keycloak/import/
   ```

3. Commit the updated JSON.
4. On the next fresh database (or fresh CI run), the new config will be imported automatically.

## Forcing a re-import locally

`--import-realm` skips realms that already exist. To re-apply the JSON against an existing database, either:

- drop the realm in the admin UI first, then restart, **or**
- wipe the Postgres volume (`docker compose -f tools/docker/docker-compose-ci.yaml down -v`) and bring the stack back up.

## Bootstrap admin

The Compose file sets `KC_BOOTSTRAP_ADMIN_USERNAME=admin` / `KC_BOOTSTRAP_ADMIN_PASSWORD=admin_password`. These are only used on first boot to seed the `master` realm admin and are not part of the imported `processpuzzle` realm.
