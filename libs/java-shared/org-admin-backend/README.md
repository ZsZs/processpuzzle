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
- `KeycloakUserDirectoryAdapter` implements it, reusing `processpuzzle-core`'s admin client and
  `keycloak.admin.*` properties.
- `KeycloakOrganizationUserDirectoryAdapter` implements the shared-realm organization topology.
- `NoOpUserDirectoryPort` is the fallback when no Keycloak is configured, so the library and its
  tests run without one.

Consequences worth stating: a user granted a role here sees it in their token on **next login**, not
immediately; and because Keycloak owns the data, deleting an organization does not orphan user rows
here — there are none.

## Tenant resolution

Resolving a request means answering "which realm is this?", and refusing unknown or suspended
tenants before touching the directory. `TenantRealmResolver` authorizes the caller and consults the
feature-owned `TenantRealmDirectory` port, supplied by the host. The module declares only
`allowedDependencies = {"core", "shared"}`; it does not depend on platform-admin.

## Invitation failure and recovery

Both Keycloak adapters use the same compensating invitation workflow. An account is created
**disabled**, without credentials and with `UPDATE_PASSWORD`. Organization membership (when
applicable) and all requested roles are assigned and read back before enabling the account.
Success is returned only after a final read confirms the enabled account and its roles.

| Outcome | API result | Client action |
| --- | --- | --- |
| Completed | 201 with the stored user | Refresh the directory |
| Duplicate username/email | 409 `user.already-exists` | Correct the request; no existing account is adopted |
| Failure before creation, or confirmed removal after a post-create failure | 503 `user-directory.unavailable` | Retry after the provider recovers |
| Unconfirmed creation or cleanup | 409 `user.invitation-recovery-required` | Reconcile before retrying |

Compensation deletes **only the account id returned by this attempt's create response**. It covers
membership, role assignment, verification, activation and final-read failures. A confirmed 404 on
deletion also means cleanup is complete. A timeout, server error or missing create id cannot prove
that creation failed; a cleanup failure cannot prove that the account was removed. These outcomes
are never presented as an ordinary retryable outage.

The recovery response includes `operationId` (also in `errorText`, so the existing frontend error
notification displays it), and `userId` when creation returned one. `operationId` correlates server
logs; it is **not an idempotency key**. A privileged identity operator should:

1. Locate the operation's log entries for the realm, directory, attempted username and confirmed id.
2. Inspect Keycloak and its administrative audit for that attempt. An unknown create must not be
   attributed to an account solely because the username/email matches. If ownership cannot be
   established, leave the account untouched and investigate rather than deleting or adopting it.
3. For a confirmed account from the failed attempt, inspect membership, roles and enabled state.
   Remove it if no subsequent legitimate use or membership makes deletion unsafe, then allow a
   fresh invitation. Otherwise resolve it explicitly with the affected administrators.

This deliberately chooses compensation and explicit operator recovery instead of introducing a
durable invitation journal/resume API. Calls are not atomic or idempotent. Process termination
during a call or loss of the API response still requires reconciliation; no automatic recovery is
claimed. Pending accounts stay disabled until provisioning has been verified, but an activation
whose response is lost may already have enabled the account. Other directory writes also remain
non-atomic: a 503 alone does not establish that they were unapplied.

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

## Self-registration

`POST /registration` (public) creates a user in the stack realm through the Keycloak Admin API. It is **off by
default**: set `processpuzzle.registration.enabled=true` (`PROCESSPUZZLE_REGISTRATION_ENABLED`). It answers 404 in
Organization mode. `processpuzzle.registration.verify-email` adds the `VERIFY_EMAIL` action (needs realm SMTP).
Requests are rate limited per client (`max-attempts` per `window`, in-memory, per instance; keyed on `X-Real-IP`).
Known limits: no captcha; a duplicate email returns 409 (user enumeration). Existing realms must have
`registrationAllowed=false` applied by hand, since realm imports are one-shot.
