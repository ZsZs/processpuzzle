# Architecture review: base-event-backend

- **Slice code:** BEV
- **Paths in scope:** `libs\java-shared\base-event-backend`
- **Reviewed at commit:** `ada4dab69e96143cdfdab3d5724b58692c22eee1`
- **Source provenance:** Workspace HEAD was `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`; scoped Git comparisons confirmed no tracked source differences between this slice, HEAD and the requested review commit.
- **Date:** 2026-10-10
- **Tools run:** Static review only: scoped discovery, ripgrep, file reads and numbered PowerShell reads. Inspected module/Maven boundaries, catalog aggregate/repository, CRUD/import use cases and endpoint, both event bridges, and module, listener, persistence and tenant-erasure tests. No builds, tests, Modulith verification or publication-registry experiments executed.
- **Calibration:** All effort estimates are provisional; rubric section 9 calibration has not been performed.

## Summary

This is a compact event-catalog module with appropriately limited declared dependencies and a comprehensible metadata matching model. Catalog listeners deliberately republish in new transactions, and thrown occurrences preserve their identity and correlation. The main architectural risk is destructive catalog evolution: replacement/import/deletion do not protect definitions already used by consumers. A second issue is that tests for the central durable republishing path bypass Spring transactions and the publication registry. The existing persistence and real-transaction erasure tests are valuable but do not establish that different delivery guarantee.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BEV-TST-01 | Durable catalog bridges are tested as plain method calls | TST | 3 | 2 feature | M | High | 3.0 |
| BEV-MET-01 | Catalog evolution can invalidate live subscriptions | MET | 4 | 2 feature | L | High | 2.0 |

## Findings

### BEV-TST-01 — Durable catalog bridges are tested as plain method calls

- **Category:** TST
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\adapter\inbound\PlatformEventCatalogListener.java:44` — correctness depends on after-commit delivery and a new transaction publishing a second durable event.
  - `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\adapter\inbound\ThrownEventCatalogListener.java:44` — has the same transactional bridge and republishes at line 61.
  - `libs\java-shared\base-event-backend\src\test\java\com\processpuzzle\event\adapter\inbound\PlatformEventCatalogListenerTest.java:33` — constructs the listener directly with mocked repository/publisher; line 57 calls it directly.
  - `libs\java-shared\base-event-backend\src\test\java\com\processpuzzle\event\adapter\inbound\ThrownEventCatalogListenerTest.java:36` — likewise constructs a plain listener; line 56 bypasses transaction/event infrastructure.
  - `libs\java-shared\base-event-backend\src\test\java\com\processpuzzle\event\domain\EventDefinitionPersistenceTest.java:16` — tests catalog JPA mapping/finders, not bridge delivery or retry.
- **Why it is debt:** Plain method tests establish matching and payload shape but cannot detect a lost second-hop publication, delivery after rollback, or incorrect completion/retry behavior. Those guarantees are this module's central integration responsibility, and regressions affect workflows and other catalog subscribers while all these tests can remain green. This is a missing test boundary, not a claim that `REQUIRES_NEW` is incorrect or that production durability has failed.
- **Suggested refactoring:** Add a focused Spring/Modulith integration fixture with the actual publication registry, committed and rolled-back source transactions, and a downstream listener that can fail once. Assert committed second-hop persistence, absence on rollback, incomplete-publication retry, and stable occurrence/correlation on replay. Reuse the existing real-transaction test pattern while keeping fast matching unit tests.
- **Depends on / blocks:** none

### BEV-MET-01 — Catalog evolution can invalidate live subscriptions

- **Category:** MET
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.0
- **Evidence:**
  - `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\usecase\DeleteEventDefinition.java:23` — deletes directly; its documentation at line 10 explicitly permits deletion while a workflow names the definition.
  - `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\usecase\ReplaceEventDefinition.java:35` — replaces binding/kind and validates only the resulting definition.
  - `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\usecase\ImportEventDefinitions.java:60` — upsert replaces existing definitions without a consumer compatibility check.
  - `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\domain\EventDefinition.java:135` — kind, subject type, action and state are all mutable under the same ID.
  - `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\adapter\inbound\ThrownEventCatalogListener.java:47` — missing or now-SYSTEM definitions cause a thrown occurrence to be logged and dropped; lines 53–63 derive delivery kind from the current catalog.
- **Why it is debt:** An author can invalidate an already configured subscription or change a MESSAGE into SIGNAL semantics without changing the referenced ID. The listener documents the consequence itself: a once-valid thrown event is dropped when the catalog changes, while raw fact matching also uses only current definitions. Avoiding compile-time knowledge of subscribers is desirable, but does not remove the need for a metadata compatibility/retirement contract; the impact extends to workflows consuming these IDs.
- **Suggested refactoring:** Introduce a narrow consumer-usage/compatibility port supplied by the host, or immutable event-definition revisions with retirement rather than immediate deletion. Guard delete, replacement and import consistently, including kind changes and active references; retain referenced revisions until consumers migrate. Keep the event module independent of workflow repositories and add change-while-subscribed tests.
- **Depends on / blocks:** BEV-TST-01 — use bridge integration coverage when changing catalog delivery semantics; coordinate revision identity with BWF-MET-01 without making the modules compile against each other.

## Out-of-scope observations

- Unscored consumer corroboration: `libs\java-shared\base-workflow-backend\src\main\java\com\processpuzzle\workflow\definition\domain\WorkflowValidator.java:246` validates catalog existence/kind when saving workflow metadata. Consumer receipts and runtime snapshots are owned by the BWF findings, not scored again here.
- The host's publication-registry configuration and shared event constructors/serialization are outside this slice. No assertion is made about their retry scheduling, deterministic SYSTEM occurrence identity, or compatibility policy.

## Strengths worth keeping

- Small declared dependency surface: `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\package-info.java:26` permits only core/shared; `libs\java-shared\base-event-backend\src\test\java\com\processpuzzle\event\EventModularityTests.java:15` contains verification (not executed).
- Organization-scoped identity and candidate lookup are explicit: `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\domain\EventDefinition.java:44` and `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\domain\EventDefinitionRepository.java:29`. Matching also checks organization at `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\domain\EventDefinition.java:128`.
- API mapping is separated from persistence and commands: `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\adapter\inbound\EventDefinitionsEndpoint.java:52`. Replacement preserves identity and optionally enforces the client version at `libs\java-shared\base-event-backend\src\main\java\com\processpuzzle\event\usecase\ReplaceEventDefinition.java:31`.
- Tenant cleanup has genuine commit/rollback test structure rather than only mocked calls: `libs\java-shared\base-event-backend\src\test\java\com\processpuzzle\event\adapter\inbound\TenantDataCleanerTest.java:44` suspends the outer test transaction, and `libs\java-shared\base-event-backend\src\test\java\com\processpuzzle\event\adapter\inbound\TenantDataCleanerTest.java:78` exercises rollback. These tests were inspected, not run.
