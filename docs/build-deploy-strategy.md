# ProcessPuzzle build & deployment strategy

Status: v2 — the shared **infrastructure layer** is delivered (§§2, 4, 10, 11): one compose
definition, one environment-parameterized workflow, GHCR + Coolify webhook. The six per-app
workflows and their Application resources are still to come (§12). Tagging strategy proposed as a
sensible default, open to revision.

## 1. Source layout

Two GitHub repositories, coupled at build time only (via git submodule), independent at deploy time.

### `processpuzzle` (public)
- `apps/` — **processpuzzle-testbed-frontend**, **processpuzzle-testbed-backend**
- `libs/` — the shared platform feature libraries that both this repo's testbed app *and* the biz repo depend on:
  - `base-entity` (backend + frontend) — two-layer EAV/JSONB entity system
  - `base-state` (backend) — flat state machine engine
  - `base-workflow` (backend) — SPEM-inspired workflow engine
  - `processpuzzle-frontend` building blocks — Angular signals-first components
  - `@processpuzzle/e2e-testing` — metadata-driven E2E testing infrastructure
- Infrastructure definitions for **PostgreSQL**, **Keycloak**, **MinIO** currently live here too (see open question in §6)

### `processpuzzle-biz` (private)
- `apps/` — **processpuzzle-admin-frontend**, **processpuzzle-admin-backend**, **processpuzzle-biz-frontend**, **processpuzzle-biz-backend**
- `libs/` — the libraries that are *commercial* rather than platform, and therefore cannot live in the public repo:
  - `platform-admin-backend` / `platform-admin-frontend` — the org registry, plans, subscriptions, billing and the staff UI over them. **Moved out of the public repo on 2026-09-04**, together with `processpuzzle-admin-frontend`, `processpuzzle-biz-frontend` and `processpuzzle-biz-e2e` — see [Extracting platform-admin](platform-admin-extraction.md)
  - `subscription-backend` / `subscription-frontend` — **do not exist yet**. The thin subscription functionality behind `processpuzzle-biz-frontend`: the public site's signup step and whatever it needs to hand to Admin over the Biz → Admin REST edge
- Pulls in `processpuzzle` as a **git submodule** to reuse the `libs/` platform features above at build time — already configured

> Note that the private repo therefore has libraries of its own, not only applications. Any two of
> its four apps may share one (`platform-admin-*` is Admin-only today, but `subscription-*` is
> consumed by Biz and read by Admin), which is what makes them libraries rather than app-internal
> packages. The public repo's `libs/` list above is abridged — `base-rule`, `base-app`,
> `base-widget`, `base-document`, `org-admin`, `processpuzzle-core`, `processpuzzle-store` and
> `api-contracts` stay public too. In particular **`org-admin` stays**: per-tenant user management is
> a platform feature, not a commercial one.

## 2. What actually gets built

The six application images, plus **five of the six infrastructure images** — corrected from the original claim that none of the infrastructure is built. Four of them bake configuration that has to travel with the image, so referencing the upstream image by tag would mean mounting that configuration at deploy time instead:

| Image | What it bakes on top of upstream |
|---|---|
| `processpuzzle-postgres` | `postgresql/10-init-db.sh` — the per-stack databases and the application role |
| `processpuzzle-keycloak` | `keycloak/import/*-realm.json`, plus `kc.sh build` for an optimized start |
| `processpuzzle-keycloak-init` | `keycloak/init/bootstrap-platform-admin-client.sh` |
| `processpuzzle-minio` | `minio/init-minio.sh` — buckets and the Spring service account |
| `processpuzzle-json-server` | `tools/mock-backend/db.json` |
| pgweb | *nothing* — so it is not built. Referenced as `sosedoff/pgweb:0.17.0`, a pinned upstream tag |

Application images:

| Repo | Images built |
|---|---|
| `processpuzzle` | `processpuzzle-testbed-frontend`, `processpuzzle-testbed-backend` |
| `processpuzzle-biz` | `processpuzzle-admin-frontend`, `processpuzzle-admin-backend`, `processpuzzle-biz-frontend`, `processpuzzle-biz-backend` |

The infrastructure images dropped the misleading `testbed-` prefix and moved to GHCR
(`ghcr.io/zszs/processpuzzle-*`) when they became one shared layer; the application images are still
on Docker Hub until their per-app workflows land.

## 3. Flow diagram

![Build and deployment flow](build-deploy-flow.svg)

The platform libraries and the testbed app both live in `processpuzzle`; `processpuzzle-biz` reaches them only through the submodule (dashed arrow) — never a runtime dependency, just a build-time one. Each repo's CI builds and pushes only its own app images; everything lands on the same Coolify instance but as independent, individually redeployable resources sitting on top of one shared infrastructure layer.

## 4. Coolify resource model

Coolify supports two resource types — using the right one per layer is what preserves independent app lifecycles (the property lost with a single docker-compose file):

- **Docker Compose resource** — manages a whole compose file as one unit. Appropriate for the **infrastructure layer** (Postgres, Keycloak, MinIO) since it's conceptually one thing and doesn't need independent redeploys.
- **Application resource** — one Dockerfile/image per resource, each with its own build trigger, environment variables, deploy webhook, and redeploy control. Used for **each of the 6 application images**, so redeploying one app never touches another — the same independence OpenShift's per-app Deployments gave.

All resources sit in the same Coolify project/environment so the application resources have network access to the shared infrastructure resource.

## 5. GitHub Actions setup

Per app, a workflow that:
1. Triggers on push to `main` / a release tag, scoped with `paths:` filters (or driven by `nx affected`) so only the changed app rebuilds
2. Builds the app's Docker image
3. Pushes to GHCR, e.g. `ghcr.io/zszs/processpuzzle-testbed-backend:stage`
4. Calls that app's Coolify deploy webhook (Coolify generates one webhook URL + token per Application resource) to trigger the redeploy

Six small, independent workflows (or one matrix workflow per repo) rather than one monolithic pipeline.

## 6. Repo synchronization

No syncing or merging needed between `processpuzzle` and `processpuzzle-biz`. The existing git submodule is the correct — and only necessary — coupling: it pulls the shared `libs/` platform features into the biz repo's build, and the resulting images are self-contained. Each repo's CI stays fully independent otherwise.

## 7. Shared infrastructure configuration discipline

Each Application resource gets its own environment variables pointing at the *same* shared instances but with per-app identity, per the platform topology:

| App | Database | Keycloak realm | MinIO bucket prefix |
|---|---|---|---|
| Testbed | `PROCESSPUZZLE_TESTBED` | `processpuzzle-testbed` | `processpuzzle-testbed` |
| Biz | `PROCESSPUZZLE_BIZ` | `processpuzzle-biz` | `processpuzzle-biz` (uncertain if needed) |
| Admin | `PROCESSPUZZLE_ADMIN` | `processpuzzle-admin` | `processpuzzle-admin` |

Coolify's project-level shared environment variables (host names, credentials) plus per-resource overrides (db name, realm, bucket) keep "same infra, different identity per app" enforced structurally rather than by convention.

## 8. Decisions log

- **Infra definition location** — resolved: the Postgres/Keycloak/MinIO compose definition lives in the `processpuzzle` repo (as reflected in §1 and §4). The earlier note about a combined testbed+biz docker-compose living in `processpuzzle-biz` is superseded — infra and applications are no longer bundled together at all, per the independent-lifecycle model in §4.
- **Registry: GHCR** (2026-09-04). The infrastructure images are `ghcr.io/zszs/processpuzzle-*`, pushed with the workflow's built-in `GITHUB_TOKEN` — no third-party registry credential to hold. The application images follow when their per-app workflows land.
- **Deployment trigger: the Coolify deploy webhook** (2026-09-04). One `curl` against the URL Coolify prints on the resource's Webhook page, wrapped in the [`coolify-deploy`](../.github/actions/coolify-deploy/action.yml) composite action so the six app workflows reuse it. The whole URL comes from a secret, which keeps the action independent of Coolify's URL shape.
- **One compose file per layer, not per environment** (2026-09-04). `docker-compose-ci.yaml` and `docker-compose-prod.yaml` were two definitions of the same infrastructure that had already drifted — prod had no MinIO and no pgweb. They are replaced by `docker-compose-infrastructure.yaml` (the shared layer, §4's Docker Compose resource) and `docker-compose-apps.yaml` (a holding position until each app image is its own Application resource). CI and `npm run stack-*` overlay both.
- **Committed `.env.<environment>` for non-secrets, GitHub Environment secrets for credentials** (2026-09-04). `tools/docker/env/.env.{ci,stage,prod}` are in git and hold no credentials except `ci`'s demo values, which were always in git. See §11 for how they reach Coolify, which does *not* read them.

## 9. Proposed default: image tagging & promotion

Not yet decided with certainty — proposed as the sensible default, open to revision:

- Every push to `main` builds and pushes one image tagged with the commit SHA, e.g. `ghcr.io/zszs/processpuzzle-testbed-backend:sha-<commit>`.
- That same image is **promoted**, not rebuilt, from stage to production — re-tag `sha-<commit>` as `:stage` on deploy to stage, and as `:prod` once verified, rather than running a separate build per environment. This guarantees the exact bytes tested on stage are what reach production.
- Coolify's Application resource for stage watches the `:stage` tag; production watches `:prod`.

## 10. The workflow template

Two workflows, and the split between them is what makes §9's promotion real — the image is built
**once**, on `develop`, and every later environment points at that same digest.

### `Build-ProcessPuzzle-Infrastructure` — [`build-infrastructure.yml`](../.github/workflows/build-infrastructure.yml)

Owns the trigger: **any** change under `tools/docker/**` or `tools/mock-backend/**`, on `push` to
`feature/**` and `develop` and on `pull_request` to `develop`. Matrix-builds the five built images
with `context: .` (every Dockerfile COPYs from the repo root) and `cache-from/to: type=gha`.

- **`feature/**` and pull requests build only** — `push: false`, no registry login. Proving the
  Dockerfiles build is the whole job; publishing a feature branch's bytes to a tag a deployment
  watches would defeat the point of watching it. It also means a fork PR, whose `GITHUB_TOKEN` is
  read-only, is not a special case.
- **`develop` pushes** `sha-<commit>` and `latest`, then calls the deploy workflow with that
  `sha-<commit>`, which reaches stage as a re-tag.

The path filter is the whole directory rather than the four image folders: it over-triggers slightly,
but GHA caching makes an unaffected image nearly free, and the alternative is a path list that
silently rots whenever a Dockerfile gains a `COPY`.

### `Deploy-Infrastructure` — [`deploy-infrastructure.yml`](../.github/workflows/deploy-infrastructure.yml)

No trigger of its own; three ways in. Four jobs:

| Job | Runs when | What it does |
|---|---|---|
| `resolve` | always | Derives the lower-case environment tag and the build-or-promote decision once, rather than repeating the expressions in three `if:` conditions |
| `build-and-push` | `image_tag` empty | Matrix over the five images, as above. The escape hatch for standing an environment up from nothing |
| `promote` | `image_tag` given | `docker buildx imagetools create` re-tags an existing `sha-<commit>` as `:stage` / `:prod`. Nothing is pulled, built or pushed — §9's guarantee that the exact bytes tested on stage are what reach production |
| `deploy` | either of the two succeeded or was skipped | `environment: ${{ … }}`, so GitHub resolves the per-environment secrets, then the `coolify-deploy` action, then a best-effort readiness poll so that a red workflow means a red stack |

The three ways in: `workflow_call` **with** an `image_tag` (what the build workflow does on
`develop`); `workflow_dispatch` **with** one (promote a verified `sha-<commit>` to `PROD`); and
`workflow_dispatch` **without** one (build from the current ref first). A caller in either repo can
invoke it, as `docs/build-and-deploy-caller.yml` sketches.

**To make one of the six app workflows from this pair:** replace the matrix with the single app
image, drop the `promote` job's image loop down to that one image, and point `deploy` at that
Application resource's own `COOLIFY_WEBHOOK`. Nothing else changes.

### Secrets and variables per GitHub Environment

`STAGE` and `PROD`, none of which existed before this change. `tools/docker/env/.env.example`
documents where each is consumed.

| Secret | Used for |
|---|---|
| `COOLIFY_WEBHOOK` | full deploy webhook URL, including `?uuid=` |
| `COOLIFY_TOKEN` | Coolify API token, `deploy` permission only |
| `POSTGRES_PASSWORD` | Keycloak's own DB role |
| `PROCESSPUZZLE_DB_PASSWORD` | the application role created by `10-init-db.sh` |
| `KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD` | bootstrap admin |
| `MINIO_ROOT_PASSWORD` / `MINIO_SERVICE_PASSWORD` | MinIO root + the Spring service account |
| `PLATFORM_ADMIN_CLIENT_SECRET` | master-realm service account |

Two optional **variables** (`vars`, not secrets) enable the readiness gate:
`KEYCLOAK_PUBLIC_URL` and `MINIO_PUBLIC_URL`. The step is skipped when neither is set, since
Keycloak's health endpoint lives on its management port and a reverse proxy need not expose it.

## 11. Three things to know about Coolify

- **It does not read `--env-file`.** A Coolify Docker Compose resource reads the compose file from git and interpolates it with the *resource's own* environment variables. So for `stage` / `prod` the committed `tools/docker/env/.env.<environment>` file is the documented source of truth that has to be entered once into the resource (credentials marked as secret there), while `--env-file` is what `ci` and local development use. Every variable in the compose file carries a `${VAR:-<ci default>}` default, so one missed in Coolify degrades to the CI value rather than to an empty string — check the rendered `docker compose config` on the first deploy.
- **`force=true` restarts without always re-pulling** ([coollabsio/coolify#5318](https://github.com/coollabsio/coolify/issues/5318)). A resource that watches a moving tag therefore needs Coolify's *"pull latest images and restart"* option enabled. That, plus watching `:stage` / `:prod`, is exactly §9's model. `tools/docker/docker-compose-infrastructure.yaml` additionally sets `pull_policy: always` on the five infrastructure services, so the file is correct on its own rather than depending on that checkbox.
- **It builds before it deploys, from the repo root.** Coolify runs `docker compose … build --pull` ahead of `up`, passing `--project-directory <artifact dir>` — the repo root. Compose resolves a relative `build.context` against the *project directory*, not against the compose file's directory, so a `context: ../../` written for `tools/docker/` climbs two levels above the root and fails with `lstat /tools: no such file or directory`. Both halves of that are why the infrastructure compose file carries **no `build:` section at all**: fixing only the path would have Coolify succeed at the wrong thing, rebuilding on the deployment host bytes that §9 says must come from CI. The `build:` sections live in `tools/docker/docker-compose-build.yaml`, overlaid by CI and by `npm run stack-up-build`, where the project directory defaults to `tools/docker` and the relative context is correct. With nothing to build, Coolify's build step logs `No services to build` and exits 0.

*Optional follow-up, deliberately not done here:* push the `.env.<environment>` values into the
Coolify resource over its API before triggering the deploy, making the repo the true source of truth.
It needs an endpoint shape verified against the running Coolify version, so it should land as its own
step once the manual path is proven.

## 12. Still open

- The six per-app workflows, and the per-app Coolify **Application** resources they deploy to. `docker-compose-apps.yaml` is the holding position until then.
- Creating the Coolify project, resources and environment variables — manual, one-off, and a precondition for the `deploy` job to do anything.
