# Architecture review: base-starter-backend

- **Slice code:** BSR
- **Paths in scope:** `libs\java-shared\base-starter-backend`
- **Reviewed at commit:** ada4dab69e96143cdfdab3d5724b58692c22eee1
- **Date:** 2026-10-10
- **Tools run:** Read-only IDE call hierarchy for `ImportBundle.importAuthorized`, glob/ripgrep, source/test/documentation reads, Git HEAD/status, and `npm exec nx show project base-starter-backend --json` (successful; resolved build/test/lint targets use Maven). No tests, builds, lint or Modulith verification executed.

## Summary

The importer delegates definition ownership to participant SPIs rather than importing other feature implementations, and registry access is separated from transactional application. Metadata validation, bounded downloads and provenance are concrete implemented capabilities, not empty scaffolding. Its principal architectural risk is that the destructive replacement precondition is checked outside the replacement transaction with no shared exclusion mechanism. The other finding is a test architecture gap: the transactional suite persists provenance, but its actual definition participants cannot roll back, so it does not prove the central all-or-nothing/dry-run guarantee.

Effort estimates are provisional: rubric section 9 calibration has not been performed. Static review inspected `ImportBundleTest`, its `FakeParticipant`, the relevant HTTP-registry tests and `StarterModularityTests`; none were executed. The domain package contains JPA persistence models/repositories and the use case directly coordinates them; this is pragmatic persistence coupling, not evidence by itself of harmful cross-feature coupling. Actual feature participants, instance writers and deployment-level database isolation were outside scope, so no production data-loss incident is asserted.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| BSR-PER-01 | Replacement preflight does not exclude concurrent instance writes | PER | 5 | 3 platform | L | High | 3.8 |
| BSR-TST-01 | Transaction tests cannot prove rollback of imported definitions | TST | 3 | 2 feature | M | High | 3.0 |

## Findings

### BSR-PER-01 — Replacement preflight does not exclude concurrent instance writes

- **Category:** PER
- **Criticality:** 5
- **Blast radius:** 3 platform
- **Effort:** L
- **Confidence:** High
- **Priority score:** 3.8
- **Evidence:**
  - `libs\java-shared\base-starter-backend\src\main\java\com\processpuzzle\starter\usecase\ImportBundle.java:73` — creates a transaction template without an explicit isolation or exclusion strategy.
  - `libs\java-shared\base-starter-backend\src\main\java\com\processpuzzle\starter\usecase\ImportBundle.java:121` — counts instance data before entering the `transactionTemplate.execute` at line 126.
  - `libs\java-shared\base-starter-backend\src\main\java\com\processpuzzle\starter\usecase\ImportBundle.java:137` — replacement begins by removing every participant's existing definitions.
  - `libs\java-shared\base-starter-backend\src\main\java\com\processpuzzle\starter\usecase\ImportBundle.java:175` — destructive removal is delegated across all registered definition kinds.
  - `libs\java-shared\base-starter-backend\src\test\java\com\processpuzzle\starter\usecase\ImportBundleTest.java:78` — the instance probe reads a plain test counter; the refusal test at line 210 exercises an already-nonempty organization, not a concurrent write.
- **Why it is debt:** The safety rule is explicitly that replacement is permitted only while no instance refers to these definitions, but the count is a time-of-check snapshot rather than an invariant protected through commit. A writer can create an instance after the count and before removal, invalidating the basis for an accepted import; downstream constraints may reject some such races, but the importer supplies no universal guarantee. The blast radius is platform because one import replaces definitions owned by multiple features and can conflict with their instance writers.
- **Suggested refactoring:** Define an organization-scoped replacement/exclusion protocol shared with instance creation and concurrent installs. Acquire the relevant guard before checking probes, recheck inside the protected transaction, and hold protection through replacement/provenance commit; moving the count alone into a default-isolation transaction is insufficient. Add real-transaction race tests and a documented integration contract for participants/writers; choose locking or a maintenance-state protocol with an explicit cross-process strategy.
- **Depends on / blocks:** BSR-TST-01 supplies the transactional test foundation; no dependency on changes to either other reviewed slice.

### BSR-TST-01 — Transaction tests cannot prove rollback of imported definitions

- **Category:** TST
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\java-shared\base-starter-backend\src\test\java\com\processpuzzle\starter\usecase\ImportBundleTest.java:40` — uses a real JPA transaction manager but explicitly substitutes fake definition participants.
  - `libs\java-shared\base-starter-backend\src\test\java\com\processpuzzle\starter\usecase\FakeParticipant.java:19` — definition state is an ordinary `LinkedHashMap`; writes at line 52 and clearing at line 63 are not enlisted in the transaction.
  - `libs\java-shared\base-starter-backend\src\test\java\com\processpuzzle\starter\usecase\ImportBundleTest.java:125` — the dry-run test checks empty provenance/installed tables, not restored definition state.
  - `libs\java-shared\base-starter-backend\src\test\java\com\processpuzzle\starter\usecase\ImportBundleTest.java:147` — later-participant refusal asserts no provenance, but does not assert restoration of definitions already deleted or written.
  - `libs\java-shared\base-starter-backend\src\main\java\com\processpuzzle\starter\usecase\ImportBundle.java:163` — production dry-run correctness depends entirely on rolling back participant writes.
- **Why it is debt:** The tests exercise transaction rollback only for the importer's bookkeeping, not for the definition changes that make replacement dangerous. Their fake participants retain changes even on rollback, so the suite can remain green if transaction propagation or participant enlistment violates the advertised dry-run/all-or-nothing semantics. That weakens the feature's executable integration contract for every participant author; it is not evidence that all production participants currently violate it.
- **Suggested refactoring:** Add test-only transactional participants backed by distinct real tables and the same transaction manager. Seed existing definitions, fail a later participant after an earlier write, and assert exact restoration of definitions and provenance; repeat for dry runs and successful commits. Keep map fakes for ordering/plan unit tests, and make the transactional participant contract reusable by deploying applications without adding feature dependencies to this library.
- **Depends on / blocks:** blocks reliable validation of BSR-PER-01

## Out-of-scope observations

- Real `DefinitionImportParticipant` and `InstanceDataProbe` implementations belong to other slices and were not inspected. Their transaction participation, side effects and instance constraints remain integration questions, not additional scored findings.
- The public bucket's publishing controls, API contracts and composition-root authorization of `InstallStarter.provision` were outside scope. The trusted provisioning path is documented and is not treated as an authorization defect without its caller.
- Missing widget/document/workflow/app participants and the documented future migration feature are not scored as debt merely for being unimplemented.

## Strengths worth keeping

- `libs\java-shared\base-starter-backend\src\main\java\com\processpuzzle\starter\package-info.java:15` restricts the module to core/shared; `libs\java-shared\base-starter-backend\src\main\java\com\processpuzzle\starter\usecase\ImportParticipants.java:66` rejects duplicate kind registrations and orders participant execution without feature imports.
- `libs\java-shared\base-starter-backend\src\main\java\com\processpuzzle\starter\usecase\InstallStarter.java:61` downloads and validates the catalog hash before entering import application, including expected starter identity/version.
- `libs\java-shared\base-starter-backend\src\main\java\com\processpuzzle\starter\bundle\BundleReader.java:63` bounds uncompressed entries and validates paths; `libs\java-shared\base-starter-backend\src\main\java\com\processpuzzle\starter\registry\HttpStarterRegistry.java:110` separately caps downloaded bytes.
- `libs\java-shared\base-starter-backend\src\main\java\com\processpuzzle\starter\usecase\ImportBundle.java:230` records canonical post-import fingerprints within the same application transaction rather than hashing source YAML as a proxy for stored definitions.
- `libs\java-shared\base-starter-backend\src\test\java\com\processpuzzle\starter\registry\HttpStarterRegistryTest.java:49` uses a local HTTP server for adapter behavior; `libs\java-shared\base-starter-backend\src\test\java\com\processpuzzle\starter\StarterModularityTests.java:17` calls Modulith `verify()`.
