# Architecture review: base-app-backend

- **Slice code:** BAP
- **Paths in scope:** `libs\java-shared\base-app-backend`
- **Reviewed at commit:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`
- **Date:** 2026-10-10
- **Tools run:** Static review using IDE directory/source inspection, scoped ripgrep, PowerShell read-only source inspection and file reads. Read `AGENTS.md`, the rubric and relevant README intent. Inspected Modulith, persistence, validation and route-use-case tests; did not execute tests, builds, ArchUnit or Modulith verification. No Nx configuration was needed or read.
- **Calibration:** Reviewer/owner calibration was not performed. Effort estimates are provisional; rubric scales and effort points are unchanged.

## Summary

The implementation is substantially beyond the README's scaffold description: it owns draft/published application graphs, module definitions, validation, imports and runtime projections. Feature-level boundaries are deliberately constrained through tenant, rule and entity-registry ports, but internal use cases depend back on inbound adapters. The principal debts are inconsistent route metadata semantics, deliberate last-write-wins editing without conflict detection, and a REST-shaped application layer. These are implementation/design findings, not penalties for unfinished scaffolding.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BAP-MET-01 | Publishable orphan routes are rejected by runtime resolution | MET | 3 | 2 feature | M | High | 3.0 |
| BAP-PER-01 | Whole-graph editing has no stale-write boundary | PER | 5 | 2 feature | L | High | 2.5 |
| BAP-LAY-01 | Application use cases depend on their inbound representation | LAY | 3 | 1 slice | L | High | 0.8 |

## Findings

### BAP-MET-01 — Publishable orphan routes are rejected by runtime resolution

- **Category:** MET
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\service\AppDefinitionValidator.java:476` — `reportOrphanRoutes` classifies unreferenced routes as warnings; lines 487–491 explicitly describe them as addressable by URL.
  - `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\PublishAppDefinition.java:53` — publishing rejects only blocking validation problems.
  - `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\GetRouteDefinition.java:55` — resolution rejects an existing route unless a visible navigation node reaches it.
  - `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\service\NavVisibilityFilter.java:71` — reachability is exclusively a walk of navigation entries.
- **Why it is debt:** Authoring and runtime interpret the same route metadata differently: an unlinked route can be saved and published with only a warning, but the route endpoint always returns not-found for it. This couples authorization to menu placement while validation promises URL addressability, forcing the feature's designer/runtime consumers to learn an undocumented distinction. The problem is not that hidden routes should automatically be authorized; it is the absence of one consistent reachability policy.
- **Suggested refactoring:** Make navigation-only reachability explicit and reject orphan routes at publication, retaining warnings during draft authoring. Share that policy between validation and resolution, and add a test spanning validation, publication and route lookup for an unlinked route. If independent deep links are required instead, design explicit route authorization metadata before relaxing the runtime check.
- **Depends on / blocks:** none

### BAP-PER-01 — Whole-graph editing has no stale-write boundary

- **Category:** PER
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.5
- **Evidence:**
  - `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\domain\AppDefinition.java:29` — the entity separates editorial revision from `@Version` but deliberately supplies no independent optimistic-locking column; the comment explicitly states designers are last-write-wins.
  - `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\domain\AppDefinition.java:124` — `replaceDraft` replaces the entire graph and increments a plain revision counter.
  - `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\UpdateAppDefinition.java:52` — update loads the current entity, validates the incoming graph and saves it without an expected revision comparison.
  - `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\ImportAppDefinitions.java:94` — importing an existing definition also replaces its whole draft.
  - `libs\java-shared\base-app-backend\src\test\java\com\processpuzzle\app\domain\AppDefinitionPersistenceTest.java:133` — the inspected publication/edit test exercises sequential snapshot preservation, not competing or stale edits.
- **Why it is debt:** This is a flaw in the explicitly chosen last-write-wins design, not an inference from a missing annotation alone. A second designer can submit a graph based on an older read after another edit completes; the use case accepts it and silently replaces the newer work, and a display revision counter does not prevent that loss. Protecting authored metadata needs both a client-visible precondition and a persistence concurrency boundary, separate from the editorial counter whose publishing semantics must remain unchanged. The feature-wide blast includes the editing contract and its clients.
- **Suggested refactoring:** Introduce an independent persistence lock version and a client expected-revision/ETag precondition for whole-definition replacement. Define explicit conflict behavior for imports and module edits, return a conflict response rather than silently overwriting, and preserve the existing editorial revision semantics. Include migration/defaulting, stale-client tests and competing-transaction persistence tests in the change.
- **Depends on / blocks:** none

### BAP-LAY-01 — Application use cases depend on their inbound representation

- **Category:** LAY
- **Criticality:** 3
- **Blast radius:** 1 slice
- **Effort:** L
- **Confidence:** High
- **Priority score:** 0.8
- **Evidence:**
  - `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\UpdateAppDefinition.java:3` — the use case imports inbound `AppMapper` and accepts generated `AppDefinitionInput` at line 52.
  - `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\PublishAppDefinition.java:53` — publication maps a stored domain entity back to an API model to run validation.
  - `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\service\AppDefinitionValidator.java:3` — structural validation operates on generated contract types rather than the existing domain graph.
  - `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\ImportAppDefinitions.java:7` — import depends on inbound mapper and inbound YAML DTO; lines 45–47 and 68 perform format decoding inside the use case.
  - `libs\java-shared\base-app-backend\src\test\java\com\processpuzzle\app\AppModularityTests.java:16` — the inspected architecture test verifies module structure, not the intra-module dependency direction.
- **Why it is debt:** Dependencies run from the application layer back into its inbound adapter, contrary to the hexagonal intent. Even publishing an already persisted graph requires the REST representation and mapper, so contract-generator or serialization changes reach validation and orchestration instead of remaining at an adapter boundary. YAML import repeats that dependency rather than presenting another adapter to the same application command.
- **Suggested refactoring:** Define application-owned command records around the graph and header values; map REST and YAML inputs into those commands in inbound adapters. Validate the domain/application representation directly, eliminating the publication domain-to-DTO round trip. Add an architectural rule forbidding use-case/domain dependencies on inbound packages and generated wire models.
- **Depends on / blocks:** none

## Out-of-scope observations

- Host identity adapters, core guard implementation, generated OpenAPI source and frontend consumers were not inspected. Their deployment behavior and contract changes are unscored.
- `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\package-info.java:15` documents host-provided adapters; this review does not claim those external implementations are present or absent.

## Strengths worth keeping

- `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\package-info.java:41` limits module dependencies to core/shared; lines 15–24 document feature-owned outbound ports rather than feature-internal calls.
- `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\FindAllAppDefinitions.java:44` composes the tenant predicate with additional filters using AND and checks access before querying.
- `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\domain\AppDefinition.java:132` keeps a published graph alongside the draft; `libs\java-shared\base-app-backend\src\test\java\com\processpuzzle\app\domain\AppDefinitionPersistenceTest.java:133` explicitly tests sequential publication/edit separation.
- `libs\java-shared\base-app-backend\src\main\java\com\processpuzzle\app\usecase\service\AppDefinitionValidator.java:82` combines structural validation and tenant rule evaluation rather than embedding governance conventions in each write.
- `libs\java-shared\base-app-backend\src\test\java\com\processpuzzle\app\AppModularityTests.java:19` supplies a module verification test, inspected but not executed here.
