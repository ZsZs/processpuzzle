# Deploying the testbed stack to stage — the manual steps

Everything in the repository is done. What remains is configuration that lives outside git: DNS, one
GitHub Environment, two Coolify resources and one Keycloak client. This is the checklist.

Read [Build and deployment](build-deploy-strategy.md) §4 and §11 first if you want the *why*; this
document is only the *what*.

> **Coolify UI labels drift between versions.** Where a field is named below, treat the name as a
> hint and match on function. Anything that could not be verified from the repository is marked
> **⚠ verify in the UI**.

---

## 0. Order of operations — this part is not optional

1. DNS records exist (§1)
2. **Infrastructure resource** deployed, and its network exists (§3)
3. **Testbed application resource** created and deployed (§4)
4. Keycloak client redirect URI added by hand (§5)
5. GitHub Environment configured (§2) — can be done any time before the first `develop` push

Step 3 fails outright if step 2 has not happened. `docker-compose-apps.yaml` declares its network as
`external: true`, so if `processpuzzle-stage` does not exist yet, `up` aborts with:

```
network processpuzzle-stage declared as external, but could not be found
```

That is by design — the applications join the infrastructure's network rather than owning one — but
it does mean the infrastructure resource has to be up first.

---

## 1. DNS

Three `A` records pointing at the Coolify host:

| Record | Serves |
|---|---|
| `testbed.stage.processpuzzle.de` | the Angular frontend (nginx) |
| `api.stage.processpuzzle.de` | the Spring Boot backend |
| `auth.stage.processpuzzle.de` | Keycloak |

The backend needs a name of its own because **nginx does not proxy to it**. The browser calls
`APP_SERVICE_ROOT` cross-origin, which is what makes `APP_CORS_ALLOWED_ORIGINS` load-bearing rather
than decorative.

`.de`, not `.com`: it matches the Coolify control plane. Production is still written `.com` in
`.env.prod` and is deliberately unresolved — see [Build and deployment](build-deploy-strategy.md)
§12.

---

## 2. GitHub → Settings → Environments → `STAGE`

### Secrets

| Secret | Value | Status |
|---|---|---|
| `COOLIFY_WEBHOOK_TESTBED` | the full webhook URL from the **testbed application** resource's Webhook page, `?uuid=…` and all | **new — this is the one that was missing** |
| `COOLIFY_WEBHOOK` | the same, for the **infrastructure** resource | already set |
| `COOLIFY_TOKEN` | Coolify API token, `deploy` permission is enough | already set |

**Store the webhook URL without a trailing `&force=false`.**
[`coolify-deploy/action.yml`](../.github/actions/coolify-deploy/action.yml) appends its own `force`
parameter to whatever query string the URL already has, so a URL that carries one arrives with the
parameter twice.

This is the fix for [run 34049062656](https://github.com/ZsZs/processpuzzle/actions/runs/34049062656),
which was green while deploying nothing. The workflow now **fails** on a missing webhook instead of
warning, so once this secret exists a broken deploy is visible.

### Variables (`vars`, not secrets — both optional)

Setting these turns on two steps that are skipped while they are empty: the post-deploy readiness
poll, and the Playwright run against the deployed environment.

| Variable | Value |
|---|---|
| `TESTBED_FRONTEND_PUBLIC_URL` | `https://testbed.stage.processpuzzle.de` |
| `TESTBED_BACKEND_PUBLIC_URL` | `https://api.stage.processpuzzle.de` |

`TESTBED_FRONTEND_PUBLIC_URL` must name the **same origin** as
[`apps/processpuzzle-testbed-e2e/env/.env.stage`](../apps/processpuzzle-testbed-e2e/env/.env.stage),
which is where the Playwright suite reads its base URL from. That file is already
`https://testbed.stage.processpuzzle.de`.

Consider leaving them unset for the *first* deploy and adding them once the stack is verified by
hand — otherwise a first-start timeout reds a workflow whose deployment actually succeeded.

### Nothing else

The seven credentials in [`.env.example`](../tools/docker/env/.env.example) (`POSTGRES_PASSWORD`,
`PROCESSPUZZLE_DB_PASSWORD`, `KEYCLOAK_ADMIN_USERNAME` / `_PASSWORD`, `MINIO_ROOT_PASSWORD`,
`MINIO_SERVICE_PASSWORD`, `PLATFORM_ADMIN_CLIENT_SECRET`) are consumed **only by Coolify**. No
workflow reads them — `deploy-infrastructure.yml` and `deploy-testbed-apps.yml` reference nothing but
the webhook and the token. Keeping copies in the GitHub Environment is fine as a record, but it is
not what makes the deployment work.

---

## 3. Coolify → the existing **infrastructure** resource

Three changes, then redeploy.

| Field | Change to | Why |
|---|---|---|
| `KC_HOSTNAME` (env var) | `https://auth.stage.processpuzzle.de` | was `https://stage.auth.processpuzzle.com`. Keycloak builds the **issuer of every token** from this |
| Domain, `keycloak` service | `auth.stage.processpuzzle.de` → container port **8080** | ⚠ verify in the UI |
| — | redeploy | `KC_HOSTNAME` is baked into the running container's env |

`KC_HOSTNAME` has to match, character for character:

- `PROCESSPUZZLE_SECURITY_ISSUER_BASE_URL` on the application resource (§4)
- `AUTHENTICATION_SERVICE_ROOT` and `AUTH_SERVICE_CONFIG.authServerUrl` in
  [`config.stage.json`](../apps/processpuzzle-testbed-frontend/src/run-time-conf/config.stage.json)
  (already committed as `.de`)
- the domain above

A mismatch is a 401 on every authenticated request with nothing in the browser to explain it.

### Confirm the network before moving on

```bash
docker network ls | grep processpuzzle-stage
```

The infrastructure compose declares `name: ${PP_NETWORK:-processpuzzle}`, so with
`PP_NETWORK=processpuzzle-stage` set on the resource this is the name compose creates. If Coolify has
a *"connect to a predefined network"* toggle on the resource, it does not replace this — the explicit
`name:` is what the applications join. **⚠ verify in the UI** that no per-resource network renaming
is in play.

### Full variable list for this resource

Non-secret, copied from [`.env.stage`](../tools/docker/env/.env.stage) — which stays the source of
truth, because Coolify does **not** read `--env-file`:

```
PP_IMAGE_REGISTRY=ghcr.io/zszs
PP_IMAGE_TAG=stage
PP_PGWEB_VERSION=0.17.0
PP_NETWORK=processpuzzle-stage
PP_POSTGRES_PUBLISH=127.0.0.1:5432:5432
PP_KEYCLOAK_PUBLISH=127.0.0.1:7070:8080
PP_MINIO_API_PUBLISH=127.0.0.1:7000:9000
PP_MINIO_CONSOLE_PUBLISH=127.0.0.1:7001:9001
PP_PGWEB_PUBLISH=127.0.0.1:8082:8081
PP_JSON_SERVER_PUBLISH=127.0.0.1:3000:3000
KEYCLOAK_DB_NAME=keycloak
KEYCLOAK_DB_USERNAME=keycloak
PROCESSPUZZLE_DB_USERNAME=processpuzzle
PGWEB_DATABASE=processpuzzle_testbed
KC_HOSTNAME=https://auth.stage.processpuzzle.de
KC_HOSTNAME_STRICT=true
KC_INTERNAL_URL=http://keycloak:8080
MINIO_ROOT_USER=minioadmin
MINIO_SERVICE_USER=springboot
```

Plus, marked **secret**: `POSTGRES_PASSWORD`, `PROCESSPUZZLE_DB_PASSWORD`,
`KEYCLOAK_ADMIN_USERNAME`, `KEYCLOAK_ADMIN_PASSWORD`, `MINIO_ROOT_PASSWORD`,
`MINIO_SERVICE_PASSWORD`, `PLATFORM_ADMIN_CLIENT_SECRET`.

The first three carry a `${VAR:?}` guard, so omitting one fails at `up` with the variable's name in
the log. The rest fall back to the CI *demo* values — which is worse than failing, so check them.

---

## 4. Coolify → a **new** resource for the testbed stack

### Create it

| Setting | Value |
|---|---|
| Type | **Docker Compose** (not Application) |
| Repository | this repository, branch `develop` |
| Compose file | `tools/docker/docker-compose-apps.yaml` |
| Project / environment | the same one the infrastructure resource is in |

One resource for both halves, not one per image: a Coolify resource reads exactly one compose file,
and `depends_on` inside that file is then what starts the backend before the frontend. See
[Build and deployment](build-deploy-strategy.md) §4.

### Enable "pull latest images and restart"

**⚠ Do not skip this.** The resource watches the moving tag `:stage`, and `force=true` restarts a
resource *without* always re-pulling ([coollabsio/coolify#5318](https://github.com/coollabsio/coolify/issues/5318)).
Without the option the webhook cheerfully restarts the stale image and the deploy looks successful.

### Domains

| Service | Domain | Container port |
|---|---|---|
| `processpuzzle-testbed-frontend` | `testbed.stage.processpuzzle.de` | **80** |
| `testbed-backend` | `api.stage.processpuzzle.de` | **8080** |

Note the service name is `processpuzzle-testbed-frontend` while the *container* is `testbed-frontend`
— Coolify addresses services. The published ports are bound to `127.0.0.1`, deliberately: Coolify's
proxy reaches the containers over the compose network, and loopback still leaves them reachable
through an SSH tunnel for inspection. **⚠ verify in the UI** how it wants the port expressed.

### Environment variables

```
PP_IMAGE_REGISTRY=ghcr.io/zszs
PP_IMAGE_TAG=stage
PP_NETWORK=processpuzzle-stage
PP_TESTBED_FRONTEND_PUBLISH=127.0.0.1:9090:80
PP_TESTBED_BACKEND_PUBLISH=127.0.0.1:8080:8080
PIPELINE_STAGE=stage
SPRING_PROFILES_ACTIVE=stage
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/processpuzzle_testbed
PROCESSPUZZLE_DB_USERNAME=processpuzzle
PROCESSPUZZLE_SECURITY_STACK_REALM=processpuzzle-testbed
MINIO_BUCKET_PREFIX=processpuzzle-testbed
MINIO_ENDPOINT=http://minio:9000
MINIO_SERVICE_USER=springboot
KC_INTERNAL_URL=http://keycloak:8080
APP_CORS_ALLOWED_ORIGINS=https://testbed.stage.processpuzzle.de
PROCESSPUZZLE_SECURITY_ISSUER_BASE_URL=https://auth.stage.processpuzzle.de
```

Plus, marked **secret**: `PROCESSPUZZLE_DB_PASSWORD`, `MINIO_SERVICE_PASSWORD`,
`PLATFORM_ADMIN_CLIENT_SECRET` — the same three values as on the infrastructure resource. They are
credentials the infrastructure *created*; if they disagree the backend cannot log in to PostgreSQL or
MinIO.

Two you can **omit**, because their compose defaults are already right:

- `FIREBASE_API_KEY` — defaults to empty; Firebase was removed and `main.ts` reads it as `?? ''`
- `PROCESSPUZZLE_SECURITY_JWKS_BASE_URL` — defaults to `http://keycloak:8080`, which is correct.
  It must **not** be the public issuer URL: that origin does not resolve inside the container, and
  reading the signing keys from it is what once made every authenticated request answer 500

### Must agree with the infrastructure resource

| Variable | Consequence if it drifts |
|---|---|
| `PP_NETWORK` | `up` fails: external network not found |
| `PROCESSPUZZLE_DB_USERNAME` / `PROCESSPUZZLE_DB_PASSWORD` | backend cannot connect to PostgreSQL |
| `MINIO_SERVICE_USER` / `MINIO_SERVICE_PASSWORD` | object storage fails at the first upload |
| `PLATFORM_ADMIN_CLIENT_SECRET` | identity ports fall back to no-ops; everything else still works |
| `KC_INTERNAL_URL` | admin API and JWKS unreachable |
| `PROCESSPUZZLE_SECURITY_ISSUER_BASE_URL` = infra's `KC_HOSTNAME` | 401 on every authenticated request |

### Then copy the webhook URL into GitHub (§2)

---

## 5. Keycloak admin console on stage

Add to the `processpuzzle-testbed` realm → client `processpuzzle-testbed` → **Valid redirect URIs**:

```
https://testbed.stage.processpuzzle.de/*
```

**This has to be done by hand even though the change is committed.** The URI is in
[`processpuzzle-testbed-realm.json`](../tools/docker/keycloak/import/processpuzzle-testbed-realm.json),
but `--import-realm` **skips a realm that already exists** — so the committed file only reaches a
realm created after this change. The import file matters for a fresh environment; the console matters
for the one already running.

`webOrigins` is `"+"`, which derives CORS origins from the redirect URIs, so it needs no separate
edit.

---

## 6. Verify

### After §3 (infrastructure)

```bash
curl -fsS https://auth.stage.processpuzzle.de/realms/processpuzzle-testbed/.well-known/openid-configuration \
  | grep -o '"issuer":"[^"]*"'
```

The issuer must read exactly `https://auth.stage.processpuzzle.de/realms/processpuzzle-testbed`. If it
still says `.com` or `localhost`, `KC_HOSTNAME` did not take — redeploy.

### After §4 (applications)

```bash
curl -fsS https://api.stage.processpuzzle.de/actuator/health
curl -fsS https://testbed.stage.processpuzzle.de/home
curl -fsS https://testbed.stage.processpuzzle.de/assets/runtime-env.json   # must show PIPELINE_STAGE: stage
```

Allow up to **~7 minutes** for the backend's first start against an empty database: Spring Modulith's
ArchUnit module-structure pass plus the metadata seeding was measured at 262 s, and the healthcheck's
`start_period` is 420 s. A transient `unhealthy` inside that window is not a failure.

Then log in through the UI — that is the only check that exercises the issuer, the redirect URI and
CORS together.

### The end-to-end path

Push to `develop`. The `deploy-stage` job must show **`Redeploy the testbed stack` as success, not
skipped.** Skipped means the webhook secret is still missing.

---

## 7. When something is wrong

| Symptom | Cause |
|---|---|
| `network processpuzzle-stage declared as external, but could not be found` | infrastructure resource not deployed, or its `PP_NETWORK` differs |
| nginx exits, `host not found in upstream "json-server"` | the frontend is not on the infrastructure network — nginx resolves its upstream at startup |
| Browser shows HTTP status **0** on API calls | the frontend's origin is missing from `APP_CORS_ALLOWED_ORIGINS` |
| **401** on every authenticated request | `PROCESSPUZZLE_SECURITY_ISSUER_BASE_URL` ≠ Keycloak's advertised issuer |
| **500** on every authenticated request | `PROCESSPUZZLE_SECURITY_JWKS_BASE_URL` was set to the public URL; it must stay `http://keycloak:8080` |
| Login redirects to an "Invalid redirect URI" page | §5 not done |
| Deploy is green but nothing changed | *"pull latest images and restart"* is off, so the stale `:stage` image was restarted |
| Frontend serves the CI configuration | `PIPELINE_STAGE` is unset on the resource and fell back to its CI default |
| Backend answers but with demo credentials | a secret was omitted and fell back to the CI value — the non-`:?`-guarded ones do this silently |

The general rule behind half of that table: **every variable in both compose files carries a
`${VAR:-<ci default>}`**, so a variable missed in Coolify degrades to the CI value rather than to an
empty string. On the first deploy, read the resource's rendered `docker compose config` rather than
trusting the form.

---

## 8. Not needed

- **Building images in Coolify.** Both Dockerfiles are packaging-only (`COPY dist/…`); a git clone has
  no `dist/`. Both compose files are pull-only for exactly this reason, and Coolify's build step will
  log `No services to build` and exit 0. Images come from GHCR, built by CI.
- **A second compose file for stage.** One definition per layer serves `ci`, `stage` and `prod`; what
  differs is the environment.
- **`FIREBASE_API_KEY`.** See §4.
- **Anything for prod yet.** `.env.prod` still names unverified `.com` hosts.
