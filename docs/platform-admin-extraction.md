# Extracting platform-admin into the private processpuzzle-biz repository

`platform-admin` is commercial: tenant provisioning, plans, subscriptions, invoices and the staff
surface that deletes tenants. It belongs in a private repository, and this document is the plan for
getting it there without leaving the public repository unable to build, test or deploy.

> **Status.** Planned 2026-09-04; **Phases 0, 1 and 3 are complete** as of the same day. Phase 0's
> three inbound edges were cut and its gate is clean. The directories then moved out, and Phase 3
> deregistered them here: Maven modules and dependencies, the `generate-platform-admin-api`
> execution, the Nx release/tsconfig/script/`implicitDependencies` entries, six workflows, the
> compose services and Dockerfiles, the devcontainer ports and the orphaned `platform-admin:`
> configuration. 0.4 (extracting `auth-backend`) is still postponed to the start of Phase 2, and
> **Phase 2 is the remaining work** — it is the private repository's, and it is where
> `processpuzzle-admin-backend` and `processpuzzle-biz-backend` get written.
>
> Two consequences deliberately left standing rather than papered over: `docker-compose-prod.yaml`
> no longer publishes port 80 or reverse-proxies `/api/`, since the two frontends that did have
> moved (the file names both gaps at the point they bite); and the `processpuzzle-admin` and
> `processpuzzle-biz` realms, databases and bucket prefixes stay declared here as shared
> infrastructure, per strategy §8, so this repository still knows stacks #2 and #3 exist without
> building an application for either.
>
> Decisions taken: `processpuzzle-admin-frontend` and
> `processpuzzle-biz-frontend` move with it; `org-admin` stays public. Prerequisite reading:
> [Build & deployment strategy](build-deploy-strategy.md) for the two-repo layout this serves,
> [Backend library dependencies](backend-library-dependencies.md) for the edges being cut, and
> [Application stacks](application-stacks.md) for what stack #3 consists of.

## What moves and what stays

Per the [build & deployment strategy](build-deploy-strategy.md), the private repository ends up with
**four applications and libraries of its own** — it is not merely a home for platform-admin.

| Moves to processpuzzle-biz | Stays public |
| --- | --- |
| `libs/java-shared/platform-admin-backend` | `libs/java-shared/org-admin-backend` |
| `libs/js-shared/platform-admin-frontend` | `libs/js-shared/org-admin-frontend` |
| `apps/processpuzzle-admin-frontend` | `apps/processpuzzle-testbed-frontend` |
| `apps/processpuzzle-biz-frontend`, `apps/processpuzzle-biz-e2e` | `apps/processpuzzle-testbed-backend`, `apps/processpuzzle-testbed-e2e` |
| `api-contracts/src/main/resources/platform-admin-api.yaml` + its generator execution | the other twelve API specs |
| `platform-admin-backend/src/main/resources/default-organizations/`, `default-plans/` | every other feature's seed directory |
| the `processpuzzle-admin` and `processpuzzle-biz` stacks' app services, Dockerfiles, CORS origins | the shared infrastructure definition (per strategy §8) and the testbed stack |

Two things are **new work in the private repository**, not moves: `processpuzzle-admin-backend` and
`processpuzzle-biz-backend` (below), and the `subscription-backend` / `subscription-frontend`
libraries that do not exist anywhere yet.

`processpuzzle-core` keeps `core.tenancy` and `core.identity` in the public repository. They are
infrastructure, not commercial: `OrganizationGuard`, `TenantRoles` and `KeycloakAdminClient` are
what org-admin needs to stay behind, and the private repository reaches them through the submodule
like every other platform library.

## The consequence that is easy to miss

`tools/docker/docker-compose-ci.yaml` builds **one backend image and runs it twice** —
`testbed-backend` and `admin-backend` are both `zsuffazs/processpuzzle-testbed-backend:latest`,
differing only in database, realm, bucket prefix and CORS origins. The comment in that file states
the assumption plainly: *"Both keep every feature module … the two remain the same application."*

Once platform-admin is gone from the public repository that stops being true, and the strategy
document's six-image model makes the split explicit: the private repository builds **two backends**,
neither of which exists today.

| New application | Composition | Notes |
| --- | --- | --- |
| `processpuzzle-admin-backend` | every public platform module **+** private `platform-admin` | the nearest thing to today's image; the closest starting point is a copy of `processpuzzle-testbed-backend` |
| `processpuzzle-biz-backend` | *thin* — `subscription` only, no platform core | the architecture summary is explicit that Biz consumes neither the backend platform core nor the shared UI libs |

Plan for both explicitly. Each is a new Spring Boot application (a `pom.xml`, a
`@SpringBootApplication` Modulith root, a `SecurityConfig`, an `application.yaml` and a
`ModularityTests`), not a configuration change, and together they are the single largest piece of new
work in this migration. `processpuzzle-biz-backend` is the more interesting of the two precisely
because it is thin: it is the first deployable that composes *none* of the platform, so it will
expose every place where "the application" and "the platform" are still assumed to be the same thing.

## Phase 0 — make nothing in the public repository reference it

This is the whole job. platform-admin has **no outbound dependency on any feature library**, so once
the three inbound edges are gone the directory can be moved with `git mv` and nothing breaks. Do all
of Phase 0 in the public repository, on `develop`, before anything moves.

**Running order**, which is not the numbering: **0.3** (done) → **0.2** (done) → **0.1**. Phase 0 is
then complete. **0.4** was drafted as part of Phase 0 and has been postponed to the start of
Phase 2 — it is documented here rather than there because the analysis belongs with the other
coupling work, but it is not a prerequisite for anything in Phase 0.

### 0.4 auth-backend — a library for the resource server — **POSTPONED to Phase 2**

> **Decided 2026-09-04: not now.** Do this as the *first step of Phase 2*, before
> `processpuzzle-admin-backend` is written — not as part of Phase 0. The analysis below stands and
> is worth reading before starting; only the timing changed.
>
> **Why postponed.** The whole benefit is that two more backends will need this, and today there is
> exactly one consumer. Extracting a library for one consumer against an imagined second is
> speculative, and the lift does not get harder with waiting: the package is self-contained (a
> single Javadoc cross-reference points into it), so it stays a `git mv` plus a package rename plus
> registration. That is unlike the platform-admin edges, which genuinely compounded — 24 files of
> coupling by the time anyone counted. Worse, the opt-out seam below would be designed blind:
> `processpuzzle-biz-backend` may well need no resource server at all, in which case auth-backend
> has two consumers with identical needs and the opt-out complexity disappears.
>
> **The claim that this had to precede 0.1 was wrong.** The argument was that 0.1's Organizations
> work would otherwise be written twice. It would not: the claim read in `CurrentPrincipal` and the
> check in `JwtOrganizationAccessPolicy` live in classes that move to auth-backend *wholesale*, so
> the work is carried along by the same `git mv` rather than rewritten.
>
> **The real trigger** is the second consumer, which arrives in Phase 2. Note the submodule does
> not rescue you there: an application's source is not a library, so the private repository cannot
> depend on `apps/processpuzzle-testbed-backend` however it pulls the public repository in. The one
> cost of waiting is the risk that whoever writes admin-backend copy-pastes the package out of
> expedience — which naming it here is meant to insure against.

It earns its place because the application security layer is nine generic classes sitting in an
*application*, and both private backends need the identical thing across a repository boundary.
This document previously said the private backend "inherits the same `SecurityConfig` shape",
which was wishful — that means copy-paste between two repositories, and copy-paste between
repositories drifts.

#### Scope: the organizing principle is not "Keycloak"

The tempting framing is "put all Keycloak functionality in one module". Measured against the tree,
that framing is wrong, and the measurement is what decides it:

| Candidate | Spring Security imports | Consumers | Verdict |
| --- | --- | --- | --- |
| app's `com.processpuzzle.security` | throughout | the app only | **moves** |
| `core.tenancy` (guard, policy, roles, `KnownRealms`) | **none** — plain interfaces | 62 files across base-app (32), platform-admin (23), org-admin (7), the app (5) | **stays in core** |
| `core.identity` (`KeycloakAdminClient`) | **none** — a `RestClient` | org-admin (4), platform-admin (9) | **stays in core** |

Moving `core.tenancy` would hand base-app a 32-file diff and give three feature libraries a
dependency on a Spring-Security-carrying artifact to perform an authorization check that needs no
Spring Security — the same footprint argument that keeps the resource server *out* of core, running
in reverse. `KeycloakAdminClient` is Keycloak-shaped but is an outbound REST client with a token
cache; moving it would push Spring Security onto org-admin for nothing.

So the principle is **"does it inspect the incoming request?"** and auth-backend is correspondingly
small. Nothing in core moves, which means zero churn in base-app, org-admin and platform-admin.

#### What moves, and the one class that must not

From `apps/processpuzzle-testbed-backend/.../security/` — eight classes and their tests:
`SecurityConfig`, `SecurityProperties`, `SecurityConstants`, `CurrentPrincipal`,
`TenantAuthenticationManagerResolver`, `RealmRoleConverter`, `JwtOrganizationAccessPolicy`,
`ApiSecurityErrorHandler`. The package is self-contained — the only reference to it from elsewhere in
the application is one Javadoc link in `CorsConfig`.

**`RealmRoleMembershipPolicy` stays in the application.** It implements
`com.processpuzzle.workflow.execution.usecases.outbound.RoleMembershipPort` — base-workflow's port.
In auth-backend it would make an infrastructure module compile against a feature library, which is
the same mistake in a new place. It belongs beside `BaseAppPortsConfiguration` in
`com.processpuzzle.composition`, which is where adapters for other people's ports live. It needs
nothing from auth-backend but `CurrentPrincipal`.

That is the same lesson as org-admin's `KeycloakUserDirectoryAdapter` and platform-admin's
`OrganizationRealmProvisioner`, neither of which moves either: **a feature's port adapter belongs to
the feature or to the application, never to the infrastructure module underneath both.**
auth-backend verifies who the caller is; it does not administer identities.

#### Shape

- `libs/java-shared/auth-backend`, artifact `auth-backend`, package `com.processpuzzle.auth`.
- `@ApplicationModule(type = OPEN)`, like core. Not a feature module: if it were, org-admin
  depending on it would be a feature→feature edge — exactly what 0.2 removed.
- No frontend counterpart needed; `libs/js-shared/auth` already publishes `@processpuzzle/auth`, so
  the name slots into the existing pairing convention.
- Dependencies: `processpuzzle-core`, `api-contracts`, `spring-boot-starter-oauth2-resource-server`.

#### The filter chain must be opt-out

Applications component-scan `com.processpuzzle`, so a `SecurityConfig` bean here is picked up
whether the application wants it or not — and `processpuzzle-biz-backend`, a thin public marketing
surface with anonymous browsing, wants a different chain from `processpuzzle-admin-backend`. Make the
chain conditional on a property, or expose a builder the application calls, rather than an
unconditional `@Bean`.

`@AutoConfiguration` will not solve this: `OrganizationGuard`'s Javadoc already documents that in
this workspace component scan picks such a class up *before* the auto-configuration import is
applied, which is what defeated `LoggingAspectAutoConfiguration`.

#### Registration

The new-library checklist, backend half only: root `pom.xml` (`<module>`,
`<auth-backend.version>`, `dependencyManagement`), `project.json` with `implicitDependencies`,
`tools/scripts/release-java-lib.mjs` PROJECTS map, `.github/workflows/{build,release}-auth-backend.yml`,
a `sonar-project.properties` plus the SonarCloud project, and the app's `pom.xml` +
`project.json` `implicitDependencies`. Nothing generates these and a missing one fails silently.

#### Then 0.1 lands here, not in the application

0.1's substance is Keycloak Organizations support — reading the native `organization` claim into
`CurrentPrincipal` and checking it in `JwtOrganizationAccessPolicy`. Written in the application it
would sit in the one place `admin-backend` cannot reach, and would be written twice. This is the
whole reason 0.4 goes first.

### 0.1 base-app — drop the `TenantDirectory` **adapter** — **DONE**

> **Scoped down on execution, 2026-09-04.** This section used to say "delete the port", in three
> bullets. That was wrong about the size: `TenantDirectory` is used by **six** production classes
> in base-app — `DefaultAppLoader` (the seed gate), `CreateAppDefinition`,
> `CreateModuleDefinition` and `ImportAppDefinitions` (each rejecting an unknown `orgKey` with
> `UnknownTenantException`), and `GetAppLayout` (the tenant's `defaultLocale`) — plus about ten
> test files and four `AppTestFixtures` helpers. Deleting the port is a ~16-file redesign of
> base-app.
>
> **The split does not need it.** The only platform-admin reference left in the public repository
> was the single `tenantDirectory` bean in `BaseAppPortsConfiguration`. Dropping the *adapter*
> clears the gate; deleting the *port* is a separate question about what base-app should do, and
> it can be answered at any time without blocking anything.

What was done: the `tenantDirectory` bean and its three tests in `BaseAppPortsConfigurationTest`
(existence, the two-field projection, the absent case) are gone. `app.usecase.port.TenantDirectory`
**remains** in base-app with its permissive defaults, which makes it a second `P⁰` alongside
`EntityNameRegistry`: declared, and answered by nobody.

Two behaviours changed in this deployment, and neither is a hole:

- base-app no longer refuses an unknown `orgKey` on create, import or module creation.
  `OrganizationGuard` already gates those endpoints and, with realm name equal to organization
  key, a caller can only write into its own tenant — the existence check was redundant once
  membership had been established. Same argument as 0.2's.
- `GetAppLayout` reports no tenant locale. It already handled that case, since the port's own
  default returns nothing.

`processpuzzle-admin-backend`, which unlike this application will have a tenant registry, can
declare the bean again and get both behaviours back with no change to base-app. What is covered
nowhere now is the *translation* those three deleted tests pinned; it needs covering again
wherever the bean is next declared.

Still open, and no longer on this migration's critical path:

- deleting the port and reworking the six classes, if base-app should genuinely never ask;
- `DefaultAppLoader`'s seed gate, which now admits any `orgKey` — harmless while seed files name
  only the configured stack tenant;
- `StarterAppCreator` still keys off `OrganizationProvisionedEvent`, which nothing will publish
  once platform-admin leaves the build. **Checked 2026-09-04: this is not a defect.** It was
  written up here as a Phase 3 trap and it is not one. `processpuzzle-testbed-apps.yaml` seeds the
  `demo` app and `DefaultAppLoader` keeps loading it, while the starter definition is a separate,
  deliberately empty app with id `app` that **nothing references** — no e2e, no frontend, no test
  outside `StarterAppCreatorTest`. After the move testbed loses an empty row nobody reads, and
  the class stays correctly in base-app: dormant in the public repository, live in
  `processpuzzle-admin-backend`, where platform-admin does publish the event.

  A first-touch trigger *is* needed, but for the tenancy model rather than for the split: a
  developer who self-registers gets an `orgKey` with no seed file and no provisioning event
  behind it, so nothing would create anything for them. That belongs with the Keycloak
  Organizations work, not here.

Verified: `processpuzzle-testbed-backend` 73 tests green, including `ModularityTests` and the full
context test.

### 0.2 org-admin — replace `FindOrganization` with a port — **DONE**

The only compile edge from a feature library. `TenantRealmResolver` asks platform-admin two things:
map an `orgKey` to a realm, and refuse an unknown or suspended tenant.

Do **not** simply read the realm from the token and drop the rest — that silently discards the
suspended-tenant refusal, which is a real capability in the commercial product. Declare a port in
org-admin's own `usecases/outbound` speaking org-admin's own types:

- the **default** answers from the caller's token (the issuer names the realm) and permits, which is
  correct for testbed, where there is no tenant lifecycle to suspend;
- the **adapter in the private repository's composition root** answers from platform-admin's
  `FindOrganization` and refuses on status, restoring today's behaviour where it matters.

As implemented — `com.processpuzzle.orgadmin.usecases.outbound.TenantRealmDirectory`, returning
`Optional<Tenant>` where `Tenant` is `(String realm, boolean administerable)`:

- **One bit, not a duplicated `OrganizationStatus`.** The plan called for step 2 of the recipe —
  duplicate the enum, translate by name. In the event a boolean was enough and better: org-admin
  refuses `SUSPENDED` and `PROVISIONING` *identically*, so the extra three constants would have been
  vocabulary org-admin does not own and cannot transition. The translation happens in the adapter,
  where the words are still the registry's.
- **The realm is returned, not assumed.** `TenantRealmResolver` already returned the registry's key
  rather than the path parameter, so that a caller could not keep using the raw segment. The port
  preserves it, which also leaves room for a registry that canonicalises a differently-cased key.
- **`UnknownOrganizationException`**, named for the condition rather than after platform-admin's
  class — which also avoids two types with one simple name in a single build. It sits beside the
  existing `UnknownRoleException`. The `organization.not-found` error id is unchanged: the id belongs
  to org-admin-api.yaml, not to the class.
- **`BY_CONVENTION` permits**, and unlike `KnownRealms` that is the usual direction — refusing would
  take org-admin out of service in exactly the deployments that have no tenant lifecycle to enforce.
  Safe here because `guard.requireDesign` runs *first* and, with realm name equal to organization
  key, a caller can only ever reach its own realm; the existence check is redundant once membership
  has been established. The two ports look alike and their defaults run opposite ways on purpose.
- `allowedDependencies` is now `{"core", "shared"}`; the `pom.xml` dependency and the
  `project.json` `implicitDependencies` entry are gone.

**One test's worth of coverage moved out of this repository.** `aSuspendedTenantIsRefused` and
`aProvisioningTenantIsRefusedToo` collapsed into one `aTenantThatIsNotAdministerableIsRefused`,
because org-admin can no longer tell the two apart — that mapping is the adapter's, and until the
adapter exists nothing tests it. Deliberate and noted in the test's own Javadoc, but it is a real
gap to close on the private side rather than a free simplification.

Verified: `org-admin-backend` 71 tests green including `OrgAdminModularityTests`, which is what
checks the narrowed `allowedDependencies`; `processpuzzle-testbed-backend` 76 green including
`ApiAdviceScopeTest` (the advice-scope invariant survives org-admin owning its own exception) and
`ModularityTests`.

### 0.3 testbed security — give the realm resolver a `KnownRealms` port — **DONE**

The edge nobody had noticed. `TenantAuthenticationManagerResolver` and `SecurityConfig` inject
`OrganizationRepository` so that `isKnownRealm` can decide **which realms' JWKS a bearer token may be
validated against** — this deployment's own stack realm, or any existing organization key.

As implemented:

- **`com.processpuzzle.core.tenancy.KnownRealms`** — in core, not in the application, because *two*
  applications need the same interface: the public testbed backend, which wants the default, and the
  private admin backend, which implements it over the organization registry. An interface duplicated
  across two repositories drifts.
- **The port answers only the *additional*, tenant-owned realms.** The deployment's own stack realm
  is configuration, is checked by the resolver before the port is consulted, and stays trusted even
  with no bean present. An adapter author must therefore *not* return the stack realm from
  `isKnown` — it is not the port's business.
- **`KnownRealms.NONE` denies**, and this is the one port default in the platform that does. Every
  other one permits, on the principle that a library which cannot answer must not answer "no"; here a
  permissive default would accept a token signed by a key from an arbitrary realm, which is not a
  degraded answer but a broken one. For a single-realm stack `NONE` is also the *complete* answer
  rather than a fallback, which is why testbed wires no adapter and loses nothing.
- The per-request cache and the prefix gate are unchanged — they are what stop the class being a
  denial-of-service amplifier, and that reasoning is unaffected by where the realm list comes from.
- The private repository wires the platform-admin-backed adapter, which is where multi-realm
  resolution is actually needed.

Verified: `TenantAuthenticationManagerResolverTest` (10 tests, one new pinning the deny-by-default),
`KnownRealmsTest` in core, and the full `processpuzzle-testbed-backend` suite including
`ModularityTests` — 76 and 75 green. `com/processpuzzle/security` no longer names platform-admin at
all; `BaseAppPortsConfiguration` is the only remaining reference in the application, which is 0.1.

`SecurityProperties.platformAdminAuthority` and `OrganizationGuard.requirePlatformAdmin()` are just a
role name and a policy method; both stay. After the move the public repository's `/platform/**`
filter rule matches nothing, which is harmless — leave it, since the private backend inherits the
same `SecurityConfig` shape.

### Phase 0 gate

Two checks, both cheap, and neither is optional:

```bash
# 1. No Java outside platform-admin's own directory IMPORTS it. Match imports rather than the bare
#    package name: api-contracts/target holds generated com.processpuzzle.platformadmin.* DTOs (that
#    is Phase 1's business, not Phase 0's), and a dozen Javadoc paragraphs across core, base-app and
#    org-admin discuss the module by name on purpose. Neither is an edge.
grep -rn "^import com\.processpuzzle\.platformadmin" apps libs --include=*.java   | grep -v "libs/java-shared/platform-admin-backend" | grep -v "/target/"

# 2. No module still declares it as an allowed dependency. Exclude platform-admin's own
#    package-info: its path contains "platformadmin", so a naive grep matches its own
#    declaration of {"core", "shared"} and reads as a false positive.
grep -rn 'platformadmin ::' apps libs --include=package-info.java \
  | grep -v "libs/java-shared/platform-admin-backend"

# 3. The module graph still validates.
"$MAVEN_HOME/bin/mvn.cmd" test -pl apps/processpuzzle-testbed-backend -Dtest=ModularityTests
```

The first two must return nothing. Only then is the move mechanical.

**As of 2026-09-04 both return nothing: Phase 0 is complete.** The public repository's Java names
platform-admin only in Javadoc prose and in `api-contracts`' generated DTOs, which is Phase 1's
business. The Maven and Nx dependency edges from the *application* remain on purpose, and are
deregistered in Phase 3, so that platform-admin keeps working in testbed until it actually moves.

## The frontend half

Phase 0 was entirely backend. The split also moves `platform-admin-frontend`,
`processpuzzle-admin-frontend`, `processpuzzle-biz-frontend` and `processpuzzle-biz-e2e`, so the same
question applies: does anything staying behind depend on something leaving?

**Checked 2026-09-04 — it does not.** No project outside `platform-admin-frontend` and
`processpuzzle-admin-frontend` imports `@processpuzzle/platform-admin`, and
`processpuzzle-testbed-frontend` names neither admin nor biz anywhere. The moving projects import
only libraries that stay, which is the direction the submodule supports:

```bash
# must return nothing but the two moving projects themselves
grep -rn "@processpuzzle/platform-admin" --include=*.ts --include=*.json --include=*.html apps libs \
  | grep -v node_modules | grep -v /reports/
```

There is therefore no frontend equivalent of Phase 0 to do. The frontend work is all Phase 3
deregistration — with one exception.

### `processpuzzle-biz-frontend` is not yet the application the strategy describes

The [build & deployment strategy](build-deploy-strategy.md) and the
[architecture summary](processpuzzle-architecture-summary.md) both describe Biz as a thin marketing
site: *"almost no shared surface; marketing pages + a thin subscription form"*, explicitly **not** a
consumer of the shared UI libraries. The application in the tree is not that. It imports eight
`@processpuzzle/*` entry points — `auth`, `auth/domain`, `auth/feature`, `base-entity`,
`base-widget`, `org-admin`, `test-util`, `util` — because it is the former `processpuzzle-ui`,
renamed but not repurposed. `docker-compose-ci.yaml` admits as much in a comment: biz-frontend
*"still calls this instance"* — the testbed backend — *"incoherent with the target state, and
deliberately left so until that application is repurposed."*

So moving it as-is relocates a full-featured platform application, pointed at the wrong backend, into
the private repository, where it would then have to be gutted. Two orders are available and the
choice is worth making deliberately:

- **Repurpose first, in the public repository.** The gutting diff stays reviewable in the open, and
  the private repository starts with the thin application the strategy describes. Costs a round of
  work before the split can finish.
- **Move first, repurpose privately.** The split completes sooner; the private repository inherits
  six public library dependencies and a `docker-compose` entry pointing at testbed-backend, and the
  repurposing happens where fewer people can see it.

Either way `processpuzzle-biz-backend` does not exist yet, so biz-frontend has no correct backend to
point at until Phase 2 creates one. That is the real constraint: **biz-frontend cannot be finished in
either repository until biz-backend exists**, which argues for moving it and repurposing it alongside
its own backend.

`processpuzzle-biz-e2e` needs no decision — it is a bare scaffold, one `example.spec.ts` against
`localhost:4200`, importing nothing.

## Phase 1 — split the contract

`platform-admin-api.yaml` and its generator execution (`api-contracts/pom.xml`, execution id
`generate-platform-admin-api` → `com.processpuzzle.platformadmin.api` / `.model`) move to the private
repository, which needs its own `openapi-generator-maven-plugin` configuration.

The one trap: a spec in the private repository cannot `$ref` across into the public
`shared-api.yaml`, and cross-file `$ref` is not something this workspace does even within one repo.
Use `schemaMappings` to point `ErrorResponse` and `ImportResult` at the
`com.processpuzzle.shared.model` types the submodule's `api-contracts` already generates, rather than
duplicating the schemas. Duplication is the normal fallback here; mapping works because those types
are on the classpath either way. Keep the specs on OpenAPI 3.0.3 — the 3.1 code path still fails on
Windows.

## Phase 2 — move

Order matters only in that the private repository must build before the public one drops anything.

1. **Extract `auth-backend` first** (see [0.4](#04-auth-backend--a-library-for-the-resource-server--postponed-to-phase-2)),
   in the *public* repository, before writing any private application. This is the moment its second
   consumer appears, and the alternative is copy-pasting the resource server across a repository
   boundary.
2. Create `processpuzzle-biz`, private. Copy in the moving directories with history if you want it
   (`git filter-repo --path`), or plainly if you do not.
3. Wire the coupling the strategy document specifies: `processpuzzle` as a **git submodule**, built
   from source, *not* published artifacts. That has a consequence worth stating, because it is the
   opposite of what per-lib versioning would suggest — the private repository pins a **submodule
   SHA**, so "which version of base-entity does Admin run" is answered by a commit rather than a
   version range, and a public-repo change reaches Admin only when someone bumps the pointer. Decide
   who bumps it and how often; an unattended submodule is how an admin deployment silently ends up
   six weeks behind the platform.
4. Add `processpuzzle-admin-backend` and `processpuzzle-biz-backend` (see above), plus the
   composition root holding the two adapters Phase 0 created — `KnownRealms` and org-admin's tenant
   port. Only Admin needs them; Biz composes no platform module that asks.
5. Build and test the private repository green, including a `ModularityTests` of its own for
   `processpuzzle-admin-backend`.
6. Only then run Phase 3 in the public repository.

**No public project may depend back on the private repository.** Phase 0 is what guarantees this,
and the Phase 0 gate is what proves it.

## Phase 3 — deregister in the public repository

Nothing generates these entries and a missing one fails silently — no CI job, no release path, or an
unresolvable import. This is the new-library checklist run in reverse, with the specific lines:

**Maven**
- `pom.xml` — the `<module>` entry, the `<platform-admin-backend.version>` property, the
  `dependencyManagement` block
- `apps/processpuzzle-testbed-backend/pom.xml` — the dependency
- `libs/java-shared/api-contracts/pom.xml` — the `generate-platform-admin-api` execution

**Nx** — note this covers **four** moving frontend projects, not two; the biz entries are easy to
miss because the application was renamed rather than added.
- `apps/processpuzzle-testbed-backend/project.json` — `implicitDependencies`
- `libs/java-shared/org-admin-backend/project.json` — already done in 0.2
- `nx.json` — `release.projects` entries for `platform-admin-frontend` **and**
  `processpuzzle-biz-frontend`
- `tsconfig.base.json` — the `@processpuzzle/platform-admin` path
- `package.json` — thirteen scripts: `config-env-`/`build-`/`lint-`/`test-` for
  `platform-admin-backend` and `platform-admin-frontend`, the same four for
  `processpuzzle-biz-frontend` plus its `serve-`, and `build-`/`lint-`/`test-` for
  `processpuzzle-admin-frontend`
- `tools/scripts/release-java-lib.mjs` (`platform-admin-backend`) and `release-js-lib.mjs`
  (`platform-admin-frontend`, `processpuzzle-biz-frontend`) — the `PROJECTS` map entries

**CI**
- delete `.github/workflows/{build,release}-platform-admin-{backend,frontend}.yml`,
  `build-processpuzzle-admin-frontend.yml` and `build-processpuzzle-biz-frontend.yml`
- remove `libs/js-shared/platform-admin-frontend/**` from any remaining workflow's path filters

**Sonar** — delete the three `sonar-project.properties` files with the moving directories, and delete
the corresponding projects through the admin API using the token at the repository root.

**Docker / infrastructure**
- `docker-compose-ci.yaml` and `docker-compose-prod.yaml` — the `processpuzzle-admin-frontend`,
  `admin-backend` and `processpuzzle-biz-frontend` services
- `tools/docker/processpuzzle-admin-frontend/` and `tools/docker/processpuzzle-biz-frontend/`
- the **`processpuzzle-admin` and `processpuzzle-biz` realm imports stay**, with `keycloak-init`.
  Strategy §8 resolves this: the shared infrastructure definition lives in the public repository, and
  a realm is infrastructure rather than something a backend provisions for itself. So the public repo
  keeps declaring realms for stacks it no longer builds an application for. That is intentional but
  worth naming, since it is the one place where the public repository still knows stack #3 exists.
- `PLATFORM_ADMIN_CLIENT_SECRET` stays — it is `core.identity`'s Keycloak admin client secret, named
  after the client, and org-admin still needs it.

**Beyond deregistration.** Strategy §§4–5, 9 replace the deployment model wholesale, and that is a
separate exercise from this migration: per-app Coolify Application resources instead of one compose
file, GHCR instead of `zsuffazs/*` on Docker Hub, and `sha-<commit>` → `:stage` → `:prod` promotion
instead of `:latest`. Do not entangle the two — extract first against the current pipeline, then
re-platform. `docker-compose-ci.yaml` should survive either way; it is the CI topology, not the
deployment.

## What the public repository looks like afterwards

Twelve backend libraries become eleven; three frontend applications become one, and one e2e project
of two remains. The remaining feature libraries have **no compile dependencies on one another
except** `base-state → base-entity` and `base-workflow → base-state`/`base-rule`, all three of which
are already ports awaiting only the adapter relocation described in the dependency matrix. Tenancy in
the public repository reduces to one idea: an orgKey the token names is a valid orgKey, and
`core.tenancy` enforces that the path segment agrees with it.

## Risks

- **The two new private backends are real work.** Everything else here is deletion and configuration;
  those are new applications. Do not schedule the migration as if Phase 3 were the end of it.
- **The private repository inherits no e2e coverage worth the name.** `processpuzzle-biz-e2e` is a
  scaffold — one `example.spec.ts` against `localhost:4200` — and there is no admin e2e project at
  all. Whatever `@processpuzzle/e2e-testing` gives the testbed has to be re-established there against
  two apps that have never had it, and the submodule is what makes that library available.
- **Phase 0.3 is the sequencing risk.** Multi-realm token validation is a security-relevant path,
  and it is currently the only consumer of platform-admin outside the composition root. Land and
  test it separately from the rest of Phase 0.
- **The admin stack has no CI coverage in either repository during the transition.** The public
  repository loses `build-processpuzzle-admin-frontend.yml` before the private repository has an
  equivalent unless the phases overlap deliberately.
- **The demo orgKey gate.** `DefaultAppLoader` currently refuses seeds for unknown tenants. Get its
  replacement right before deleting `TenantDirectory`, or a testbed database reset comes back up with
  no seeded content and no error explaining why.
