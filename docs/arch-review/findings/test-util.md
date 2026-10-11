# Architecture review: test-util

- **Slice code:** FTU
- **Paths in scope:** `libs\js-shared\test-util`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** IDE/scoped file reads, glob, inspection of public exports, manifest, README and unit tests, and read-only report-reference/schema checks. Static review only; tests inspected, not executed. No build or lint was run.
- **Calibration:** Human/reviewer calibration was not performed; effort estimates are provisional. Rubric scales are unchanged.

## Summary

This is a small, intentionally reusable testing boundary rather than an application feature. Its narrow public API and deterministic helpers are useful, but two test doubles change the semantics of the dependencies they replace. The breakpoint observer ignores requested queries and initial state, while the translation helpers interpret the same configuration differently and discard legitimate nested translations. Existing tests often preserve those altered semantics rather than checking compatibility with consumers' dependency contracts.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
|---|---|---|---|---|---|---|---|
| FTU-TST-01 | Breakpoint mock does not model query-specific current state | TST | 3 | 3 platform | M | High | 4.5 |
| FTU-TST-02 | Translation helpers expose incompatible catalog and language semantics | TST | 3 | 3 platform | M | High | 4.5 |

## Findings

### FTU-TST-01 — Breakpoint mock does not model query-specific current state

- **Category:** TST
- **Criticality:** 3
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.5
- **Evidence:**
  - `libs\js-shared\test-util\README.md:19` — the helper is offered as a replacement for Angular's `BreakpointObserver`.
  - `libs\js-shared\test-util\src\lib\mock-breakpoint-observer\mock-breakpoint-observer.service.ts:11` — current viewport state is not retained; updates use a plain subject.
  - `libs\js-shared\test-util\src\lib\mock-breakpoint-observer\mock-breakpoint-observer.service.ts:86` — aggregate `matches` is computed over all generated true breakpoint entries.
  - `libs\js-shared\test-util\src\lib\mock-breakpoint-observer\mock-breakpoint-observer.service.ts:90` — `observe` accepts no query and gives every observer the same stream.
  - `libs\js-shared\test-util\src\lib\mock-breakpoint-observer\mock-breakpoint-observer.service.spec.ts:239` — the test explicitly expects no initial emission.
- **Why it is debt:** A consumer observing only a handset query receives `matches: true` after a desktop resize because some desktop breakpoint matched, while a viewport set before subscription is lost. This changes the branch and initialization behavior under test, so responsive components can pass or fail for reasons unrelated to their real dependency. The blast radius is platform because the exported mock is a shared Angular testing foundation, not because RxJS itself is unsuitable.
- **Suggested refactoring:** Retain simulated viewport state, accept the real observe query shape, and project initial/subsequent results onto only the requested queries with explicit false values. Add compatibility cases for handset-on-desktop, multiple disjoint observers, resize-before-subscribe and supported custom queries; make unsupported query behavior explicit instead of silently ignoring it.
- **Depends on / blocks:** none

### FTU-TST-02 — Translation helpers expose incompatible catalog and language semantics

- **Category:** TST
- **Criticality:** 3
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.5
- **Evidence:**
  - `libs\js-shared\test-util\src\lib\transloco\transloco-testing.provider.ts:20` — the shared configuration advertises available languages, default language, scope and a translation map.
  - `libs\js-shared\test-util\src\lib\transloco\transloco-testing.provider.ts:46` — the loader extracts a scope from a language object or filters object-valued keys out of an unscoped catalog.
  - `libs\js-shared\test-util\src\lib\transloco\transloco-testing.provider.ts:69` — the provider uses fixed languages/defaults and disables language-change rerendering instead of honoring those configuration fields.
  - `libs\js-shared\test-util\src\lib\transloco\transloco-testing.provider.ts:92` — the component helper passes the complete translation map to `TranslocoTestingModule` using another fixed language list.
  - `libs\js-shared\test-util\src\lib\transloco\transloco-testing.provider.spec.ts:15` — component tests supply slash-keyed scoped catalogs with nested keys; the loader-specific test at line 143 instead asserts nested-object removal.
- **Why it is debt:** An unscoped catalog such as `{ en: { buttons: { save: 'Save' } } }` is reduced to an empty object by one public helper, while the other passes nested catalogs through. A caller's advertised default-language configuration is also ignored, and switching between helpers changes scope layout and rerender behavior. This forces test-only catalog reshaping and makes internationalization tests dependent on harness choice across the shared consumer base.
- **Suggested refactoring:** Establish one documented translation-map convention and configuration-to-Transloco mapping shared by both entry points, preferably delegating loading to the supported testing module rather than guessing whether an object is a scope. Preserve nested namespaces, honor supplied language/default settings, and test both helpers against the same nested, scoped and language-switching component cases.
- **Depends on / blocks:** none

## Out-of-scope observations

- Consumers were not inspected; the review establishes divergence in the exported doubles, not a count of affected application tests or a production-language defect.
- Supported CDK and Transloco versions are declared in `libs\js-shared\test-util\package.json:13`; no dependency-package implementation or runtime compatibility test was executed.

## Strengths worth keeping

- Public exports stay focused on breakpoint simulation and translation support: `libs\js-shared\test-util\src\public-api.ts:1`.
- Responsive tests cover several widths and device classes rather than a single hard-coded mobile flag: `libs\js-shared\test-util\src\lib\mock-breakpoint-observer\mock-breakpoint-observer.service.spec.ts:133`.
- The component translation helper waits for language loading and view stability: `libs\js-shared\test-util\src\lib\transloco\transloco-testing.provider.ts:111`.
- Translation tests include rendered nested labels and an actual language change, providing a useful base for shared helper-contract tests: `libs\js-shared\test-util\src\lib\transloco\transloco-testing.provider.spec.ts:100`.
