# Application stacks

Three applications share one set of infrastructure services — PostgreSQL, Keycloak and MinIO — while
staying otherwise independent of one another. This document is the single source of truth for **which
identifiers belong to which stack**: realm, organization key, database, bucket prefix and hostname. It
exists because those five names are decided once per stack and then repeated across a dozen files
(realm imports, compose services, CORS allow-lists, run-time configuration, nginx vhosts), and a stack
whose names drift is a stack that fails at run time in a way no test catches.

> **Status.** Decided 2026-09-02; the infrastructure and backend half was implemented the same day.
> Per-stack PostgreSQL databases, one backend deployment per stack, the renamed realms and the MinIO
> bucket prefix are in place, as are all three application renames. The `processpuzzle-biz-frontend` repurposing
> and the subdomains are not — see [Deltas from the current implementation](#deltas-from-the-current-implementation).
>
> **Two of the three stacks' applications left this repository on 2026-09-04.** Stacks #2 and #3 are
> now built in the private `processpuzzle-biz` repository, together with the commercial
> `platform-admin` feature — see [Extracting platform-admin](platform-admin-extraction.md). This
> document still specifies all three, because **the identifiers are shared infrastructure**: the
> realms, the databases and the bucket prefixes are declared in this repository's
> `tools/docker/`, and a stack whose names drift across a repository boundary fails in exactly the
> way this document exists to prevent. What the public repository no longer contains is the
> *applications* for #2 and #3.

## The three stacks

| | #1 Testbed | #2 ProcessPuzzle UI | #3 ProcessPuzzle Admin |
| --- | --- | --- | --- |
| **Purpose** | Try out framework features | Public product site + customer onboarding | Internal staff administration |
| **Audience** | Anyone, self-registered | Anyone, anonymous | ProcessPuzzle employees only |
| **Hostname** | `testbed.processpuzzle.com` | `processpuzzle.com` | `admin.processpuzzle.com` |
| **Nx application** | `processpuzzle-testbed-frontend` | `processpuzzle-biz-frontend` | `processpuzzle-admin-frontend` |
| **Keycloak realm** | `processpuzzle-testbed` | — none | `processpuzzle-admin` |
| **Organization key** | `processpuzzle-testbed` | — none | `processpuzzle-admin` |
| **PostgreSQL database** | `PROCESSPUZZLE_TESTBED`&nbsp;[^folding] | — none | `PROCESSPUZZLE_ADMIN`&nbsp;[^folding] |
| **MinIO bucket prefix** | `processpuzzle-testbed` | — none | `processpuzzle-admin` |
| **Backend** | `testbed-backend` (host 8080) | `processpuzzle-biz-backend` (new, onboarding only) | `admin-backend` (host 8083) |

The pattern is deliberately mechanical: **for stacks #1 and #3 the realm name, the organization key and
the bucket prefix are the same string**, and the database is that string upper-cased with dashes
replaced by underscores. Anything that needs to name a stack derives the name rather than inventing
one, so a new stack is one decision and not five.

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
prospective customers. Anonymous throughout, so it has **no realm, no organization key, no PostgreSQL
database and no bucket prefix**, and it does not call `processpuzzle-testbed-backend`. Onboarding needs a
little server-side work (capture a prospect, provision a trial), and that is a separate, small
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

One PostgreSQL, one Keycloak and one MinIO serve all three stacks. Sharing the *servers* while
separating the *namespaces* is the whole design:

- **PostgreSQL** — one database per stack, plus `keycloak` for Keycloak's own storage. No stack has a
  grant on another's database.
- **Keycloak** — one realm per stack. Realms are Keycloak's isolation boundary: a token from
  `processpuzzle-testbed` is not merely under-privileged in `processpuzzle-admin`, it is unverifiable
  there, because the realms do not share signing keys.
- **MinIO** — bucket names are `<stack-prefix>-<purpose>`, e.g. `processpuzzle-admin-documents`.

### One backend deployment per stack

A backend is deployed **once per stack that needs it**, each instance configured with a single stack's
database, realm and bucket prefix. The backend therefore stays a single-tenant application, and
decoupling is a property of the deployment rather than logic inside it.

Until 2026-09-04 this was literally the same image twice, `testbed-backend` and `admin-backend`, and
both instances kept every feature module because the component scan is not per-stack. That is no
longer so: the staff stack's backend is `processpuzzle-admin-backend` in the private repository,
which composes `platform-admin` on top of these libraries, while `processpuzzle-testbed-backend` here
composes only the public ones. What still differs purely by environment is the per-stack
configuration — `SPRING_DATASOURCE_URL`, `PROCESSPUZZLE_SECURITY_STACK_REALM`,
`MINIO_BUCKET_PREFIX` and the CORS allow-list.

The rejected alternative was one shared instance routing to a datasource per organization key. That
would make the backend tenant-aware at the persistence layer, and it would couple the availability of
the staff surface to that of a public demonstration site — the two stacks whose failure domains most
need to stay apart.

## Deltas from the current implementation

The infrastructure and backend half is done; the application-level half is not. Recorded so the gap
stays visible.

### Done

| Area | Was | Now |
| --- | --- | --- |
| Persistence | H2 in-memory; PostgreSQL hosted only `keycloak` | `processpuzzle_testbed` and `processpuzzle_admin`, created by `tools/docker/postgresql/10-init-db.sh`; H2 is test-scope only |
| Backend deployments | One `processpuzzle-backend` container serving every frontend | `testbed-backend` (host 8080), one stack. `admin-backend` (host 8083) ran the same image beside it until the platform-admin extraction moved it to the private repository. |
| Testbed realm | `processpuzzle`, registration disabled | `processpuzzle-testbed`, `registrationAllowed: true` |
| Admin realm | `processpuzzle-platform` | `processpuzzle-admin` |
| Admin realm client id | `processpuzzle-ui` (in the platform realm — misleading) | `processpuzzle-admin` |
| MinIO buckets | Flat, keyed by mime type (`configuration`, `images`, …) | `<stack-prefix>-<purpose>`, from `minio.bucket-prefix` |
| Stack organization keys | Claimable by a customer | Reserved in `ReservedOrganizationKeys.DEFAULTS`, except the key the deployment itself serves |
| Stack realm lifecycle | Provisioned like a tenant's, so the stack's own bootstrap wrote to it | Infrastructure-owned: `OrganizationRealmProvisioner` creates and deletes no realm for the deployment's own stack key |
| Trusted realm property | `processpuzzle.security.platform-realm` | `…stack-realm` — "the realm this instance serves", which is what it always meant |
| Admin application | `apps/platform-admin`, container `platform-admin` | `apps/processpuzzle-admin-frontend`, container `processpuzzle-admin-frontend`; image `zsuffazs/processpuzzle-admin-frontend`, Sonar key `processpuzzle_processpuzzle_admin_frontend`. The `platform-admin-frontend` / `platform-admin-backend` **libraries** keep their names. |
| Backend application | `apps/processpuzzle-backend`, artifact `processpuzzle-backend`, image `zsuffazs/processpuzzle-backend` | `apps/processpuzzle-testbed-backend`, artifact `processpuzzle-testbed-backend`, image `zsuffazs/processpuzzle-testbed-backend`, main class `ProcessPuzzleTestbedBackendApplication`. Still one image for both deployments, so the `admin-backend` service runs the testbed-named image until a separate admin backend exists. |
| Testbed application | `apps/processpuzzle-testbed`, container `processpuzzle-testbed`, image `zsuffazs/processpuzzle-testbed` | `apps/processpuzzle-testbed-frontend`, container `processpuzzle-testbed-frontend`, image `zsuffazs/processpuzzle-testbed-frontend`, Sonar key `processpuzzle_testbed_frontend`. The npm package stays `@processpuzzle/testbed` so its version history and release tags survive. |
| Tenant application | `apps/processpuzzle-ui`, container `processpuzzle-ui`, image `zsuffazs/processpuzzle-ui`, Sonar key `processpuzzle_processpuzzle_ui` | `apps/processpuzzle-biz-frontend`, container `processpuzzle-biz-frontend`, image `zsuffazs/processpuzzle-biz-frontend`, Sonar key `processpuzzle_biz_frontend`, npm package `@processpuzzle/processpuzzle-biz-frontend` (never published, so nothing to preserve), e2e project `processpuzzle-biz-e2e`. A rename only — the repurposing below is still outstanding. |
| Tenant realm client id | `processpuzzle-ui` | `processpuzzle-biz` — `keycloak.admin.tenant-client-id` and the SPA's `AUTH_SERVICE_CONFIG.clientId` must agree, so both moved together. Realms provisioned before this hold the old client and need a `down -v` reset locally. |

> The `zsuffazs/*` image coordinates above record what the rename produced at the time. Every image
> has since moved to `ghcr.io/zszs/*` and Docker Hub is no longer published to — see
> [`docs/build-deploy-strategy.md`](build-deploy-strategy.md) §9.

### Still to do

| Area | Today | Target |
| --- | --- | --- |
| Testbed self-service roles | Do not exist | A registered user may grant themselves roles |
| `processpuzzle-biz-frontend` | Tenant org-admin surface; reads an orgKey path segment, still calls `testbed-backend`. Now in the private repository, unchanged | Public site + onboarding, no Keycloak, no platform backend |
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

## Open question: customer tenants

Provisioning a customer creates a realm per organization (`KEYCLOAK_ADMIN_TENANT_REDIRECT_URI`,
`TenantAuthenticationManagerResolver`), so a customer already looks like a fourth stack — but whether
each customer gets its own database, bucket prefix and subdomain, or whether customers share one
multi-tenant deployment, is **deliberately not decided here**. The three stacks above are internal and
fixed; customer tenants are created at run time, and applying the one-deployment-per-stack rule to
them has consequences (provisioning becomes infrastructure work, not an API call) that deserve their
own decision.

Until that decision is made, treat the existing tenant provisioning as unchanged, and do not extend
the table above to cover it.
