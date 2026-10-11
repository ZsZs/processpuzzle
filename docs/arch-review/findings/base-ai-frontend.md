# Architecture review: base-ai-frontend

- **Slice code:** FAI
- **Paths in scope:** `libs\js-shared\base-ai-frontend`
- **Reviewed at commit:** 5714af4efcc879e0a6d5ae6935588e0cec65dbfb
- **Date:** 2026-10-10
- **Tools run:** Read-only IDE symbol lookup, scoped glob/ripgrep, source/test/manifest/documentation reads, Git HEAD, and `npm exec nx show project base-ai-frontend --json` (successful). Resolved packaging uses `@nx/angular:package`, tests use `@nx/angular:unit-test` with Vitest, and lint uses ESLint. No tests, builds, lint or runtime calls executed.

## Summary

The library uses signals for UI state and the framework's public descriptor/facade/store APIs for profile authoring, with dedicated services for enrollment and recognition. Scoped peer manifests target Angular 22; the review uses the current Nx 23 workspace context rather than the older stack description in general documentation. Its main debts are enrollment operations that outlive their subject context, camera results that are not bound to changing inputs, and a fixed detector-class list inside otherwise metadata-driven authoring. Cancellation exists, but does not cover all asynchronous resource acquisition paths.

Effort estimates are provisional because rubric section 9 calibration has not been performed. Static review inspected the enrollment component, recognition camera and recognition service tests, plus profile mapping/state composition; none were executed. Existing tests cover sequential subject changes, established camera teardown, cancellation and polling, but not the identified delayed acquisition/context-change interleavings. Organization roots are captured by root-provided services and the profile registry is session-cached; host tenant switching, backend authorization, remote job cleanup and external base-entity implementations were not inspected or assumed defective.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FAI-STA-01 | Enrollment operations are not owned by a stable subject context | STA | 5 | 2 feature | M | High | 5.0 |
| FAI-STA-02 | Camera answers survive changes to their recognition inputs | STA | 4 | 2 feature | M | High | 4.0 |
| FAI-MET-01 | Detector capabilities are compiled into the profile form | MET | 3 | 2 feature | M | High | 3.0 |
| FAI-OPS-01 | Cancellation does not cover delayed resource acquisition | OPS | 3 | 1 slice | M | High | 1.5 |

## Findings

### FAI-STA-01 — Enrollment operations are not owned by a stable subject context

- **Category:** STA
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\js-shared\base-ai-frontend\src\lib\feature\enrollment\entity-enrollment-tab.component.ts:230` — an effect launches asynchronous `load` on descriptor/entity-ID changes without cleanup or request-generation ownership.
  - `libs\js-shared\base-ai-frontend\src\lib\feature\enrollment\entity-enrollment-tab.component.ts:247` — rebuild deletes using the current entity ID, awaits completion, then reads `this.entityId()` again for synchronization at line 248.
  - `libs\js-shared\base-ai-frontend\src\lib\feature\enrollment\entity-enrollment-tab.component.ts:268` — `load` awaits a profile and updates shared signals without checking whether a newer subject replaced it.
  - `libs\js-shared\base-ai-frontend\src\lib\feature\enrollment\entity-enrollment-tab.component.ts:282` — `refresh` reads mutable profile/ID state; `show` can publish any late response and schedule another timer.
  - `libs\js-shared\base-ai-frontend\src\lib\feature\enrollment\entity-enrollment-tab.component.ts:235` — destruction clears only the current timer, not pending reads or actions.
- **Why it is debt:** Reusing the tab for another subject does not invalidate work already running for the previous subject. A rebuild can delete A's derived gallery, then synchronize B after navigation during the awaited deletion; delayed reads can also overwrite B's display with A's data and recreate polling after destruction. This is a feature-level consistency problem because the stale state can drive writes, not merely a transient rendering glitch.
- **Suggested refactoring:** Capture an immutable operation context containing entity code and object ID, and use it for every step of a rebuild. Make loads/polls cancellable and generation-scoped, reject late publications, and invalidate pending UI work on context change/destruction; do not imply that cancelling an HTTP subscription rolls back an accepted mutation. Add delayed-response tests for A-to-B navigation during deletion, overlapping profile reads, and destruction while a processing response is pending.
- **Depends on / blocks:** none

### FAI-STA-02 — Camera answers survive changes to their recognition inputs

- **Category:** STA
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\js-shared\base-ai-frontend\src\lib\feature\recognition\recognition-camera.component.ts:194` — entity name and candidates are mutable signal inputs.
  - `libs\js-shared\base-ai-frontend\src\lib\feature\recognition\recognition-camera.component.ts:216` — labels always derive from the current candidate input, not the shot's candidate snapshot.
  - `libs\js-shared\base-ai-frontend\src\lib\feature\recognition\recognition-camera.component.ts:306` — asynchronous recognition starts with whichever inputs are current when capture finishes.
  - `libs\js-shared\base-ai-frontend\src\lib\feature\recognition\recognition-camera.component.ts:311` — result acceptance checks only explicit abort, not whether entity/candidates changed.
  - `libs\js-shared\base-ai-frontend\src\lib\feature\recognition\recognition-camera.component.ts:323` — the result is emitted to the current host without its entity/candidate context.
- **Why it is debt:** The widget is advertised as reusable with application-supplied candidates, but a shot has no stable association with those inputs. If a host changes the candidate set or entity while a shot is running, the previous answer can be emitted into the new context and its labels are resolved against the new candidates; already displayed choices also survive the change. A consuming workflow can therefore act on a recognition belonging to the previous selection unless it independently implements missing correlation.
- **Suggested refactoring:** Snapshot entity/candidate identity and labels at shot start and assign a context generation. Abort/invalidate pending and displayed answers on meaningful input changes, and emit only results belonging to the active generation; consider an explicit caller context key if the same candidates can represent distinct host operations. Test input replacement during frame capture, pending recognition and manual review.
- **Depends on / blocks:** none

### FAI-MET-01 — Detector capabilities are compiled into the profile form

- **Category:** MET
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\js-shared\base-ai-frontend\src\lib\domain\profile\recognition-profile.descriptors.ts:5` — the form documents that the service can report a fuller detector-class list, while deliberately selecting a presumed useful subset.
  - `libs\js-shared\base-ai-frontend\src\lib\domain\profile\recognition-profile.descriptors.ts:11` — twelve class names are compiled into `DETECTOR_CLASSES`.
  - `libs\js-shared\base-ai-frontend\src\lib\domain\profile\recognition-profile.descriptors.ts:35` — the required dropdown obtains all options from that constant.
  - `libs\js-shared\base-ai-frontend\src\lib\domain\profile\recognition-profile.mapper.ts:70` — the mapper otherwise carries `detectorClass` as a string rather than a closed domain enum.
- **Why it is debt:** Recognition profiles are intended to extend recognition through metadata, but the authoring surface imposes a narrower compiled capability model. A deployment with a supported class outside the twelve choices cannot select it through the supplied profile form without replacing or modifying that form. Model/capability evolution therefore requires frontend intervention even when the persisted profile representation already supports the value.
- **Suggested refactoring:** Supply detector capabilities through a feature-owned injectable configuration/provider, optionally backed by a capability endpoint, rather than a fixed dropdown constant. Preserve existing unknown values when editing profiles and distinguish unavailable capability discovery from an invalid saved value. Test authoring/editing with a deployment-contributed class and with capability discovery unavailable.
- **Depends on / blocks:** none

### FAI-OPS-01 — Cancellation does not cover delayed resource acquisition

- **Category:** OPS
- **Criticality:** 3
- **Blast radius:** 1 slice
- **Effort:** M
- **Confidence:** High
- **Priority score:** 1.5
- **Evidence:**
  - `libs\js-shared\base-ai-frontend\src\lib\feature\recognition\recognition-camera.component.ts:222` — destruction aborts the current shot and stops only the stream already assigned to the component.
  - `libs\js-shared\base-ai-frontend\src\lib\feature\recognition\recognition-camera.component.ts:341` — a pending `getUserMedia` promise subsequently assigns its stream with no destroyed-state check.
  - `libs\js-shared\base-ai-frontend\src\lib\domain\recognition\recognition.service.ts:52` — cancellation is checked before calling `upload`, but the signal is not passed into it.
  - `libs\js-shared\base-ai-frontend\src\lib\domain\recognition\recognition.service.ts:78` — after awaiting an upload slot, `upload` starts the storage PUT unconditionally at line 88.
  - `libs\js-shared\base-ai-frontend\src\lib\feature\recognition\recognition-camera.component.spec.ts:240` — camera tests normally resolve acquisition immediately, so stopping an already-established stream does not cover late acquisition.
- **Why it is debt:** Cleanup is attached to resources that already exist, not to the asynchronous operation acquiring them. If the component is destroyed while camera permission is pending, a subsequently returned stream can remain live with nobody left to stop it; cancelling during slot reservation can likewise initiate a new storage upload after cancellation. These are observable device/network lifecycle leaks even though late recognition outputs are correctly suppressed after explicit abort.
- **Suggested refactoring:** Retain destruction/acquisition state and immediately stop a stream that arrives after its owner has ended. Propagate cancellation through slot acquisition and storage requests, checking it before every new side effect and unsubscribing pending HTTP work where possible. Test deferred camera resolution after destruction and cancellation between slot reservation and PUT.
- **Depends on / blocks:** none

## Out-of-scope observations

- Tenant roots and profile caches are initialized at application/service lifetime. The host's tenant/account rebootstrap contract was not inspected, so no cross-tenant vulnerability or switching failure is asserted.
- Generic base-entity request cancellation, descriptor routing, profile persistence and the actual detector capability endpoint are outside scope. FAI-MET-01 concerns this form's local capability restriction, not a verified remote schema mismatch.
- Recognition polling currently treats a read error as failure and abandons the local wait; server job retention/recovery is not evaluated here.
- No forbidden deep import or cyclic library dependency was established from the reviewed public imports/manifests; Nx lint was not executed.

## Strengths worth keeping

- `libs\js-shared\base-ai-frontend\src\lib\domain\recognition\recognition.service.ts:24` creates a bare storage client, keeping presigned uploads outside application interceptors; `libs\js-shared\base-ai-frontend\src\lib\domain\recognition\recognition.service.spec.ts:49` explicitly tests the boundary.
- `libs\js-shared\base-ai-frontend\src\lib\domain\profile\recognition-profile.mapper.ts:63` isolates flattened-form/nested-transport conversion from components, including numeric normalization.
- `libs\js-shared\base-ai-frontend\src\lib\domain\profile\recognition-profile.store.ts:9` composes public signal-store features instead of duplicating generic CRUD state.
- `libs\js-shared\base-ai-frontend\src\lib\domain\profile\profiled-entity.registry.ts:32` resolves display names through the definition registry before using the naming fallback.
- `libs\js-shared\base-ai-frontend\src\lib\feature\recognition\recognition-camera.component.ts:250` explicitly cancels a shot and clears its UI; the service's bounded polling and abortable delay prevent indefinite scheduled polling in the handled cancellation path.
