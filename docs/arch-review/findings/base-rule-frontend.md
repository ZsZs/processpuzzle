# Architecture review: base-rule-frontend

- **Slice code:** FRU
- **Paths in scope:** `libs\js-shared\base-rule-frontend`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** Static IDE file discovery, scoped glob/ripgrep, file and numbered PowerShell reads, and Git revision verification. Applied the existing rubric and architectural intent from `AGENTS.md`/`README.md`; inspected Angular 22 peer dependencies rather than relying on older README labels. Traced evaluator, engine adapter, rule service/store/facade, metadata authoring, context options, dry-run UI and relevant tests. Tests inspected, not executed; no builds, tests, Nx tasks or lint were run.
- **Estimation note:** Effort is provisional because rubric calibration has not been completed. Rubric scales and effort points are unchanged.

## Summary

The slice composes the generic entity feature through its public API and keeps rule evaluation behind a small adapter. Three concrete gaps weaken the frontend validation contract: evaluation can mutate its input (FRU-ABS-01), non-boolean and asynchronous results are converted to successful verdicts (FRU-MET-01), and rule loading drops all pages after the first (FRU-API-01). The authoring descriptor explicitly promises a boolean expression, making the evaluator mismatch a gap in current behavior rather than missing future functionality. The inspected tests cover normal predicates, evaluation errors, caching and one-page response adaptation, but do not exercise the complete contracts behind these findings.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FRU-ABS-01 | Rule evaluation can mutate the caller's entity | ABS | 5 | 2 feature | M | High | 5.0 |
| FRU-MET-01 | Boolean rule verdicts are replaced by JavaScript truthiness | MET | 4 | 2 feature | M | High | 4.0 |
| FRU-API-01 | Context rule loading silently discards later pages | API | 4 | 2 feature | M | High | 4.0 |

## Findings

### FRU-ABS-01 — Rule evaluation can mutate the caller's entity

- **Category:** ABS
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\js-shared\base-rule-frontend\src\lib\domain\base-rule.descriptors.ts:19` — the authoring field describes a “PPCL boolean expression evaluated against `entity`”, not a transformation.
  - `libs\js-shared\base-rule-frontend\src\lib\domain\base-rule-evaluator.service.ts:24` — the compiled function receives the supplied entity object directly.
  - `libs\js-shared\base-rule-frontend\src\lib\domain\base-rule-evaluator.service.ts:45` — strict-mode JavaScript is compiled without a read-only input wrapper, copy or mutation boundary.
  - `libs\js-shared\base-rule-frontend\src\lib\adapter\base-rule-engine.adapter.ts:28` — the public rule-engine adapter forwards its caller's entity unchanged to that evaluator.
  - `libs\js-shared\base-rule-frontend\src\lib\domain\base-rule-evaluator.service.spec.ts:60` — the inspected runtime-error test checks the returned error, not whether execution changed input before throwing.
- **Why it is debt:** A validation predicate has write access to the object it is supposed to assess. An expression such as `(entity.quantity = 1, true)` changes caller-owned data and reports success; nested mutations also survive, and catching a later exception does not undo earlier writes. This breaks the read-only evaluation abstraction at the public adapter boundary, affecting both the rule feature and generated-form consumers without needing to assume any backend behavior.
- **Suggested refactoring:** Evaluate against an isolated, recursively read-only snapshot with an explicitly supported value model, and return a clear evaluation failure for mutation attempts. Avoid freezing the caller's live object as a side effect of validation. Add tests for direct writes, nested writes, array mutation, multiple rules sharing a target and mutation followed by an exception; verify the original object remains unchanged.
- **Depends on / blocks:** none

### FRU-MET-01 — Boolean rule verdicts are replaced by JavaScript truthiness

- **Category:** MET
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\js-shared\base-rule-frontend\src\lib\domain\base-rule.descriptors.ts:21` — metadata authoring explicitly advertises a boolean expression.
  - `libs\js-shared\base-rule-frontend\src\lib\domain\base-rule-evaluator.service.ts:5` — compiled expressions return `unknown`.
  - `libs\js-shared\base-rule-frontend\src\lib\domain\base-rule-evaluator.service.ts:22` — execution is synchronous and its result is converted with `Boolean(result)` at line 29, with no boolean/thenable check.
  - `libs\js-shared\base-rule-frontend\src\lib\feature\base-rule-dry-run.dialog.ts:89` — dry run uses the same evaluator, and its template at line 43 displays a successful result as “Passed.”
  - `libs\js-shared\base-rule-frontend\src\lib\domain\base-rule-evaluator.service.spec.ts:19` — tests describe truthy/falsy outcomes but use ordinary boolean comparison expressions; no inspected case rejects a non-boolean or asynchronous result.
- **Why it is debt:** The rule language presented to authors promises predicates, but execution accepts arbitrary truthy values as successful validation. The string `"false"` and an asynchronous expression returning a promise of `false` both yield `passed: true`, with the latter never awaiting its actual verdict. Dry run repeats that misleading success, so it does not catch the mismatch before the rule is used by consumers.
- **Suggested refactoring:** Enforce `typeof result === 'boolean'` for the synchronous predicate contract and return a distinguishable evaluation error for strings, numbers, objects and thenables. If asynchronous rules are desired, introduce an explicitly asynchronous contract rather than coercing promises through the synchronous API. Add evaluator and dry-run cases for false-like strings, objects, promises and valid boolean results.
- **Depends on / blocks:** none

### FRU-API-01 — Context rule loading silently discards later pages

- **Category:** API
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\js-shared\base-rule-frontend\src\lib\adapter\base-rule-engine.adapter.ts:14` — loading a context issues one filtered `findByQuery` request without requesting subsequent pages.
  - `libs\js-shared\base-rule-frontend\src\lib\adapter\base-rule-engine.adapter.ts:41` — the page adapter returns only `content` and discards page/count information.
  - `libs\js-shared\base-rule-frontend\src\lib\adapter\base-rule-engine.adapter.ts:20` — filtering/enabled checks are then applied to that partial content and emitted as the complete rule array.
  - `libs\js-shared\base-rule-frontend\src\lib\domain\base-rule.service.ts:7` — rule retrieval reuses the generic REST collection service rather than an explicitly complete-context rule endpoint.
  - `libs\js-shared\base-rule-frontend\src\lib\adapter\base-rule-engine.adapter.spec.ts:89` — the inspected paged-response fixture has `totalPages: 1`; it verifies unwrapping but not exhaustion of a multi-page context.
- **Why it is debt:** “Load rules for a context” is adapted from a paginated browse operation without preserving the distinction between one page and the full rule set. For any supported response with additional pages, the adapter emits success while omitting those rules, so callers cannot tell that validation is incomplete. Client-side removal of disabled rules can even leave an empty first-page result although enabled rules remain on later pages.
- **Suggested refactoring:** Provide an exhaustive context-loading operation that follows pagination to completion, or use an explicitly complete-context API with a documented bound. Preserve cancellation/error propagation across the entire load and never emit a partial set as ready. Add multi-page tests with an ERROR rule on the last page, a disabled-only first page, and a later-page failure.
- **Depends on / blocks:** none

## Out-of-scope observations

- Backend rule semantics, sandboxing and enforcement were not inspected or used as evidence. No security assessment of browser code execution is claimed; FRU-ABS-01 concerns directly verified mutation of caller-owned data.
- Generated-form readiness and warning-to-control-error mapping are separate FEN-MET-01 and FEN-MET-02 findings. The adapter can emit a complete, correct result while a consumer mishandles readiness/severity; conversely, fixing the consumer cannot recover missing rule pages.
- Rule inheritance/override fields exist in the model, but incomplete planned inheritance behavior is not scored without a stronger in-scope consequence.

## Strengths worth keeping

- The entity integration uses the public package and an injected rule-engine port, with an explicit provider helper: `libs\js-shared\base-rule-frontend\src\lib\adapter\base-rule-engine.adapter.ts:3` and line 50.
- Expression caching is keyed by expression text rather than rule ID, so an edited expression does not reuse a compiled function for a different expression: `libs\js-shared\base-rule-frontend\src\lib\domain\base-rule-evaluator.service.ts:38`.
- Compile/runtime errors are represented separately from ordinary failed predicates, and disabled rules short-circuit: `libs\js-shared\base-rule-frontend\src\lib\domain\base-rule-evaluator.service.ts:13` and line 18. The corresponding tests at `libs\js-shared\base-rule-frontend\src\lib\domain\base-rule-evaluator.service.spec.ts:51` and line 78 were inspected, not executed.
- Context options merge compiled and metadata-defined entity names, deduplicate them, and expose asynchronously loaded names through a signal: `libs\js-shared\base-rule-frontend\src\lib\domain\rule-context-options.service.ts:40`. The container supplies a callback rather than freezing the initial options array at `libs\js-shared\base-rule-frontend\src\lib\feature\base-rule-container.component.ts:39`.
