# Application stacks

Four application types share one set of infrastructure services — PostgreSQL, Keycloak and MinIO — while
staying otherwise independent of one another. This document is the single source of truth for **which
identifiers belong to which stack**: realm, organization key, database, bucket prefix and hostname. It
exists because those five names are decided once per stack and then repeated across a dozen files
(realm imports, compose services, CORS allow-lists, run-time configuration, nginx vhosts), and a stack
whose names drift is a stack that fails at run time in a way no test catches.

> **Status.** Decided 2026-09-02; the infrastructure and backend half was implemented the same day.
> Per-stack PostgreSQL databases, the renamed realms and the MinIO
> bucket prefix are in place, as are all three application renames. The `processpuzzle-biz-frontend` repurposing
> and the subdomains are not — see [Deltas from the current implementation](#deltas-from-the-current-implementation).
>
> **Biz, Admin, and Custom application sources are outside this repository.** This document still
> specifies all four application types, because **the identifiers are shared infrastructure**: the
> realms, the databases and the bucket prefixes are declared in this repository's
> `tools/docker/`, and a stack whose names drift across a repository boundary fails in exactly the
> way this document exists to prevent. What the public repository no longer contains is the
> *applications* for Biz, Admin, and Custom.

## The four application types

| | #1 Testbed | #2 ProcessPuzzle UI | #3 ProcessPuzzle Admin | #4 Customer application |
| --- | --- | --- | --- | --- |
| **Purpose** | Try out framework features | Public product site + customer onboarding | Internal staff administration | Customer's ProcessPuzzle application |
| **Audience** | Anyone, self-registered | Anyone, anonymous | ProcessPuzzle employees only | Customer organization's authenticated users |
| **Hostname** | `testbed.processpuzzle.com` | `processpuzzle.com` | `admin.processpuzzle.com` | Per-customer hostname |
| **Nx application** | `processpuzzle-testbed-frontend` | `processpuzzle-biz-frontend` | `processpuzzle-admin-frontend` | `custom-shell` |
| **Keycloak realm** | `processpuzzle-testbed` | `processpuzzle-biz` | `processpuzzle-admin` | `processpuzzle-custom` |
| **Organization key** | `processpuzzle-testbed` | `processpuzzle-biz` | `processpuzzle-admin` | Customer-specific |
| **PostgreSQL database** | `PROCESSPUZZLE_TESTBED`&nbsp;[^folding] | — none | `PROCESSPUZZLE_ADMIN`&nbsp;[^folding] | `PROCESSPUZZLE_CUSTOM`&nbsp;[^folding] |
| **MinIO bucket prefix** | `processpuzzle-testbed` | — none | `processpuzzle-admin` | `processpuzzle-custom` |
| **Backend** | `testbed-backend` (container 8080; host 8180 on Coolify)&nbsp;[^proxyport] | `processpuzzle-biz-backend` (new, onboarding only) | `admin-backend` (host 8083) | `processpuzzle-custom-backend` (one deployment per customer) |

[^proxyport]: Container 8080 is what the reverse proxy and the healthcheck use, and it never varies.
    The *host* publish is for SSH-tunnel inspection only and is `127.0.0.1:8180:8080` on `stage` and
    `prod`, because `coolify-proxy` owns `0.0.0.0:8080` for the Traefik dashboard. CI keeps `8080:8080`.
    See §7.2 of [the stage runbook](stage-deployment-runbook.md#72-port-is-already-allocated-on-8080).

The fixed stacks follow a mechanical naming pattern: the realm, organization key, and bucket prefix
are the same string, and the database is that string upper-cased with dashes replaced by underscores.
Custom differs only where it must: its shared realm, database, and bucket prefix are fixed, while its
organization key and backend deployment belong to the individual customer.

> **The Hostname row is the one thing that is not settled.** `stage` is deployed on **`.de`**, matching
> the Coolify control plane: `testbed.stage.processpuzzle.de`, with `api.stage.processpuzzle.de` for
> the backend and `auth.stage.processpuzzle.de` for Keycloak — see `tools/docker/env/.env.stage`. The
> `.com` names in the table above are the *intended* production names and nothing has verified those
> records exist; `apps/processpuzzle-testbed-e2e/env/.env.prod` already disagrees with them by naming
> `testbed.processpuzzle.de`. Resolving the split is tracked in
> [Build and deployment](build-deploy-strategy.md) §12, and it changes this table when it lands. Note
> that a stack's hostname is the *only* name that varies by environment — the realm, the organization
> key, the database and the bucket prefix are the same in `ci`, `stage` and `prod`, because they name
> the stack rather than the deployment.

Both stack keys are in `ReservedOrganizationKeys.DEFAULTS`, so no customer can claim one — which for
these stacks would mean claiming a realm and a bucket namespace, not just a URL segment.

**With one exemption: the key of the stack the deployment itself serves.** A backend bootstraps its own
organization through the ordinary claim path — `DefaultAppLoader` calls `checkOrganizationKey` and skips
any file whose key it cannot claim — so reserving the key a deployment serves stops that deployment from
ever creating its own organization, and every `createAppDefinition` in it then answers
`OrganizationNotFoundException`. `ReservedOrganizationKeys` therefore removes
`processpuzzle.security.stack-realm` from the set (overridable as
`platform-admin.stack-organization-key`), and removes it *after* the configured additions, so a
deployment cannot re-reserve it by configuration. The protection is not lost: bootstrap runs before the
first request is served, and from then on the key answers `organization.key.taken`. What each deployment
exempts is only its own name — a testbed backend still refuses `processpuzzle-admin`.

**And the exempted organization gets no realm of its own.** Its realm already exists: it is imported from
`tools/docker/keycloak/import/<stack>-realm.json`, complete with the stack's public client and users. So
`OrganizationRealmProvisioner` skips realm creation for that one key — the organization still reaches
`ACTIVE`, since the realm it names is already serving requests — and skips realm *deletion* for it too.
Both halves matter. Creating would add a tenant `processpuzzle-biz` client and the org roles to a realm that
is not a tenant realm; deleting would remove the realm every user of the stack authenticates against, in
response to nothing more than a row being deleted.

[^folding]: Written upper-case here to show the derivation, but created unquoted, and PostgreSQL folds
    unquoted identifiers to lower case. The databases are therefore `processpuzzle_testbed` and
    `processpuzzle_admin`, and those are the same databases this table names. Quoting them instead
    would force quoting at every connection site forever. See `tools/docker/postgresql/10-init-db.sh`.

### #1 ProcessPuzzle Testbed

Demonstrates and exercises the framework. It needs no real security, but several features only make
sense behind a login, so **self-registration is enabled and a registered user may grant themselves
roles**. That combination is safe only because the stack holds nothing but demonstration data — it is
the reason the testbed must never share a database, a realm or a bucket with another stack.

### #2 ProcessPuzzle UI

The public face of the product at `processpuzzle.com`: marketing content and the onboarding funnel for
prospective customers. Its Biz applications authenticate with the `processpuzzle-biz` realm and public
`processpuzzle-biz` client; local Docker uses `http://localhost:9092`. It has no PostgreSQL database or
bucket prefix of its own, and it does not call `processpuzzle-testbed-backend`. Onboarding needs a little
server-side work (capture a prospect, provision a trial), and that is a separate, small
`processpuzzle-biz-backend` rather than an exception carved into the platform backend.

Keeping this stack free of Keycloak is what lets it be cached, mirrored and taken to a CDN without a
session story.

### #3 ProcessPuzzle Admin

The internal staff surface — creating, suspending and deleting tenants, and the billing views.
Completely decoupled from #1 and #2: its own realm, its own database, its own subdomain. Staff hold
the `platform-admin` realm role, which is what the backend's `/platform/**` paths require.

Because it is the stack that can delete a customer, decoupling here is a security boundary and not
merely tidiness: a compromise of the testbed's self-registration must not put a staff token within
reach.

## Shared infrastructure

One PostgreSQL, one Keycloak and one MinIO serve all four application types. Sharing the *servers* while
separating the *namespaces* is the whole design:

- **PostgreSQL** — one database per fixed application type, plus `keycloak` for Keycloak's own storage.
  `PROCESSPUZZLE_CUSTOM` is a pooled customer database, protected by organization scoping and row-level
  security.
- **Keycloak** — one realm per application type. The Custom realm is shared by customer organizations,
  using Keycloak Organizations; the other realms are isolation boundaries.
- **MinIO** — bucket names are `<stack-prefix>-<purpose>`, e.g. `processpuzzle-admin-documents`.

### One backend deployment per application type

A backend is deployed once for each fixed application type that needs it. Customer applications are
the exception: every customer gets a dedicated `processpuzzle-custom-backend` deployment, configured
for the shared Custom realm, database, and bucket prefix and restricted to that customer's organization
key. The database remains pooled; backend deployment and access control are the customer isolation
boundaries.

Until 2026-09-04 this was literally the same image twice, `testbed-backend` and `admin-backend`, and
both instances kept every feature module because the component scan is not per-stack. That is no
longer so: the staff stack's backend is `processpuzzle-admin-backend` in the private repository,
which composes `platform-admin` on top of these libraries, while `processpuzzle-testbed-backend` here
composes only the public ones. What still differs purely by environment is the per-stack
configuration — `SPRING_DATASOURCE_URL`, `PROCESSPUZZLE_SECURITY_STACK_REALM`,
`MINIO_BUCKET_PREFIX` and the CORS allow-list.

The rejected alternative was one shared Custom backend serving every customer. Dedicated deployments
prevent one customer's workload or failure from affecting another while retaining the operational
simplicity of the pooled Custom database.

## Deltas from the current implementation

The infrastructure and backend half is done; the application-level half is not. Recorded so the gap
stays visible.

### Done

| Area | Was | Now |
| --- | --- | --- |
| Persistence | H2 in-memory; PostgreSQL hosted only `keycloak` | `processpuzzle_testbed` and `processpuzzle_admin`, created by `tools/docker/postgresql/10-init-db.sh`; H2 is test-scope only |
| Backend deployments | One `processpuzzle-backend` container serving every frontend | `testbed-backend`, one stack. `admin-backend` (host 8083) ran the same image beside it until the platform-admin extraction moved it to the private repository. |
| Testbed realm | `processpuzzle`, registration disabled | `processpuzzle-testbed`, `registrationAllowed: true` |
| Admin realm | `processpuzzle-platform` | `processpuzzle-admin` |
| Admin realm client id | `processpuzzle-ui` (in the platform realm — misleading) | `processpuzzle-admin` |
| MinIO buckets | Flat, keyed by mime type (`configuration`, `images`, …) | `<stack-prefix>-<purpose>`, from `minio.bucket-prefix` |
| Stack organization keys | Claimable by a customer | Reserved in `ReservedOrganizationKeys.DEFAULTS`, except the key the deployment itself serves |
| Stack realm lifecycle | Provisioned like a tenant's, so the stack's own bootstrap wrote to it | Infrastructure-owned: `OrganizationRealmProvisioner` creates and deletes no realm for the deployment's own stack key |
| Trusted realm property | `processpuzzle.security.platform-realm` | `…stack-realm` — "the realm this instance serves", which is what it always meant |
| Admin application | `apps/platform-admin`, container `platform-admin` | `apps/processpuzzle-admin-frontend`, container `processpuzzle-admin-frontend`; image `zsuffazs/processpuzzle-admin-frontend`, Sonar key `processpuzzle_processpuzzle_admin_frontend`. The `platform-admin-frontend` / `platform-admin-backend` **libraries** keep their names. |
| Backend application | `apps/processpuzzle-backend`, artifact `processpuzzle-backend`, image `zsuffazs/processpuzzle-backend` | `apps/processpuzzle-testbed-backend`, artifact `processpuzzle-testbed-backend`, image `zsuffazs/processpuzzle-testbed-backend`, main class `ProcessPuzzleTestbedBackendApplication`. Still one image for both deployments, so the `admin-backend` service runs the testbed-named image until a separate admin backend exists. |
| Testbed application | `apps/processpuzzle-testbed`, container `processpuzzle-testbed`, image `zsuffazs/processpuzzle-testbed` | `apps/processpuzzle-testbed-frontend`, compose service and container both `testbed-frontend` (shortened afterwards to match `testbed-backend` — the container first, the service later), image `zsuffazs/processpuzzle-testbed-frontend`, Sonar key `processpuzzle_testbed_frontend`. The npm package stays `@processpuzzle/testbed` so its version history and release tags survive. |
| Tenant application | `apps/processpuzzle-ui`, container `processpuzzle-ui`, image `zsuffazs/processpuzzle-ui`, Sonar key `processpuzzle_processpuzzle_ui` | `apps/processpuzzle-biz-frontend`, container `processpuzzle-biz-frontend`, image `zsuffazs/processpuzzle-biz-frontend`, Sonar key `processpuzzle_biz_frontend`, npm package `@processpuzzle/processpuzzle-biz-frontend` (never published, so nothing to preserve), e2e project `processpuzzle-biz-e2e`. A rename only — the repurposing below is still outstanding. |
| Tenant realm client id | `processpuzzle-ui` | `processpuzzle-biz` — `keycloak.admin.tenant-client-id` and the SPA's `AUTH_SERVICE_CONFIG.clientId` must agree, so both moved together. Realms provisioned before this hold the old client and need a `down -v` reset locally. |

> The `zsuffazs/*` image coordinates above record what the rename produced at the time. Every image
> has since moved to `ghcr.io/zszs/*` and Docker Hub is no longer published to — see
> [`docs/build-deploy-strategy.md`](build-deploy-strategy.md) §9.

### Still to do

| Area | Today | Target |
| --- | --- | --- |
| Testbed self-service roles | The catalog side exists: every `RoleDefinition` written in `base-workflow` is projected into a realm role of the same name in that organization's realm, so there is now something authoritative for a token to carry, and `RealmRoleMembershipPolicy` already reads realm roles out of the caller's token. Missing is the granting half — nothing grants a registering user any of those roles — and the membership check still reads `RoleDefinition.entityRoleId` rather than the role id that is now a realm role in its own right | A registered user may grant themselves roles |
| `processpuzzle-biz-frontend` | Tenant org-admin surface; reads an orgKey path segment, still calls `testbed-backend`. Now in the private repository, unchanged | Public site + onboarding, using the `processpuzzle-biz` Keycloak realm/client; no platform backend |
| `processpuzzle-biz-backend` | Does not exist | Small onboarding-only backend, in the private repository |
| Hostnames | Ports on `localhost` — 9090 here, 9091 / 9092 in the private repository | Subdomains of `processpuzzle.com` |
| Prod application topology | Resolved for the shared services: `docker-compose-prod.yaml` is gone, replaced by one `docker-compose-infrastructure.yaml` plus `tools/docker/env/.env.prod`. What is still missing is a public origin for each *application* — nothing publishes port 80 or reverse-proxies `/api/` | Per-app deployment resources (strategy §§4, 10, 12), not a compose file |
| Schema management | Hibernate `ddl-auto: update` | A migration tool |

**`processpuzzle-biz-frontend` changes meaning**, and that is the one item that is more than a rename. It
was the tenant-facing org-admin application, which decision #2 turns into the public product site; the
org-admin surface needs a home before that change lands — see the open question below. That work now
happens in the private repository, which is where the application went on 2026-09-04, and it is
blocked there on `processpuzzle-biz-backend` existing. `@processpuzzle/org-admin` stays public, so
whichever application ends up hosting the tenant admin surface can mount it.

### Consequences worth knowing

- **Seeded metadata is now sticky.** The seed importers are create-only, and the database survives a
  restart, so editing a seed YAML no longer reaches a stack that has already been seeded. Pre-existing
  behaviour that H2 was masking; resetting a stack means dropping its database.
- **A renamed realm needs a volume reset.** `--import-realm` skips realms that already exist and realms
  live in the `postgres_data` volume, so `npm run stack-clean` is what makes the renamed realms
  appear. The same reset is what creates the per-stack databases:
  `10-init-db.sh` runs only on an empty data directory. The failure mode to know about is not the missing
  realm but a realm that already holds a stack's name without being the imported one — reserving the keys
  (above) stops new ones, and a volume from before that change can still carry one. See
  [`tools/docker/keycloak/README.md`](../tools/docker/keycloak/README.md#a-realm-that-already-exists-under-the-right-name-is-the-dangerous-case).
- **Objects in the old flat buckets are unreachable.** `documents` and `images` are not prefixed, so
  nothing looks in them any more. Local development data only.
- **One of the two MinIO problems is fixed.** `docker-compose-prod.yaml` had no MinIO service at all;
  the split into one shared `docker-compose-infrastructure.yaml` means every environment now runs it.
  Still open: `minio-config.yaml` hard-codes `http://localhost:7000`, so the endpoint needs making
  per-environment before production object storage works. And `init-minio.sh` only ever created two of
  the eight buckets — the other six work because `UploadObject` asks `CreateBucket` for whatever it
  needs, which is still true of the prefixed names.
- **The infrastructure images are published; the application images are not yet.**
  [`deploy-infrastructure.yml`](../.github/workflows/deploy-infrastructure.yml) builds and pushes the
  five built infra images to `ghcr.io/zszs/processpuzzle-*` (pgweb is referenced upstream), which is
  what makes the shared layer deployable at all. CI still publishes only the testbed application image
  (`.github/actions/build-image/action.yml`); the remaining application images wait on their per-app
  workflows — see [strategy §12](build-deploy-strategy.md).

## Customer deployment model

Customer organizations share the `processpuzzle-custom` realm, `PROCESSPUZZLE_CUSTOM` database, and
`processpuzzle-custom` bucket prefix. Keycloak Organizations supplies the organization claim, while
each customer receives a dedicated `processpuzzle-custom-backend` deployment. Provisioning therefore
creates the Keycloak organization and customer backend before Admin calls that backend's internal seed
endpoint; customers no longer receive individual realms, databases, or bucket prefixes.
