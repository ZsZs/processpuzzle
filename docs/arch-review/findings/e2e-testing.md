# Architecture review: e2e-testing

- **Slice code:** FET
- **Paths in scope:** `libs\js-shared\e2e-testing`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** IDE/scoped reads and glob, inspection of generated suites, page objects, fixture/data helpers, public API, README, manifest and selected specs, plus read-only report-reference/schema checks. Static review only; tests inspected, not executed. No runtime stack, E2E, build or lint was run.
- **Calibration:** Human/reviewer calibration was not performed; effort estimates are provisional. Rubric scales are unchanged.

## Summary

The library meaningfully derives interactions from descriptors while keeping Node-side execution separate from Angular runtime imports. Independent CRUD fixtures, containment-specific relationship checks and artifact round trips provide much stronger coverage than generic click-through smoke tests. Its cleanup boundary nevertheless begins too late and depends on the same page that may have failed. Artifact assertions also accept outcomes weaker than the advertised persistence contract, and LIST tests have an unowned non-empty-data prerequisite. These shared testing mechanisms affect confidence and repeatability across consuming features.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
|---|---|---|---|---|---|---|---|
| FET-TST-01 | Fixture rollback starts after the fallible save boundary | TST | 3 | 3 platform | M | High | 4.5 |
| FET-TST-02 | Artifact assertions do not establish byte identity or deletion | TST | 3 | 3 platform | M | High | 4.5 |
| FET-TST-03 | Generated LIST coverage has no ownership of its required data | TST | 2 | 3 platform | M | High | 3.0 |

## Findings

### FET-TST-01 — Fixture rollback starts after the fallible save boundary

- **Category:** TST
- **Criticality:** 3
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.5
- **Evidence:**
  - `libs\js-shared\e2e-testing\src\lib\data\entity-crud-fixture-manager.ts:80` — main fixture creation awaits save before constructing/registering its cleanup record at line 92.
  - `libs\js-shared\e2e-testing\src\lib\data\entity-crud-fixture-manager.ts:236` — linked fixture creation repeats the same ordering, registering at line 248.
  - `libs\js-shared\e2e-testing\src\lib\pages\entity-form.po.ts:91` — save includes clicking the write action and waiting to leave the form.
  - `libs\js-shared\e2e-testing\src\lib\pages\entity-form.po.ts:168` — navigation failure is propagated even though submission may already have reached persistence.
  - `libs\js-shared\e2e-testing\src\lib\data\entity-crud-fixture-manager.ts:43` — teardown silently returns for a closed page; remaining deletion is UI-based.
- **Why it is debt:** If persistence succeeds but navigation or the page fails, the newly created entity has not yet entered the rollback ledger and teardown cannot attempt its removal. Already tracked fixtures also lose their cleanup path when the page is closed. This undermines the independent-fixture promise precisely on failure/retry paths and can leave shared test data across any CRUD, relationship, artifact or consuming suite using the manager; no deployed contamination is claimed to have been observed.
- **Suggested refactoring:** Record a pending cleanup identity before the first write, reconcile it with any returned ID, and make deletion idempotent when creation never happened. Introduce an injectable cleanup adapter or independently owned cleanup context so rollback is not contingent on the failed page; retain reverse ordering and aggregated errors. Add fault-injection tests for persisted-but-navigation-failed, linked-fixture setup failure and closed-page teardown.
- **Depends on / blocks:** none

### FET-TST-02 — Artifact assertions do not establish byte identity or deletion

- **Category:** TST
- **Criticality:** 3
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.5
- **Evidence:**
  - `libs\js-shared\e2e-testing\src\lib\suites\entity-artifact.suite.ts:46` — the suite describes exact uploaded-byte recovery and deletion reaching the store as cross-adapter invariants.
  - `libs\js-shared\e2e-testing\src\lib\suites\entity-artifact.suite.ts:137` — initial retrieval requires HTTP 200, but line 139 compares only byte lengths.
  - `libs\js-shared\e2e-testing\src\lib\suites\entity-artifact.suite.ts:146` — deletion is considered established by any non-OK response at the previously resolved URI.
- **Why it is debt:** A different or corrupted object of the same length passes the read-back assertion, and an authorization failure or server error passes the deletion assertion. Thus a green shared suite does not establish the persistence invariants it claims, even though its UI/reload checks are valuable. The platform blast radius follows from this being the common acceptance boundary for artifact controls and interchangeable storage adapters, not from any assumed deployed exposure.
- **Suggested refactoring:** Compare the returned buffer or a cryptographic digest with the uploaded bytes. Verify deletion through an adapter-appropriate authoritative missing-object result, distinguishing expected absence from authentication, expiry and service failures. Add negative test cases for same-length wrong bytes and 401/403/500 responses after deletion.
- **Depends on / blocks:** none

### FET-TST-03 — Generated LIST coverage has no ownership of its required data

- **Category:** TST
- **Criticality:** 2
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\js-shared\e2e-testing\src\lib\suites\entity-list.suite.ts:8` — configuration accepts registry, routes and exclusions but no fixture/seed provider.
  - `libs\js-shared\e2e-testing\src\lib\suites\entity-list.suite.ts:42` — each case navigates and unconditionally asserts non-empty rows without arranging any.
  - `libs\js-shared\e2e-testing\src\lib\pages\entity-list.po.ts:111` — non-empty means an actual visible first data row.
  - `libs\js-shared\e2e-testing\src\lib\suites\entity-crud.suite.ts:72` — CRUD cases independently remove their created fixtures rather than establishing data for later LIST cases.
  - `libs\js-shared\e2e-testing\src\lib\suites\entity-list.suite.ts:19` — the exclusion option explicitly acknowledges that a read-only entity may have nothing creating a row.
- **Why it is debt:** A correct empty list fails, while excluding the entity also removes its toolbar and empty-state coverage. Because no suite-local owner establishes the data prerequisite, generated coverage depends on ambient seeds and grows less reliable as descriptors or test environments change. This is a contained test-design issue with platform reach through the reusable suite factory, rather than a claim that every current seeded run fails.
- **Suggested refactoring:** Separate list-shell/empty-state checks from row-dependent checks. Add a per-entity fixture lifecycle hook, using the shared manager for creatable entities and an application-supplied setup adapter for runtime/read-only entities; document any intentionally seeded mode explicitly and validate its prerequisites.
- **Depends on / blocks:** none

## Out-of-scope observations

- Application-specific authentication, descriptor endpoints and storage implementations were not inspected. FET-TST-02 concerns the test oracle and does not duplicate host interceptor or backend authorization findings.
- The living-documentation fixture explicitly identifies itself as an illustrative in-memory stub: `libs\js-shared\e2e-testing\src\lib\processpuzzle-e2e-living-docs\support\platform-fixture.ts:1`. It is not evidence that a real cross-feature workflow passes; replacing an intentional demonstration stub is not scored as absent production functionality.
- FET-TST-01 owns the shared manager defect. Consumers using it should cross-reference this item rather than receive duplicate cleanup findings; unrelated application-owned REST fixture code requires its own evidence.
- Static inspection did not establish current runtime seed contents or reproduce an orphaned fixture.

## Strengths worth keeping

- Type-only base-entity exports preserve the Playwright/Node boundary without evaluating the Angular runtime: `libs\js-shared\e2e-testing\src\public-api.ts:1`.
- Fixture dependencies are checked for cycles and created per linked attribute; cleanup processes tracked records in reverse order and aggregates failures: `libs\js-shared\e2e-testing\src\lib\data\entity-crud-fixture-manager.ts:47`; `libs\js-shared\e2e-testing\src\lib\data\entity-crud-fixture-manager.ts:150`.
- CRUD update tests reload and assert persisted fields rather than just observing local form state: `libs\js-shared\e2e-testing\src\lib\suites\entity-crud.suite.ts:105`.
- Relationship suites explicitly distinguish association, component and embedded-component persistence semantics: `libs\js-shared\e2e-testing\src\lib\suites\entity-relationship.suite.ts:54`.
- Data-factory tests use plain descriptor objects, matching the JSON registry boundary, and verify targeted overrides: `libs\js-shared\e2e-testing\src\lib\data\test-data-factory.spec.ts:6`.
