# Architecture review: processpuzzle-core

- **Slice code:** PCR
- **Paths in scope:** `libs\java-shared\processpuzzle-core`
- **Reviewed at commit:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`
- **Date:** 2026-10-10
- **Tools run:** Scoped glob/ripgrep and source/test reads; Maven dependency and module-declaration inspection. IDE text search returned no usable matches, so scoped text inspection was used. Tests were inspected, not executed; no build or Modulith verification was run.
- **Calibration:** Owner/reviewer calibration has not been performed. Effort estimates are provisional; rubric scales are unchanged.

## Summary

Core provides useful shared infrastructure without owning feature data, and its open Modulith boundary is an explicit design choice rather than a violation. Its most significant debt is the authorization guard's treatment of missing or ambiguous policy wiring as permission to proceed. The shared identity client also loses structured failure semantics and reconstructs them from arbitrary error text. Both issues sit in reusable mechanisms, so their blast radius extends beyond a single feature; this is a static architecture assessment, not a claim that the deployed testbed is exploitable.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PCR-SEC-01 | Missing or ambiguous policy wiring silently permits access | SEC | 5 | 3 platform | M | High | 7.5 |
| PCR-ABS-01 | Identity failures lose structured status semantics | ABS | 4 | 3 platform | M | High | 6.0 |

## Findings

### PCR-SEC-01 — Missing or ambiguous policy wiring silently permits access

- **Category:** SEC
- **Criticality:** 5
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 7.5
- **Evidence:**
  - `libs\java-shared\processpuzzle-core\src\main\java\com\processpuzzle\core\tenancy\OrganizationGuard.java:36` — resolves the policy with `getIfUnique(PermitAllOrganizationAccessPolicy::new)`.
  - `libs\java-shared\processpuzzle-core\src\main\java\com\processpuzzle\core\tenancy\OrganizationAccessPolicy.java:31` — membership checks default to doing nothing; design and platform-administrator checks also default to permission.
  - `libs\java-shared\processpuzzle-core\src\test\java\com\processpuzzle\core\tenancy\OrganizationGuardTest.java:80` — the no-policy test explicitly expects design, staff and role checks to permit access.
  - `libs\java-shared\processpuzzle-core\src\main\java\com\processpuzzle\core\tenancy\OrganizationGuard.java:12` — the implementation documents that nonunique resolution falls back rather than failing startup.
- **Why it is debt:** The library intentionally supports hosts without identity, but implicit bean absence and ambiguous wiring are not reliable declarations of that intent. With multiple policy beans and no unique candidate, the same permissive fallback can replace policies a host intended to enforce; a newly added policy method is also permissive until implementations override it. This is a flaw in the default-policy design, not an objection to an explicitly selected demonstration mode. Platform blast follows from this guard being the common tenant/design/staff enforcement mechanism for feature use cases.
- **Suggested refactoring:** Require an explicit permissive-mode choice for nonsecured hosts and fail startup when restrictive-mode policy wiring is absent or ambiguous. Keep the policy interface deny-by-default or require implementations to make each authorization decision explicitly. Add real application-context tests for zero, one and multiple policy beans, a primary candidate, and the explicit demonstration mode; migrate host wiring without removing the supported unauthenticated use case.
- **Depends on / blocks:** none

### PCR-ABS-01 — Identity failures lose structured status semantics

- **Category:** ABS
- **Criticality:** 4
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 6.0
- **Evidence:**
  - `libs\java-shared\processpuzzle-core\src\main\java\com\processpuzzle\core\identity\KeycloakAdminClient.java:119` — idempotent operations suppress a wrapped failure if `mentionsStatus` accepts it.
  - `libs\java-shared\processpuzzle-core\src\main\java\com\processpuzzle\core\identity\KeycloakAdminClient.java:170` — the matcher examines the cause's message, not its HTTP response status.
  - `libs\java-shared\processpuzzle-core\src\main\java\com\processpuzzle\core\identity\KeycloakAdminClient.java:174` — any occurrence of the tolerated number anywhere in the message is sufficient.
  - `libs\java-shared\processpuzzle-core\src\test\java\com\processpuzzle\core\identity\KeycloakAdminClientTest.java:88` — the tolerance test covers a genuine matching status, not a different status or transport error whose message contains that number.
- **Why it is debt:** The shared adapter erases a machine-readable distinction and then makes an idempotency decision using unstructured diagnostics. An unrelated response body or transport message containing `404` or `409` can therefore be treated as successful convergence when that status is tolerated, even though the operation failed. The risk affects every identity adapter that uses this common tolerance operation, rather than only one HTTP endpoint; current downstream call-site exposure was not independently assessed here.
- **Suggested refactoring:** Preserve a typed optional HTTP status in the identity exception, extracting it from a response exception before wrapping; distinguish transport failures from response failures. Compare the actual status exactly when deciding idempotent success, retaining the original cause for diagnostics. Test matching statuses, different statuses containing the tolerated digits in their bodies, and transport failures containing those digits.
- **Depends on / blocks:** none

## Out-of-scope observations

- Host policy implementations and downstream identity workflows are outside this slice; these findings concern the reusable mechanism, not a proven deployment exposure.
- The core module is intentionally open (`libs\java-shared\processpuzzle-core\src\main\java\com\processpuzzle\core\package-info.java:6`). Openness alone was not scored as debt.

## Strengths worth keeping

- `libs\java-shared\processpuzzle-core\src\main\java\com\processpuzzle\core\definition\DefinitionImportParticipant.java:11` explicitly defines caller-owned transactions and deferred external side effects, making dry-run expectations visible to every participant.
- `libs\java-shared\processpuzzle-core\src\main\java\com\processpuzzle\core\i18n\TranslationBundleImporter.java:19` shares loading mechanics while leaving feature-owned persistence behind a sink.
- `libs\java-shared\processpuzzle-core\pom.xml:75` documents optional security dependencies to avoid changing the authentication behavior of applications that did not opt in.
