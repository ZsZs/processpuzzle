# ProcessPuzzle :: Org Admin Backend

![Build and Test](https://github.com/ZsZs/processpuzzle/actions/workflows/build-org-admin-backend.yml/badge.svg)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=processpuzzle_org_admin_backend&metric=alert_status)](https://sonarcloud.io/summary?id=processpuzzle_org_admin_backend)
[![Maven Central](https://img.shields.io/maven-central/v/com.processpuzzle/org-admin-backend?style=flat)](https://central.sonatype.com/artifact/com.processpuzzle/org-admin-backend)

Server-side companion of [`@processpuzzle/org-admin`](../../js-shared/org-admin-frontend/README.md):
user and role management **inside one organization**, for that organization's own administrator.

## There is no user table

**Keycloak is the system of record.** This module persists nothing about users — it proxies the
Keycloak Admin API for the tenant's realm through one outbound port:

- `UserDirectoryPort` — list/search users (paged), invite, enable, disable, delete, list realm roles,
  read/assign/unassign a user's roles.
- `KeycloakUserDirectoryAdapter` implements it, reusing `platform-admin-backend`'s admin client and
  `keycloak.admin.*` properties.
- `NoOpUserDirectoryPort` is the fallback when no Keycloak is configured, so the library and its
  tests run without one.

Consequences worth stating: a user granted a role here sees it in their token on **next login**, not
immediately; and because Keycloak owns the data, deleting an organization does not orphan user rows
here — there are none.

## Why it depends on platform-admin

Resolving a request means answering "which realm is this?", and refusing unknown or suspended
tenants before touching the directory. That is `FindOrganization`'s job, so the module declares
`allowedDependencies = {"core", "shared", "platformadmin :: usecase"}` and reaches nothing else. The
edge is one-way: `platform-admin` knows nothing about users.

## Technologies

- **Java 25**
- **Spring Boot 4** (Spring Modulith application module `orgadmin`)
- **Project Lombok**
- **Maven** for build and dependency management
- **Nx** for monorepo task execution

## Development

```powershell
npm exec nx build org-admin-backend
npm exec nx test org-admin-backend
npm exec nx lint org-admin-backend
```

## License

Apache License 2.0
