# Architecture review: processpuzzle-testbed-e2e

- **Slice code:** FTE
- **Paths in scope:** `apps\processpuzzle-testbed-e2e`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** IDE/scoped file reads, inspection of Playwright configuration, fixture/page/API helpers and test specifications, plus read-only report-reference/schema checks. Static review only; tests inspected, not executed. No runtime stack, E2E, AI request, fixture download, build or lint was run.
- **Calibration:** Human/reviewer calibration was not performed; effort estimates are provisional. Rubric scales are unchanged.

## Summary

The testbed sensibly delegates generic metadata coverage to the shared E2E library and adds application-specific document and recognition round trips. Document tests reread persisted content, while recognition tests separate deterministic canned responses from opt-in real-model execution. Two concrete gaps remain: shell smoke assertions check locator objects rather than the DOM, and the application-owned REST fixture cleanup assumes setup completed successfully. Existing seed and shared-library assumptions are recorded separately rather than duplicated as new application findings.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
|---|---|---|---|---|---|---|---|
| FTE-TST-01 | Shell smoke checks assert locator construction instead of rendered structure | TST | 3 | 1 slice | S | High | 3.0 |
| FTE-TST-02 | Recognition fixture rollback depends on successful setup and cleanup requests | TST | 3 | 1 slice | M | High | 1.5 |

## Findings

### FTE-TST-01 — Shell smoke checks assert locator construction instead of rendered structure

- **Category:** TST
- **Criticality:** 3
- **Blast radius:** 1 slice
- **Effort:** S
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `apps\processpuzzle-testbed-e2e\src\pages\application-page.ts:14` — root/header/sidenav/footer members are lazy locator objects constructed without inspecting the DOM.
  - `apps\processpuzzle-testbed-e2e\src\tests\home_page_structure.spec.ts:8` — the home smoke test checks those objects with `toBeTruthy`.
  - `apps\processpuzzle-testbed-e2e\src\tests\home_page_structure.spec.ts:17` — the dedicated header-structure test has only a truthiness assertion after navigation.
  - `apps\processpuzzle-testbed-e2e\src\pages\application-page.ts:53` — navigation-list presence is likewise asserted through locator truthiness.
- **Why it is debt:** A locator remains truthy when it matches no element, so removing the header or footer does not fail the purported structure assertions. This creates a false-positive boundary in the application's smoke coverage and hides shell integration regressions from checks intended to detect them. The real title and navigation assertions still provide coverage; the finding is limited to the structural assertions rather than claiming the entire suite is vacuous.
- **Suggested refactoring:** Use awaited locator assertions for presence/visibility and meaningful shell structure, with selectors verified against the intended public DOM contract. Add a negative fixture or intercepted-shell case showing that removing an expected structural element makes the smoke assertion fail.
- **Depends on / blocks:** none

### FTE-TST-02 — Recognition fixture rollback depends on successful setup and cleanup requests

- **Category:** TST
- **Criticality:** 3
- **Blast radius:** 1 slice
- **Effort:** M
- **Confidence:** High
- **Priority score:** 1.5
- **Evidence:**
  - `apps\processpuzzle-testbed-e2e\src\tests\recognize-sample.spec.ts:57` — setup creates the regatta, then championship, then registrations, each as a separately fallible operation.
  - `apps\processpuzzle-testbed-e2e\src\tests\recognize-sample.spec.ts:65` — teardown reads both race IDs and completes observation-list requests before calling the recorded-fixture cleanup.
  - `apps\processpuzzle-testbed-e2e\src\support\race-sample.api.ts:73` — successfully created entities are recorded immediately, so the cleanup ledger exists even after partially completed setup.
  - `apps\processpuzzle-testbed-e2e\src\support\race-sample.api.ts:86` — `removeAll` stops at the first rejected deletion instead of continuing and reporting aggregated failures.
  - `apps\processpuzzle-testbed-e2e\src\tests\recognize-sample.ai.spec.ts:44` — the AI suite registers cleanup early, but observation lookup and entity deletion must succeed before artifact cleanup is reached.
- **Why it is debt:** If championship creation fails after regatta creation, teardown dereferences the unset championship and never reaches the already populated cleanup ledger. An observation lookup failure or one failed delete similarly prevents cleanup of later independent resources, including the AI artifact. These failure paths can accumulate test-owned data across retries even though normal-path fixtures use run-specific races; this is the application's REST fixture lifecycle, not the shared UI manager defect.
- **Suggested refactoring:** Use a setup-independent resource ledger with optional race handles, and guarantee ledger cleanup in a finally path even when observation discovery fails. Attempt all reverse-ordered removals while collecting failures, independently finalize artifact cleanup and dispose the owned request context. Add helper-level failure-injection tests for the second-create, observation-list and first-delete failure cases.
- **Depends on / blocks:** none

## Out-of-scope observations

- Host findings FTP-API-01, FTP-MET-01 and FTP-STA-01 were supplied for deduplication; host source was not inspected, and none is rescored here.
- `apps\processpuzzle-testbed-e2e\src\tests\document-content.spec.ts:38` delegates cleanup to the shared manager. Its failure-path ownership is FET-TST-01; FTE-TST-02 applies only to the separate REST helper/recognition lifecycle.
- `apps\processpuzzle-testbed-e2e\src\tests\entity-list.spec.ts:10` excludes a legitimately empty workflow-instance list. The reusable suite's missing data-ownership/empty-state separation is FET-TST-03, not another application finding.
- Recognition setup looks up seeded boats by sail number (`apps\processpuzzle-testbed-e2e\src\tests\recognize-sample.spec.ts:51`), and the helper reads one page of at most 200 objects (`apps\processpuzzle-testbed-e2e\src\support\race-sample.api.ts:54`). Seed contents, stability and dataset size were not verified; repeatability beyond that sample-data assumption remains uncertain.
- Opt-in AI validation downloads an external photograph (`apps\processpuzzle-testbed-e2e\src\tests\recognize-sample.ai.spec.ts:94`). Neither the file nor a vision service was accessed, and no real-model success is claimed.

## Strengths worth keeping

- Document persistence is checked by reloading after autosave and independently asserting properties plus preserved content: `apps\processpuzzle-testbed-e2e\src\tests\document-content.spec.ts:64`; `apps\processpuzzle-testbed-e2e\src\tests\document-content.spec.ts:96`.
- Deterministic recognition tests inspect request candidates and persisted observations rather than only canned UI output: `apps\processpuzzle-testbed-e2e\src\tests\recognize-sample.spec.ts:91`; `apps\processpuzzle-testbed-e2e\src\tests\recognize-sample.spec.ts:111`.
- Real-model recognition is explicitly opt-in and separate from the default suite: `apps\processpuzzle-testbed-e2e\src\tests\recognize-sample.ai.spec.ts:19`.
- Generic CRUD exclusions carry domain-specific reasons, while compatible cross-field metadata uses targeted fixture overrides: `apps\processpuzzle-testbed-e2e\src\tests\entity-crud.spec.ts:8`; `apps\processpuzzle-testbed-e2e\src\tests\entity-crud.spec.ts:61`.
- Configuration scopes the dev server to development and passes escaped entity/suite selection to Playwright: `apps\processpuzzle-testbed-e2e\playwright.config.ts:13`; `apps\processpuzzle-testbed-e2e\playwright.config.ts:47`.
