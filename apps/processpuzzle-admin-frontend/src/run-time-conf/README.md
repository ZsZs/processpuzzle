# Runtime configuration

`ConfigurationService.init()` deep-merges `config.common.json`, then `config.${PIPELINE_STAGE}.json`,
then any `CONFIGURATION_OVERRIDES` from `assets/runtime-env.json`. Later wins.

Two keys are worth knowing about:

- **`PLATFORM_ADMIN_SERVICE_ROOT`** is *not* org-scoped. Every path in `platform-admin-api.yaml`
  starts with `/platform`, so this is a bare host — unlike `APP_SERVICE_ROOT`, which ends in
  `/organizations/<orgKey>`.
- **`AUTH_SERVICE_CONFIG.realm`** is the fixed `processpuzzle-admin` realm and never resolved from
  the URL. This application administers *all* tenants; its staff users do not belong to any one of
  them. That is the whole difference from `processpuzzle-biz-frontend`, which substitutes the tenant's realm
  from the first path segment before bootstrap.

The realm, its `platform-admin` role and its seed user are imported by
`tools/docker/keycloak/import/processpuzzle-admin-realm.json`, whose `processpuzzle-admin` client
declares `http://localhost:4201/*` (dev serve) and `http://localhost:9091/*` (compose) as redirect
URIs — which is why this application's `serve` and `serve-static` targets use those ports.
