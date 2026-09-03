# Tools

Utilities and infrastructure that support local development, CI, and deployment of the ProcessPuzzle platform. Nothing here ships in the production application bundle — these are the scaffolding around it.

## Directory layout

| Path | Purpose |
| --- | --- |
| [`docker/`](./docker) | Dockerfiles and compose stacks for the testbed, backend, and supporting services (Keycloak, MinIO, Postgres, Firebase emulators, json-server). |
| [`firebase/`](./firebase) | Firebase emulator seed data and local Functions sources used by the Firebase container. |
| [`httpRequests/`](./httpRequests) | IntelliJ HTTP Client environment file for ad-hoc requests against local and remote backends. |
| [`mock-backend/`](./mock-backend) | Standalone `json-server` mock with seed `db.json` and a self-signed cert — used when the full backend stack is overkill. |
| [`scripts/`](./scripts) | Build/release helpers: `release.ts`, `run-sonar-scanner.cjs`, `sanitize-lcov.cjs`. |

## Docker stacks

Two compose files at `tools/docker/`:

- **`docker-compose-ci.yaml`** — full local CI stack: the three Angular applications, one Spring backend per application stack, Keycloak (+ Postgres), MinIO, json-server, pgweb. Used by the `docker-build` Nx target and by CI to run e2e tests against a production-like topology.
- **`docker-compose-prod.yaml`** — slim image pull definition for the registry images. Used to smoke-test a published image, not to build.

Each service has its own folder under `docker/`, containing the `Dockerfile` plus any init scripts the image needs (e.g. `minio/init-minio.sh`, `postgresql/init-db.sql`, `firebase/serve.sh`).

### Services in the CI stack

| Service | Container | Image | Host port → container | Purpose |
| --- | --- | --- | --- | --- |
| `processpuzzle-testbed-frontend` | `processpuzzle-testbed-frontend` | `zsuffazs/processpuzzle-testbed-frontend` | `9090 → 80` | Angular testbed app served by nginx; entry point for e2e tests. |
| `processpuzzle-admin-frontend` | `processpuzzle-admin-frontend` | `zsuffazs/processpuzzle-admin-frontend` | `9091 → 80` | Angular staff administration app; calls `admin-backend`. |
| `processpuzzle-ui` | `processpuzzle-ui` | `zsuffazs/processpuzzle-ui` | `9092 → 80` | Angular tenant app; still calls `testbed-backend` (see below). |
| `testbed-backend` | `testbed-backend` | `zsuffazs/processpuzzle-testbed-backend` | `8080 → 8080` | Spring Boot backend for the **testbed** stack: database `processpuzzle_testbed`, realm `processpuzzle-testbed`, bucket prefix `processpuzzle-testbed`. |
| `admin-backend` | `admin-backend` | `zsuffazs/processpuzzle-testbed-backend` | `8083 → 8080` | The same image for the **admin** stack: database `processpuzzle_admin`, realm `processpuzzle-admin`, bucket prefix `processpuzzle-admin`. |
| `keycloak` | `testbed-keycloak` | `zsuffazs/testbed-keycloak` | `7070 → 8080` | OIDC provider. One realm per stack, imported from `tools/docker/keycloak/import/`; realm data lives in Postgres. |
| `keycloak-init` | `testbed-keycloak-init` | `zsuffazs/testbed-keycloak-init` | — | One-shot: creates the `master`-realm service account the backend uses to provision tenant realms. Idempotent. |
| `postgres` | `testbed-postgres` | `zsuffazs/testbed-postgres` | `5432 → 5432` | Postgres for Keycloak **and** for both stacks, one database each; volume `postgres_data`. |
| `pgweb` | `testbed-pgweb` | `zsuffazs/testbed-pgweb` | `8082 → 8081` | Web UI for Postgres inspection, mounted at `/pgweb`. Points at `processpuzzle_testbed`; showing another database means editing `PGWEB_DATABASE_URL`. |
| `minio` | `testbed-minio` | `zsuffazs/testbed-minio` | `7000 → 9000` (S3), `7001 → 9001` (console) | S3-compatible object store used by both backends; volume `minio-data`. Buckets are `<stack-prefix>-<purpose>`. |
| `json-server` | `json-server` | `zsuffazs/json-server` | `3000 → 3000` | REST mock for the *third-party* sources an application integrates with, never for a ProcessPuzzle feature; seeded from `tools/mock-backend/db.json` (see `tools/mock-backend/README.md`). |

> **Port note.** The Firestore emulator owns host port `8081` and pgweb takes host `8082` to avoid the bind collision (it still listens on `8081` inside the container, reached via `http://localhost:8082/pgweb`). That is why the second backend is published on `8083` rather than the next free-looking number. The testbed backend keeps `8080` because the Playwright suite, the testbed runtime configuration and `processpuzzle-ui` all name it.

> **First start.** `tools/docker/postgresql/init-db.sql` creates the two application databases, and Keycloak's `--import-realm` imports a realm only if it does not already exist — both run against a *fresh* `postgres_data` volume only. After changing either, reset with `docker compose -f tools/docker/docker-compose-ci.yaml down -v`.

### Service dependency diagram

Arrows show `depends_on` with `condition: service_healthy` — compose blocks each service's startup until every target it points at reports healthy. Edge labels show how the caller reaches the target at runtime.

One backend per application stack; see [`docs/application-stacks.md`](../docs/application-stacks.md). `processpuzzle-ui` pointing at the testbed backend is a known inconsistency, kept until that application is repurposed as the public site.

```mermaid
graph TD
    testbed[processpuzzle-testbed-frontend<br/>host :9090]
    admin[processpuzzle-admin-frontend<br/>host :9091]
    ui[processpuzzle-ui<br/>host :9092]
    tbackend[testbed-backend<br/>host :8080]
    abackend[admin-backend<br/>host :8083]
    keycloak[keycloak<br/>host :7070]
    kcinit[keycloak-init<br/>one-shot]
    postgres[(postgres<br/>host :5432)]
    pgweb[pgweb<br/>host :8082]
    minio[(minio<br/>host :7000 / :7001)]
    jsonserver[json-server<br/>host :3000]

    testbed -- REST --> tbackend
    testbed -- OIDC --> keycloak
    testbed -- REST --> jsonserver
    admin -- REST --> abackend
    admin -- OIDC --> keycloak
    ui -- REST --> tbackend
    ui -- OIDC --> keycloak

    tbackend -- S3 --> minio
    tbackend -- JDBC --> postgres
    tbackend -- "JWKS / admin API" --> keycloak
    abackend -- S3 --> minio
    abackend -- JDBC --> postgres
    abackend -- "JWKS / admin API" --> keycloak
    kcinit -- "Admin CLI" --> keycloak
    keycloak -- JDBC --> postgres
    pgweb -- read-only --> postgres

    classDef store fill:#eef,stroke:#446
    class postgres,minio store
```

## How pipeline stages work

The platform recognizes four pipeline stages, each with a different deployment target:

| Stage | Where it runs | How configs reach the browser |
| --- | --- | --- |
| `dev` | Local `nx serve`, no Docker | Angular build assets copy `apps/processpuzzle-testbed-frontend/src/run-time-conf/*` straight into `dist/` |
| `ci` | `docker-compose-ci.yaml` on a developer machine or CI runner | Templated at container start (see below) |
| `stage` | Firebase Hosting | Config file dropped into `<hosting-root>/run-time-conf/` by the deploy job |
| `prod` | Firebase Hosting | Same as stage, with prod values |

The Angular `ConfigurationService` (`libs/js-shared/util/src/lib/runtime-configuration/configuration.service.ts`) always fetches `run-time-conf/config.common.json` plus `run-time-conf/config.<PIPELINE_STAGE>.json` from the same origin that served the app. The mechanism for *getting those files into the right place* is what differs per stage.

## Stage-dependent environment variables (the `ci` case)

The browser cannot read container env vars — the JS bundle runs on the user's machine, not inside the nginx container. So the `ci` image renders its runtime config from container env vars **at container startup**, before nginx accepts traffic.

The flow:

1. **Shell / CI runner** exports values:
   ```sh
   export PIPELINE_STAGE=ci
   export FIREBASE_API_KEY=AIza...
   ```
2. **Compose** propagates them into the container via the `environment:` block in `docker-compose-ci.yaml`:
   ```yaml
   environment:
     PIPELINE_STAGE: ${PIPELINE_STAGE:-ci}
     FIREBASE_API_KEY: ${FIREBASE_API_KEY}
   ```
   The `${VAR}` on the right side is compose's substitution, expanded from the shell or a `.env` file next to the compose file.
3. **`docker-entrypoint.sh`** (baked into the image) runs `envsubst` against `config.ci.json.template` and writes the rendered file to `/usr/share/nginx/html/run-time-conf/config.ci.json`, then `exec`s nginx.
4. **Browser** fetches `http://<host>/run-time-conf/config.ci.json` and gets the templated values.

`envsubst`'s whitelist argument (`'${PIPELINE_STAGE} ${FIREBASE_API_KEY}'`) limits which placeholders get expanded — any other `$` in the template survives literally.

**Build-time vs. runtime — don't mix them up:**
- `ARG` in a Dockerfile and `build.args:` in compose → build time only. Use for things that decide what goes *into* the image (which stage's template to copy).
- `ENV` in a Dockerfile and `environment:` in compose → runtime, available to processes inside the container. Use for values the entrypoint script will template into the runtime config.

## Stage-dependent variables (`stage` and `prod`)

Firebase Hosting deploys are static-file uploads; there is no container entrypoint to template anything. The deploy job is responsible for writing the right `config.<stage>.json` next to the bundle before running `firebase deploy`. Secrets typically come from the CI provider's secret store (GitHub Actions secrets, Firebase CI config, etc.) and are injected into a `config.stage.json` / `config.prod.json` file as part of the deploy step.

`config.stage.json` and `config.prod.json` are intentionally **not** committed to the repo — they exist only as deployment artifacts produced by the pipeline.

## Running the CI stack locally

```sh
# from repo root — this workspace is npm, not pnpm; `pnpm nx` quarantines packages and breaks the build
npx nx run processpuzzle-testbed-frontend:docker-build   # builds testbed + backend images
docker compose -f tools/docker/docker-compose-ci.yaml up
```

A `.env` file in `tools/docker/` is the typical place for `PIPELINE_STAGE` and `FIREBASE_API_KEY`. Keep it gitignored.
`FIREBASE_API_KEY` is not optional even though nothing in the `ci` stack talks to Firebase: the testbed's
entrypoint (`tools/docker/processpuzzle-testbed-frontend/docker-entrypoint.sh`) fails fast on an unset one, so the container exits
before nginx starts. Any non-empty value will do locally. The other two frontends do not read it.

**Budget four minutes for a cold `up`, and do not read a transient `unhealthy` as a failure.** Measured
locally: Keycloak 40 s on a fresh database but 96 s against a reused one (it spends the difference trying to
reach cluster peers a previous container recorded), and each backend ~145 s, because Spring Modulith computes
the module structure with ArchUnit at startup and both backends now do it at once. The healthchecks carry
`start_period` values that cover this (150 s / 240 s); a probe that fails inside `start_period` does not count
against `retries`. Without them, `depends_on: condition: service_healthy` aborts the whole `up` with
`dependency failed to start` while the services in question are merely still booting.
