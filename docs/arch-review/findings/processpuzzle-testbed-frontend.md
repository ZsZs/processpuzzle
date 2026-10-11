# Architecture review: processpuzzle-testbed-frontend

- **Slice code:** FTP
- **Paths in scope:** `apps\processpuzzle-testbed-frontend`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** Scoped source/test reads and ripgrep; resolved `npm exec nx -- show project processpuzzle-testbed-frontend --json`. No application, tests, build, lint or browser session executed.
- **Calibration:** Effort estimates are provisional; rubric scales unchanged and owner calibration unperformed.

## Summary

The testbed is an intentional composition root, registering feature facades, metadata-resolved screens and extension providers centrally. Its identity composition is incomplete: authenticated browser state does not bind the bearer-token transport and the workflow-session projection discards roles. The race-recognition sample also lacks a context boundary for overlapping operations and splits correction into independent delete/create requests. These are concrete integration and data-lifecycle debts; hard-coded race concepts are not themselves a violation because they belong to this demonstration application rather than the generic AI library.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FTP-API-01 | Authentication is not wired into the HTTP transport | API | 4 | 3 platform | M | High | 6.0 |
| FTP-MET-01 | Workflow session projection discards available roles | MET | 3 | 2 feature | M | High | 3.0 |
| FTP-PER-01 | Correcting an observation deletes before replacement succeeds | PER | 5 | 1 slice | M | High | 2.5 |
| FTP-STA-01 | Recognition work is not isolated by selected context | STA | 4 | 1 slice | M | High | 2.0 |

## Findings

### FTP-API-01 — Authentication is not wired into the HTTP transport

- **Category:** API
- **Criticality:** 4
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 6.0
- **Evidence:**
  - `apps\processpuzzle-testbed-frontend\src\app\app.config.ts:56` — registers the authentication service and its initialization.
  - `apps\processpuzzle-testbed-frontend\src\app\app.config.ts:58` — the application HTTP interceptor list contains only the central error interceptor.
  - `apps\processpuzzle-testbed-frontend\src\app\app.config.ts:101` — separately provides an authenticated-session adapter to the workflow feature.
  - Scoped source search for `provideAuthTokenInterceptor`, `authTokenInterceptor`, `Authorization` and `Bearer` found no production application bearer wiring.
- **Why it is debt:** Initializing browser authentication and supplying a user ID to a feature does not authenticate its HTTP calls. The host composes identity-aware features but omits the explicit token-transport seam, so ordinary HttpClient requests have no application-provided bearer credential even after login. The platform blast is confined to multiple features in this host; public/demo endpoints may still work, and no particular backend refusal or deployed security policy was tested.
- **Suggested refactoring:** Add the library's opt-in token interceptor to this host's existing HTTP chain using explicitly trusted platform service roots, preserving token-free third-party/object-storage traffic. Correct the allow-list predicate before enabling cross-origin roots and test actual application providers with a stub session, not a separately configured component HttpClient. Assert both authenticated platform requests and credential-free external requests.
- **Depends on / blocks:** FAU-SEC-01 before enabling cross-origin API-root matching

### FTP-MET-01 — Workflow session projection discards available roles

- **Category:** MET
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `apps\processpuzzle-testbed-frontend\src\app\content\base-workflows\session-user.context.ts:37` — maps user identity but unconditionally supplies `roles: []`.
  - `apps\processpuzzle-testbed-frontend\src\app\content\base-workflows\session-user.context.ts:19` — the comment documents the resulting unfiltered team queue and hidden skip override, attributing it to a missing user-role property.
  - `apps\processpuzzle-testbed-frontend\src\app\content\base-workflows\session-user.context.spec.ts:20` — the fixture supplies only an ID and expects empty roles after login.
- **Why it is debt:** The host's projection drops an authorization-related capability rather than adapting it. The comment describes a past missing contract, but the separately reviewed auth slice now exposes roles on `User`; the hard-coded empty list prevents this host from using them. The consequence is inconsistent role-driven workflow UX, not proof of backend permission bypass, and the fix belongs at the host adapter rather than coupling workflow directly to auth.
- **Suggested refactoring:** Forward the session's role projection while preserving explicit unknown-versus-empty semantics if the workflow contract distinguishes them. Extend the host adapter test with a real role-bearing user, role replacement and logout. Keep server authorization authoritative and coordinate session refresh consistency with the auth owner.
- **Depends on / blocks:** FAU-STA-01 supplies a consistent provider session lifecycle; the host mapping itself can be corrected independently

### FTP-PER-01 — Correcting an observation deletes before replacement succeeds

- **Category:** PER
- **Criticality:** 5
- **Blast radius:** 1 slice
- **Effort:** M
- **Confidence:** High
- **Priority score:** 2.5
- **Evidence:**
  - `apps\processpuzzle-testbed-frontend\src\app\content\base-ai\recognize-sample.component.ts:235` — correction locates an existing observation by recognition ID.
  - `apps\processpuzzle-testbed-frontend\src\app\content\base-ai\recognize-sample.component.ts:238` — deletes the existing persisted observation before calling `save`.
  - `apps\processpuzzle-testbed-frontend\src\app\content\base-ai\recognize-sample.component.ts:256` — replacement is a separate POST; failed persistence is only surfaced by the generic error handler.
  - `apps\processpuzzle-testbed-frontend\src\app\content\base-ai\recognize-sample.component.spec.ts:106` — the correction test flushes a successful DELETE and POST, without asserting preservation after the second operation fails.
- **Why it is debt:** A logical correction has been implemented as two independently committed lifecycle operations. If deletion succeeds but creation is refused or interrupted, the original observation is lost even though the user intended to correct it, and no compensation restores it. The risk is local to this sample, but it is a data-integrity gap in a working reference application rather than a stylistic objection to component size.
- **Suggested refactoring:** Correct the existing observation through a version-aware update that preserves its identity, or define an atomic correction command if the existing transport cannot express it. Do not describe delete-then-create as replacement without a recovery protocol. Add failed-correction and duplicate-submission tests that assert the original record remains available until correction commits.
- **Depends on / blocks:** FTP-STA-01 — capture and retain the correction's context before dispatch

### FTP-STA-01 — Recognition work is not isolated by selected context

- **Category:** STA
- **Criticality:** 4
- **Blast radius:** 1 slice
- **Effort:** M
- **Confidence:** High
- **Priority score:** 2.0
- **Evidence:**
  - `apps\processpuzzle-testbed-frontend\src\app\content\base-ai\recognize-sample.component.ts:215` — changing race immediately mutates signals and starts uncancelled entry/observation requests.
  - `apps\processpuzzle-testbed-frontend\src\app\content\base-ai\recognize-sample.component.ts:274` — any completing entry request replaces the current candidate list without checking its race.
  - `apps\processpuzzle-testbed-frontend\src\app\content\base-ai\recognize-sample.component.ts:283` — any completing observation request replaces the current view without checking race/round/checkpoint.
  - `apps\processpuzzle-testbed-frontend\src\app\content\base-ai\recognize-sample.component.ts:252` — save reads the currently selected context, including after correction has awaited a DELETE.
  - `apps\processpuzzle-testbed-frontend\src\app\content\base-ai\recognize-sample.component.spec.ts:128` — context changes are tested with responses flushed sequentially, not in reverse completion order.
- **Why it is debt:** Request completion and user selection have no shared context identity. A slow response for race A can overwrite entries while race B is selected, and changing the context during an awaited correction can save the replacement under a different checkpoint/race. Signal-based rendering does not solve stale ownership of asynchronous work; the issue is the missing request/mutation context boundary.
- **Suggested refactoring:** Capture an immutable context key for every load and mutation, cancel or ignore superseded reads, and prevent old work from updating a new context's state. Clear or mark old entries unavailable during transitions and bind recognition/correction outcomes to their originating context. Add reversed-response, switch-during-correction and destroy-during-load cases using HttpTestingController.
- **Depends on / blocks:** none

## Out-of-scope observations

- The auth library's role model and interceptor are separately reviewed in FAU; this report owns their composition, not their implementation.
- Root `eslint.config.mjs` permits wildcard tag dependencies while retaining Nx's other checks. A stricter architectural tag matrix is a workspace-level design follow-up, not proof that every disabled-rule comment in this app conceals a forbidden import.
- Backend enforcement, observation-update contracts and live identity behavior were not executed or re-reviewed for this frontend pass.

## Strengths worth keeping

- `apps\processpuzzle-testbed-frontend\src\app\app.config.ts:129` registers rule evaluation application-wide rather than making validation depend on the active sample route.
- `apps\processpuzzle-testbed-frontend\src\app\app.routes.ts:98` documents single facade/store ownership instead of re-providing conflicting route-local instances.
- `apps\processpuzzle-testbed-frontend\src\app\content\base-workflows\session-user.context.ts:35` reacts to session changes rather than capturing only the initial anonymous user.
- `apps\processpuzzle-testbed-frontend\src\app\content\base-ai\recognize-sample.component.spec.ts:78` checks the host's candidate restriction at the camera boundary using realistic registration data.
