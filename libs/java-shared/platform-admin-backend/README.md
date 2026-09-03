# ProcessPuzzle :: Platform Admin Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-platform-admin-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_platform_admin_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_platform_admin_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/platform-admin-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/platform-admin-backend)

Server-side companion of [`@processpuzzle/platform-admin`](../../js-shared/platform-admin-frontend/README.md),
and the **owner of the `Organization` aggregate** — the tenant itself. Everything else in the platform is
scoped by an `orgKey`; this module is what decides that an `orgKey` exists at all.

## What it owns

- **The tenant.** `Organization`, `OrganizationRepository` and `OrganizationStatus`
  (`PROVISIONING` / `ACTIVE` / `SUSPENDED`), plus the use cases over them: provision, find, find-all,
  update, delete, suspend, activate, check-key. `base-app` used to own these and is now a consumer,
  reaching them through `platformadmin :: usecase`.
- **The identity realm behind the tenant.** One Keycloak realm per organization, named after the
  `orgKey`. `IdentityRealmPort` is the seam; `KeycloakAdminAdapter` implements it over the Keycloak
  Admin REST API, and `NoOpIdentityRealmPort` stands in when no Keycloak is configured so the library
  and its tests run without one.
- **Billing.** `Plan`, `Subscription`, `UsageRecord`, `Invoice`, `InvoiceLine` and read-only queries
  over them. Deliberately our own model with **no payment provider**: nothing in this module can
  charge anyone, it only records and reports.

## Two design points worth knowing

**`PROVISIONING` is a durable state, not a transient one.** Creating a realm is a network call, so it
must not sit inside the database transaction. `ProvisionOrganization` commits the organization as
`PROVISIONING`; an `@TransactionalEventListener(AFTER_COMMIT)` handler then calls the realm port and
flips the row to `ACTIVE`. A failure therefore *leaves* the row `PROVISIONING` — visible, and
retryable — rather than rolling a tenant back out from under a realm that already exists. The handler
is annotated `@Transactional(REQUIRES_NEW)`, which is load-bearing: after-commit work in the original
transaction is silently discarded.

**Deleting a tenant cascades by event.** `DeleteOrganization` publishes `OrganizationDeletedEvent`
rather than reaching into other features' repositories. `base-app` deletes its own app and module
definitions in a `@TransactionalEventListener(BEFORE_COMMIT)` handler — same transaction, so the
writes commit with the deletion. Entity, rule, state and workflow data is still not cleaned up; those
features subscribe to the same event when they grow an organization-aware backend.

## Technologies

- **Java 25**
- **Spring Boot 4** (Spring Modulith application module `platformadmin`)
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Development

```powershell
npm exec nx build platform-admin-backend
npm exec nx test platform-admin-backend
npm exec nx lint platform-admin-backend
```

## License

Apache License 2.0
