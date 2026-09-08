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
| `minio.stage.processpuzzle.de` | MinIO's **S3 API** (port 9000), for presigned URLs |

MinIO needs one because `MinioConfig` builds a second, *presigning* client from
`minio.public-endpoint` (`MINIO_PUBLIC_ENDPOINT`), and the URLs it signs are followed by the
**browser**. Left at the `minio-config.yaml` default they name `http://localhost:7000` and every
upload and download fails while the rest of the store works. Point the domain at container port
**9000** — the S3 API, not the 9001 console; publishing the console is a separate decision and
would want a hostname of its own, since its credentials are MinIO's root user.

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

If the resource previously deployed under the default name, this first redeploy has to *rename* the
network and will fail on `network processpuzzle has active endpoints` — see
[§7.1](#71-the-network-rename-deadlock) for the one-time cleanup.

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
| `processpuzzle-testbed-frontend` | `https://testbed.stage.processpuzzle.de` | **80** |
| `testbed-backend` | `https://api.stage.processpuzzle.de` | **8080** |

**⚠ Both domains have to be entered, with the `https://` scheme.** Coolify generates the Traefik
router labels from this field alone — DNS pointing at the server does nothing by itself, and a
missing domain surfaces as *"no available server"* rather than as any deployment error. See
[§7.3](#73-no-available-server-with-both-containers-healthy).

Note the service name is `processpuzzle-testbed-frontend` while the *container* is `testbed-frontend`
— Coolify addresses services. The **container** port in that table is what the proxy talks to over the
compose network, and it is 8080 for the backend regardless of the host port below. **⚠ verify in the
UI** how it wants the port expressed.

The published (host) ports are bound to `127.0.0.1`, deliberately: the proxy needs none of them, and
loopback still leaves the containers reachable through an SSH tunnel for inspection. The backend's is
**8180**, not 8080: `coolify-proxy` publishes `0.0.0.0:8080` for the Traefik dashboard on every
Coolify host, and binding `127.0.0.1:8080` on top of a wildcard bind fails just the same — see
[§7.2](#72-port-is-already-allocated-on-8080).

### Environment variables

```
PP_IMAGE_REGISTRY=ghcr.io/zszs
PP_IMAGE_TAG=stage
PP_NETWORK=processpuzzle-stage
PP_TESTBED_FRONTEND_PUBLISH=127.0.0.1:9090:80
PP_TESTBED_BACKEND_PUBLISH=127.0.0.1:8180:8080
PIPELINE_STAGE=stage
SPRING_PROFILES_ACTIVE=stage
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/processpuzzle_testbed
PROCESSPUZZLE_DB_USERNAME=processpuzzle
PROCESSPUZZLE_SECURITY_STACK_REALM=processpuzzle-testbed
MINIO_BUCKET_PREFIX=processpuzzle-testbed
MINIO_ENDPOINT=http://minio:9000
MINIO_PUBLIC_ENDPOINT=https://minio.stage.processpuzzle.de
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
| `network processpuzzle has active endpoints (name:"coolify-proxy")` | the resource is renaming its network and `coolify-proxy` still holds the old one — see [§7.1](#71-the-network-rename-deadlock) |
| `Bind for :::8080 failed: port is already allocated` | `PP_TESTBED_BACKEND_PUBLISH` is unset on the resource, so it fell back to the CI default `8080:8080` — which `coolify-proxy` owns; see [§7.2](#72-port-is-already-allocated-on-8080) |
| nginx exits, `host not found in upstream "json-server"` | the frontend is not on the infrastructure network — nginx resolves its upstream at startup |
| **502 Bad Gateway** on a Coolify domain | the domain names the *host-published* port (7070 / 7000 / 9090 / 8180) instead of the container port (8080 / 9000 / 80 / 8080) — Traefik reaches containers over the network, where only the container port exists |
| Presigned upload/download URLs point at `localhost:7000` | `MINIO_PUBLIC_ENDPOINT` unset, so it fell back to the `minio-config.yaml` default |
| Infrastructure deploy fails with `dependency failed to start: container keycloak-… is unhealthy`, after ~14 min | stale JDBC_PING peers — see [§7.4](#74-keycloak-unhealthy-on-redeploy-stale-jgroups-peers) |
| Browser shows `no available server`, both containers healthy | no domain set on the *service*, so no Traefik router exists — see [§7.3](#73-no-available-server-with-both-containers-healthy) |
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

### 7.1 The network-rename deadlock

Happens **once per environment**, on the first deploy after `PP_NETWORK` is set on the resource —
`processpuzzle` → `processpuzzle-stage`, and later `processpuzzle` → `processpuzzle-prod`. The deploy
log ends like this, after every image has pulled successfully:

```
Network processpuzzle Removing
Network processpuzzle-stage Creating
network:processpuzzle Error response from daemon: error while removing network:
  network processpuzzle has active endpoints (name:"coolify-proxy" id:"e11a0c20655d")
```

The compose *network key* is `processpuzzle` in both cases; only the `name:` it resolves to changed
(`docker-compose-infrastructure.yaml:32`). Compose therefore has to drop the old network and create
the new one — but `coolify-proxy` is attached to the old one for reverse proxying and stays attached
across deployments, so the removal fails and takes the create down with it. Nothing is wrong with the
images or the compose file, and no amount of redeploying clears it: the old network has to go by hand.

**Run these on the Docker host** — Coolify's sidebar **Terminal**, with the *server* (`localhost`)
selected as the target, not a container; a container has no Docker CLI or socket. Requires Terminal
Access enabled under Servers → *server* → Security.

```bash
# who still holds the OLD network — note: no -stage/-prod suffix here
docker network inspect processpuzzle

# detach every container the "Containers" block above listed
docker network disconnect -f processpuzzle coolify-proxy

docker network rm processpuzzle
```

Then **redeploy the infrastructure resource**, and afterwards the **applications resource as well**:
its containers are still attached to the network that just went away, and nginx resolves its
`json-server` and backend upstreams once at startup, so it will not recover on its own.

Two things to check before testing the public URL:

```bash
docker network ls | grep processpuzzle          # only the -stage name should remain
docker network inspect processpuzzle-stage | grep coolify-proxy
```

Coolify re-attaches the proxy on its next reconcile; if it has not,
`docker network connect processpuzzle-stage coolify-proxy`. And if `docker network rm` still refuses
after everything is disconnected, the endpoint is stale — `docker network prune`, or restart the
Docker daemon.

### 7.2 "port is already allocated" on 8080

```
Container testbed-backend-… Starting
Error response from daemon: failed to set up container networking: driver failed programming
external connectivity … Bind for :::8080 failed: port is already allocated
```

Two facts are packed into that one line.

**The `:::8080` is the diagnosis.** A wildcard bind means the resource did *not* have
`PP_TESTBED_BACKEND_PUBLISH` set, so compose used the `${…:-8080:8080}` CI default. Had the §4 value
been applied the message would have named `127.0.0.1:8080`. This is the "degrades to the CI value"
rule of §7 showing up as a hard failure rather than as a silent misconfiguration — for once.

**8080 is not available on a Coolify host at all.** `coolify-proxy` publishes it for the Traefik
dashboard:

```bash
ss -ltnp | grep ':8080'                                  # docker-proxy on 0.0.0.0 and [::]
docker ps --format '{{.Names}}	{{.Ports}}' | grep 8080  # coolify-proxy 0.0.0.0:8080->8080/tcp
```

So setting the port to `127.0.0.1:8080:8080` does not fix it either: a specific-address bind fails
while the wildcard holds the port. Hence **8180** in §4 and in `.env.stage` / `.env.prod`.

Nothing about the application changes. The proxy routes `api.stage.processpuzzle.de` to
`testbed-backend:8080` over the compose network, `APP_SERVICE_ROOT` in `config.stage.json` names that
public URL, and the healthcheck probes `localhost:8080` *inside* the container. Only the SSH-tunnel
port moves. CI keeps `8080:8080` in `.env.ci` — a GitHub runner has no Coolify proxy, and the local
`config.ci.json` expects the backend on `localhost:8080`.

### 7.3 `no available server`, with both containers healthy

Traefik's page, not the application's. A **404** means no router matched the hostname; **502** means a
router matched and the address refused the connection; *"no available server"* means the matched
service has no address at all — which is also what Coolify's catch-all answers for a hostname it has
never heard of. So it is the symptom of a *routing* gap, and a green deployment tells you nothing
about it: both containers can be `Up (healthy)` throughout.

Run these on the host. Note that **Coolify overrides `container_name:`** with
`<service>-<resourceUuid>-<id>`, so `docker inspect testbed-frontend` fails with "no such object" and
a piped `grep` swallows the error — read the name out of `docker ps` first:

```bash
docker ps --format '{{.Names}}	{{.Status}}' | grep testbed
FE=<the frontend container name from above>
docker inspect $FE --format '{{range $k,$v := .Config.Labels}}{{$k}}={{$v}}
{{end}}' | grep -i traefik
docker inspect $FE --format '{{json .NetworkSettings.Networks}}' | tr ',' '
' | grep -o '"[a-z-]*":{'
docker network inspect ${PP_NETWORK:-processpuzzle-stage}   --format '{{range .Containers}}{{.Name}} {{end}}' | tr ' ' '
' | grep coolify-proxy
```

| Reading | Cause | Fix |
|---|---|---|
| **no `traefik.*` labels** | no domain on the *service* in the resource UI — Coolify generates every label from that field | enter both domains of §4, with the `https://` scheme, and redeploy |
| labels present, `traefik.docker.network` names a network the container is not on | our `networks:` key replaces Coolify's default, so the container never joins `coolify` | Settings → **"Connect To Predefined Network"**, redeploy |
| `coolify-proxy` missing from the app network | the proxy was never attached, or is still on the pre-rename network | `docker network connect <network> coolify-proxy`, then check [§7.1](#71-the-network-rename-deadlock) |
| labels and networks both fine | the domain names a port the container does not listen on | container port is **80** for the frontend and **8080** for the backend — the host publishes (9090 / 8180) are irrelevant to the proxy |

Coolify addresses **services**, so the fields belong to `processpuzzle-testbed-frontend` and
`testbed-backend` — the container names it generated are not selectable and not what you configure.

### 7.4 Keycloak unhealthy on redeploy — stale jgroups peers

The deploy waits on Keycloak and eventually gives up:

```
Container keycloak-… Waiting
Container keycloak-… Error dependency keycloak failed to start
dependency failed to start: container keycloak-… is unhealthy
```

Nothing is wrong with Keycloak. Its log shows JOIN attempts against an address that answers
`Connection refused`, then:

```
too many JOIN attempts (10): becoming singleton
```

`JDBC_PING` records every container in the `JGROUPS_PING` table of the `keycloak` database and
never removes the row, so each redeploy leaves a dead peer behind and each subsequent start pays
to discover that. **The cost grows with every deployment**, which is what makes this look
intermittent: it fit inside `start_period: 150s` for months and then took 14 minutes.

Fixed at the image level as of 2026-09-08 — `tools/docker/keycloak/Dockerfile` builds with
`KC_CACHE=local`, so there is no cluster to join. `cache` is a build-time option, so it has to be
baked in; supplying it at run time makes an `--optimized` start exit 2.

Two consequences worth knowing. Deploying the fix needs the **image rebuilt and re-promoted**
(`tools/docker/**` triggers Build-Infrastructure, which calls Deploy-Infrastructure), not just a
redeploy of the existing `:stage` image. And to unblock a deployment *before* that lands, clear the
table by hand:

```bash
PG=<postgres container>
docker exec $PG psql -U keycloak -d keycloak -c '\dt'          # confirm the table's exact name
docker exec $PG psql -U keycloak -d keycloak -c 'DELETE FROM "JGROUPS_PING";'
```

Safe with Keycloak running — the rows are discovery hints, and a live node re-registers itself.

One thing to check afterwards, whichever route you take: `keycloak-init` has
`depends_on: keycloak: condition: service_healthy`, so an aborted deploy never ran it and the
`platform-admin` service-account client may be missing. Without it the backend's identity ports
fall back to their no-op implementations and user management silently does nothing, while every
other feature works. Re-running the deployment runs it; it is idempotent.

---

## 8. Not needed

- **Building images in Coolify.** Both Dockerfiles are packaging-only (`COPY dist/…`); a git clone has
  no `dist/`. Both compose files are pull-only for exactly this reason, and Coolify's build step will
  log `No services to build` and exit 0. Images come from GHCR, built by CI.
- **A second compose file for stage.** One definition per layer serves `ci`, `stage` and `prod`; what
  differs is the environment.
- **`FIREBASE_API_KEY`.** See §4.
- **Anything for prod yet.** `.env.prod` still names unverified `.com` hosts.
