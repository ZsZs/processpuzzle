# Architecture review: base-starter-frontend

- **Slice code:** FSR
- **Paths in scope:** `libs\js-shared\base-starter-frontend`
- **Reviewed at commit:** 5714af4efcc879e0a6d5ae6935588e0cec65dbfb
- **Date:** 2026-10-10
- **Tools run:** Read-only IDE symbol lookup, scoped glob/ripgrep, source/test/manifest/documentation reads, Git HEAD, and `npm exec nx show project base-starter-frontend --json` (successful). Resolved build is Angular packaging, tests use `@nx/angular:unit-test`/Vitest, and lint uses ESLint. No tests, builds, lint or runtime calls executed.

## Summary

This is a small, well-separated signals-first feature: a component-scoped store orchestrates requests, one service owns HTTP details, and report/installed-list components are presentational. Current peer manifests target Angular 22 and the review uses the supplied Nx 23 context. The important gap is not missing scaffolding but an invalid state transition: a late preview can authorize a different selection, bypassing the screen's core preview-before-install promise. Mutation failures also lack an explicit rejected-versus-unknown-versus-completed operation state, making recovery inconsistent.

Effort estimates are provisional because rubric section 9 calibration has not been performed. Static review inspected `starter-import.store.spec.ts`, `starter-import.component.spec.ts`, HTTP service code and report models; no tests were executed. The inspected store/component tests mostly use immediately emitting `of(...)` responses and sequential actions, so they do not establish race safety. Backend transaction/idempotency behavior, API contracts and host organization switching were not inspected.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FSR-STA-01 | Late previews can authorize an unpreviewed selection | STA | 5 | 2 feature | M | High | 5.0 |
| FSR-OPS-01 | Install outcomes lack a recoverable operation state | OPS | 3 | 2 feature | M | High | 3.0 |

## Findings

### FSR-STA-01 — Late previews can authorize an unpreviewed selection

- **Category:** STA
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\js-shared\base-starter-frontend\src\lib\domain\starter-import.store.ts:45` — `canApply` checks only that some selection exists and the stored preview says `would-apply`, not that their identities match.
  - `libs\js-shared\base-starter-frontend\src\lib\domain\starter-import.store.ts:52` — a request captures a selection, but its response at line 57 unconditionally patches the shared preview/applied slot.
  - `libs\js-shared\base-starter-frontend\src\lib\domain\starter-import.store.ts:84` — `select` clears reports without cancelling or invalidating pending work.
  - `libs\js-shared\base-starter-frontend\src\lib\feature\starter-import.component.ts:46` — starter radio inputs remain enabled during requests; the version selector at line 67 is also enabled.
  - `libs\js-shared\base-starter-frontend\src\lib\domain\starter-import.store.spec.ts:88` — selection-reset coverage changes the selection after a preview has completed, not during a pending response.
- **Why it is debt:** Start a preview for A, select B before it returns, then accept A's response: `canApply` becomes true while the next apply request sends B. The backend can validate B without knowing that the user never reviewed B's deletions, so backend validation does not restore the missing confirmation. The same uncorrelated state allows stale reports to overwrite newer selection state; the blast radius includes the feature's destructive installation request, not just report rendering.
- **Suggested refactoring:** Store preview identity/generation alongside its report and require exact active-selection correspondence in both the computed eligibility and the `apply` method. Invalidate or cancel superseded previews and ignore late results; optionally disable selection changes while a mutation is executing. Add deferred-response tests covering A-to-B selection changes, superseded previews and direct store calls that bypass button disabled state.
- **Depends on / blocks:** none

### FSR-OPS-01 — Install outcomes lack a recoverable operation state

- **Category:** OPS
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\js-shared\base-starter-frontend\src\lib\domain\starter.service.ts:38` — install is a mutating POST; response-body reports are emitted normally and transport failures are rethrown.
  - `libs\js-shared\base-starter-frontend\src\lib\domain\starter-import.store.ts:57` — every non-dry-run report, including `rejected`, is stored in `applied`.
  - `libs\js-shared\base-starter-frontend\src\lib\domain\starter-import.store.ts:60` — an exception updates only the error and releases busy state, retaining the previous successful preview.
  - `libs\js-shared\base-starter-frontend\src\lib\domain\starter-import.store.ts:45` — transport failure therefore permits immediate Apply again, whereas any `applied` report, including rejection, blocks it.
  - `libs\js-shared\base-starter-frontend\src\lib\domain\starter-import.store.ts:58` — installed-state reconciliation runs only after an explicitly successful response.
- **Why it is debt:** A missing POST response does not establish whether the installation happened, but the store returns directly to a retryable state with the old preview and no reconciliation. Conversely, a confirmed refusal is treated as an applied outcome: a subsequent successful preview still cannot enable Apply until selection is reset. This conflates materially different recovery situations and can cause unnecessary repeat mutations or a stranded UI; no claim is made that a particular backend currently duplicates work.
- **Suggested refactoring:** Model operation phases explicitly: preview-ready, applying, rejected, applied and outcome-unknown, retaining the attempted selection. Allow confirmed refusals to return to preview/retry deliberately, while unknown outcomes require reconciliation or explicit recovery before another mutation; an installed-version read is informative but should not be presented as proof of operation identity. Add tests for response loss, rejected apply followed by a fresh preview, and successful install followed by installed-list refresh failure.
- **Depends on / blocks:** FSR-STA-01 supplies stable selection/operation identity for this recovery state

## Out-of-scope observations

- Whether install requests have server-side idempotency or an operation-status API was not inspected. This report scores the frontend's unsupported assumption about failure, not an alleged backend duplicate-write defect.
- `StarterService` captures its organization root on construction; tenant-switch/rebootstrap behavior belongs to the host and was not reviewed.
- External schema evolution and supported definition kinds are not scored from handwritten interfaces alone; no contract mismatch was verified.
- Public imports/manifests did not establish a forbidden library boundary or cycle. Nx boundary lint was not run.

## Strengths worth keeping

- `libs\js-shared\base-starter-frontend\src\lib\feature\starter-import.component.ts:21` scopes `StarterImportStore` to the screen, avoiding an unnecessary global install-session singleton.
- `libs\js-shared\base-starter-frontend\src\lib\domain\starter-import.store.ts:41` uses signal-store state/computed values rather than independently maintained component copies.
- `libs\js-shared\base-starter-frontend\src\lib\domain\starter.service.ts:38` deliberately preserves structured refusal reports instead of reducing every HTTP error to a string.
- `libs\js-shared\base-starter-frontend\src\lib\feature\import-report.component.ts:20` presents create/update/delete consequences and detailed refusals; the presentation component does not issue requests.
- `libs\js-shared\base-starter-frontend\src\lib\feature\starter-import.component.spec.ts:99` exercises preview then install through the rendered screen, including deletion visibility and the refreshed installed list.
