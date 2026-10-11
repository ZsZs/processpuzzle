# Architecture review: org-admin-frontend

- **Slice code:** FOA
- **Paths in scope:** `libs\js-shared\org-admin-frontend`
- **Reviewed at commit:** 5714af4efcc879e0a6d5ae6935588e0cec65dbfb
- **Date:** 2026-10-10
- **Tools run:** Read-only IDE symbol lookup, scoped glob/ripgrep, source/test/manifest/documentation reads, Git HEAD, and `npm exec nx show project org-admin-frontend --json` (successful). Resolved build uses Angular packaging, tests use `@nx/angular:unit-test`/Vitest, and lint uses ESLint. No tests, builds, lint, live directory calls or runtime navigation executed.

## Summary

The library preserves useful responsibility boundaries: generated profile CRUD uses a signal store/facade, invitations have a separate payload, and roles have a dedicated sub-resource service and tab. Current peer manifests target Angular 22; the review uses the supplied Nx 23 workspace context. The two serious debts are both in the role editor's state model: failed initial reads become an editable empty selection, and a reused route can retain the previous user's identity. These are frontend permission-write integrity risks evidenced by this slice's request construction, not claims of deployed authorization bypasses.

Effort estimates are provisional because rubric section 9 calibration has not been performed. Static review inspected role-assignment and organization-container component tests, service/mapper code, route contribution and store/facade composition; tests were not executed. Role-editor tests use fixed route snapshots and cover successful reads and save errors, but not failed initial reads or reused-route identity changes. Services/stores use root lifetime and a construction-time organization key; the host's one-tenant-per-bootstrap behavior, HTTP error interceptor and backend enforcement were outside scope.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FOA-STA-01 | Failed role reads become an authoritative empty edit | STA | 5 | 2 feature | M | High | 5.0 |
| FOA-STA-02 | Role-editor identity is frozen to the first route snapshot | STA | 5 | 2 feature | M | High | 5.0 |

## Findings

### FOA-STA-01 — Failed role reads become an authoritative empty edit

- **Category:** STA
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\js-shared\org-admin-frontend\src\lib\feature\role-assignment.component.ts:93` — the selected roles start as an empty set, with no separate loaded/unknown role-state representation.
  - `libs\js-shared\org-admin-frontend\src\lib\feature\role-assignment.component.ts:158` — a failed user-role read only clears `loading`; a failed realm-role read does likewise at line 161.
  - `libs\js-shared\org-admin-frontend\src\lib\feature\role-assignment.component.ts:55` — once loading ends, Save is disabled only for saving/no user ID, not for failed or incomplete reads.
  - `libs\js-shared\org-admin-frontend\src\lib\feature\role-assignment.component.ts:127` — Save submits the complete selected set, including an uninitialized empty set, as a replacement.
  - `libs\js-shared\org-admin-frontend\src\lib\feature\role-assignment.component.spec.ts:49` — the common load helper successfully flushes both reads; the error-path test concerns saving rather than loading.
- **Why it is debt:** An initial directory/network failure is interpreted as completion of loading instead of absence of authoritative data. After a failed role read, the screen can show unchecked roles and accept Save, sending an empty or partial replacement that revokes roles the user never saw. An external error snackbar cannot repair that state invariant, and the resulting write affects actual feature permissions rather than just the visual editor.
- **Suggested refactoring:** Represent loading, loaded-empty, loaded-with-data and failed states explicitly and require a successfully loaded baseline for both rendering editable controls and executing Save. Provide an explicit reload action and preserve the distinction between unknown roles and intentionally cleared roles. Test each initial GET failing independently, asserting that no PUT is possible until a complete successful reload establishes the baseline.
- **Depends on / blocks:** none

### FOA-STA-02 — Role-editor identity is frozen to the first route snapshot

- **Category:** STA
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\js-shared\org-admin-frontend\src\lib\feature\role-assignment-tab.ts:17` — the same role-editor component is contributed beneath the user-specific route.
  - `libs\js-shared\org-admin-frontend\src\lib\feature\role-assignment.component.ts:106` — `ngOnInit` reads the route/parent snapshot once into mutable `userId`, then calls `reload`.
  - `libs\js-shared\org-admin-frontend\src\lib\feature\role-assignment.component.ts:127` — later saves continue to use that stored ID, not the current route parameter.
  - `libs\js-shared\org-admin-frontend\src\lib\feature\role-assignment.component.ts:143` — nested subscriptions own no route generation or destruction cancellation and publish into shared editor signals.
  - `libs\js-shared\org-admin-frontend\src\lib\feature\role-assignment.component.spec.ts:28` — route tests provide fixed snapshots without parameter-change emissions or router reuse.
- **Why it is debt:** With normal same-route component reuse, changing the user ID does not rerun `ngOnInit`, so the URL can identify B while the role editor still displays and writes A. Adding a route subscription alone would still leave overlapping loads/saves free to publish into a newer user's editor. This violates the ownership of a sensitive edit session; a host that forces remounting may avoid the trigger, but the reusable library does not establish that requirement.
- **Suggested refactoring:** Derive user identity reactively from the relevant route parameter stream or bound input, and create a fresh edit-session generation whenever it changes. Cancel obsolete reads, clear the baseline/selection until the new user's load succeeds, and associate pending save responses with the user/context that initiated them. Add real-router or parameter-stream tests for A-to-B reuse, out-of-order responses and destruction while requests are pending.
- **Depends on / blocks:** none; coordinate with FOA-STA-01 because both change the role-editor state machine

## Out-of-scope observations

- Backend authorization, provider role-replacement atomicity and identity/account ownership were not inspected. Neither finding assumes that an unauthorized tenant can access the endpoint.
- The injected organization key and service roots are construction-time values in root-provided services. The host may deliberately recreate the application for each tenant; no tenant-switch defect is scored without that host contract.
- A deployment-level HTTP interceptor is assumed by component error handlers, but its user messaging and retry behavior were not inspected.
- Existing public base-entity imports establish composition through an API, not proof of the external generic store's internal correctness. No library cycle or forbidden deep import was verified.

## Strengths worth keeping

- `libs\js-shared\org-admin-frontend\src\lib\domain\organization-user.mapper.ts:23` restricts profile updates to profile fields and `enabled`, excluding username and roles so ordinary name edits do not rewrite permissions.
- `libs\js-shared\org-admin-frontend\src\lib\domain\organization-user.service.ts:53` gives invitation creation a dedicated method rather than forcing it through the update mapper.
- `libs\js-shared\org-admin-frontend\src\lib\domain\organization-user.store.ts:8` composes public signal-store features for generic entity/container state instead of duplicating them.
- `libs\js-shared\org-admin-frontend\src\lib\feature\role-assignment.component.ts:133` adopts the server's confirmed saved roles rather than optimistically assuming the requested set became authoritative.
- `libs\js-shared\org-admin-frontend\src\lib\feature\organization-user-container.component.spec.ts:82` verifies that invitations include username and roles and reload the authoritative user list after success.
