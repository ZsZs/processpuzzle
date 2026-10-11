# Architecture review: org-admin-backend

- **Slice code:** OAD
- **Paths in scope:** `libs\java-shared\org-admin-backend`
- **Reviewed at commit:** ada4dab69e96143cdfdab3d5724b58692c22eee1
- **Date:** 2026-10-10
- **Tools run:** Read-only glob/ripgrep, source/test/documentation reads, Git HEAD/status, and `npm exec nx show project org-admin-backend --json` (successful; resolved test target runs Maven with `-am`). No tests, builds, lint, live Keycloak calls or Modulith verification executed.

## Remediation log

### OAD-OPS-01: addressed (2026-10-11)

Both adapters now share a compensating invitation workflow (`KeycloakInvitation`): create disabled,
assign membership/roles, verify the requested roles, enable, then read back the final result.
Every handled post-create provider failure attempts deletion of the account identified by the
create response. Confirmed deletion (including provider 404) permits a fresh invitation; failed
cleanup, a lost create response or a missing create id returns 409
`user.invitation-recovery-required`, with an operation correlation id and the created user id when
known. The original and cleanup failures are retained for diagnosis. Duplicate accounts are never
adopted or deleted by username.

**Design choice:** compensation with explicit operator reconciliation, not the suggested durable
invitation journal/resume protocol. This preserves Keycloak-only ownership without pretending that
remote calls are atomic or that this POST is idempotent. Process crashes and lost API responses
still require reconciliation; accounts remain disabled until provisioning is verified. The port,
exception contract, OpenAPI response and library README describe those limits and the recovery
procedure. OAD-SEC-01 and OAD-ABS-01 are unchanged.

**Validation:** `org-admin-backend:test` through Nx passed 62 focused tests across
`KeycloakInvitationTest`, both adapter tests, `OrgAdminApiExceptionHandlerTest` and
`OrgAdminEndpointsTest`. They cover every post-create failure boundary, unknown creation, failed
compensation, fresh retry after confirmed rollback, missing roles, duplicate-account isolation and
the actual HTTP recovery response with/without a known user id. `OrgAdminModularityTests` also
passed in the preceding run. The Maven reactor regenerated/compiled the OpenAPI contracts and
compiled core/org-admin. No live Keycloak or process-crash recovery test was performed.

The original assessment and evidence below are retained as the review baseline.

## Summary (review baseline)

The actual module has removed its feature dependency on platform-admin: tenant resolution and user administration are outbound ports, and core owns the reused identity client. The absence of a user database is a sound ownership choice, but it does not make a multi-call external operation atomic or safely retryable. The principal debts are partially applied invitations reported as ordinary retryable failures, shared-account mutations hidden behind a tenant-local port, and role replacement that can produce a union under concurrent requests. Metadata-driven tenant roles and centralized use-case authorization are strengths to retain rather than replace with hard-coded application roles.

Effort estimates are provisional: rubric section 9 calibration has not been performed. Static review inspected both Keycloak adapter test classes, `TenantRealmResolverTest` and `OrgAdminModularityTests`; none were executed. Adapter tests mock the admin client, so provider behavior and concurrent execution have not been demonstrated. Shared membership is a supported branch evidenced by deletion code/tests, not a claim that current production users actually belong to multiple organizations; host authorization/token interpretation and Keycloak deployment policies were outside scope.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OAD-OPS-01 | Invitation failures conceal committed external work | OPS | 3 | 2 feature | M | High | 3.0 | Addressed 2026-10-11 |
| OAD-SEC-01 | Tenant administration can mutate a shared global account | SEC | 5 | 2 feature | L | High | 2.5 | Open |
| OAD-ABS-01 | Role replacement has no concurrency contract | ABS | 5 | 2 feature | L | High | 2.5 | Open |

## Findings

### OAD-OPS-01 — Invitation failures conceal committed external work

- **Category:** OPS
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\usecases\outbound\UserDirectoryPort.java:9` — promises that a failure means the operation did not happen and is retryable as 503.
  - `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakUserDirectoryAdapter.java:99` — creates a user, then grants roles, then reads the user back as separate remote operations.
  - `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakOrganizationUserDirectoryAdapter.java:119` — creation/membership/roles/readback are separate steps; compensation covers membership failure only, not subsequent role/read failures.
  - `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakOrganizationUserDirectoryAdapter.java:142` — retrying creation of an existing username/email becomes `UserAlreadyExistsException`, rather than resuming the prior operation.
  - `libs\java-shared\org-admin-backend\src\test\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakUserDirectoryAdapterTest.java:104` — explicitly tests a missing readback after successful creation as `DirectoryUnavailableException`.
- **Why it is debt:** Keycloak owning all user state avoids a dual-write user table, but does not roll back already completed HTTP calls. A role-assignment or final-read outage can leave an enabled account while the client receives a failure; repeating the invitation can then return a duplicate conflict instead of completing the original request. This contradicts the port's failure contract and pushes recovery onto the admin UI/operator.
- **Suggested refactoring:** Define an explicit operation identity and recoverable invitation outcome, distinguishing rejected, completed and partially applied/unknown operations. Reconcile creation, membership and role steps against that identity before retrying; do not adopt an unrelated pre-existing account merely because its username matches. Extend compensation/recovery tests to each post-create failure boundary and update the failure contract to stop promising atomicity the provider does not supply.
- **Depends on / blocks:** none

### OAD-SEC-01 — Tenant administration can mutate a shared global account

- **Category:** SEC
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.5
- **Evidence:**
  - `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakOrganizationUserDirectoryAdapter.java:154` — verifies membership in the requested organization, then writes profile fields and `enabled` to the realm-global `/users/{id}` resource.
  - `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakOrganizationUserDirectoryAdapter.java:179` — deletion explicitly checks other organization memberships before deleting the global account, demonstrating that shared accounts are an anticipated case.
  - `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakOrganizationUserDirectoryAdapter.java:221` — platform-role revocation/grant uses realm-global mappings, unlike tenant-scoped role groups.
  - `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\usecases\inbound\TenantRealmResolver.java:62` — authorization establishes design rights for the requested tenant, not rights to manage a global identity shared by tenants.
  - `libs\java-shared\org-admin-backend\src\test\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakOrganizationUserDirectoryAdapterTest.java:152` — tests preserving an account belonging to another organization, but the same boundary is not applied to profile/enable changes.
- **Why it is debt:** The per-realm and per-organization adapters do not have equivalent ownership semantics despite sharing one port. For a member of two organizations, either organization's admin can disable the realm account used by both; platform role changes likewise mutate shared mappings rather than one tenant's membership. Membership-in-this-tenant proves access to that membership, not authority over every tenant using the account, so the tenant-local abstraction loses the ownership boundary precisely where it matters.
- **Suggested refactoring:** Separate organization-membership administration from global identity/profile administration in the port and authorization model. Use tenant-scoped role assignments and tenant-local suspension where the provider supports them, reserving global account mutation for an explicitly authorized identity owner. If shared membership is intentionally unsupported, enforce that invariant at every write and provisioning boundary instead of relying on current usage; test two-organization accounts for update, suspension and role changes.
- **Depends on / blocks:** none

### OAD-ABS-01 — Role replacement has no concurrency contract

- **Category:** ABS
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.5
- **Evidence:**
  - `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\usecases\outbound\UserDirectoryPort.java:64` — promises an exact replacement and explicitly says concurrent editors must not merge into a union.
  - `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakUserDirectoryAdapter.java:161` — independently reads held roles, calculates a difference, revokes and grants with separate HTTP calls.
  - `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakOrganizationUserDirectoryAdapter.java:202` — the organization adapter follows the same read/difference/write protocol, with further individual group operations at line 217.
  - `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\usecases\inbound\ReplaceOrganizationUserRoles.java:36` — validates user/role existence but supplies no expected version or serialized mutation scope to the port.
  - `libs\java-shared\org-admin-backend\src\test\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakUserDirectoryAdapterTest.java:167` — tests a sequential before/after role list using a mock, not overlapping replacements.
- **Why it is debt:** Two requests can read the same held set, both remove it, and then grant disjoint requested sets, leaving their union even though neither caller requested it. Revoking before granting prevents a transient union within one request, not the union produced by interleaved requests. Because these are permission/role assignments consumed outside this slice, this is an authorization-state integrity risk as well as a violated API promise.
- **Suggested refactoring:** Give role replacement an explicit expected-state/conflict contract and implement serialized mutation or a provider-supported atomic/conditional replacement. Coordinate across application instances, identify whether direct provider-side editors are also supported, and verify the final requested set before reporting success; a plain Java monitor is not a cross-process solution. Add deterministic overlapping-request tests for both adapters and failure tests between revocation and grant.
- **Depends on / blocks:** none

## Out-of-scope observations

- The root application security chain, token-to-tenant role interpretation and shared identity client implementation were not inspected. OAD-SEC-01 is based on the global mutation visible in this adapter, not an assertion about an unreviewed token exploit.
- Actual Keycloak single-membership policies could constrain OAD-SEC-01's triggering condition; this slice does not establish such a policy.
- The in-scope README still describes a platform-admin dependency, but actual `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\package-info.java:45` and Maven dependencies restrict this feature to core/shared. This documentation drift is not scored as a live module-boundary violation.
- There is no local event registry or user persistence to inspect here; the durability findings concern multi-call external operations, not a demand to duplicate Keycloak's user data.

## Strengths worth keeping

- `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\package-info.java:45` declares only core/shared dependencies; tenant lookup is the feature-owned `TenantRealmDirectory` port rather than a commercial-feature use case.
- `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\usecases\inbound\TenantRealmResolver.java:62` centralizes authorization before existence/status checks; `libs\java-shared\org-admin-backend\src\test\java\com\processpuzzle\orgadmin\usecases\inbound\TenantRealmResolverTest.java:35` verifies no directory lookup after denial.
- `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakOrganizationUserDirectoryAdapter.java:102` confines reads to organization members; its test at `libs\java-shared\org-admin-backend\src\test\java\com\processpuzzle\orgadmin\adapters\outbound\KeycloakOrganizationUserDirectoryAdapterTest.java:83` checks another tenant's non-member is never written.
- `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\adapters\outbound\UserDirectoryConfiguration.java:29` chooses adapter topology from configuration and uses a write-refusing fallback when identity administration is unavailable.
- `libs\java-shared\org-admin-backend\src\main\java\com\processpuzzle\orgadmin\usecases\inbound\ReplaceOrganizationUserRoles.java:43` discovers declared roles dynamically; `libs\java-shared\org-admin-backend\src\test\java\com\processpuzzle\orgadmin\OrgAdminModularityTests.java:22` verifies module declarations.
