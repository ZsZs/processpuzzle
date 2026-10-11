# Architecture review: base-state-frontend

- **Slice code:** FST
- **Paths in scope:** `libs\js-shared\base-state-frontend`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** Static review: IDEA symbol discovery and attempted call hierarchy (timed out), followed by scoped glob/ripgrep, file and numbered PowerShell reads. Git confirmed matching HEAD/scoped tracked source. Inspected manifests/public exports, definition mapping/store/facade, registry, layout persistence/conversion/canvas, operation controls/tabs and representative HTTP/component tests. No build, lint, tests or browser execution.
- **Calibration:** Effort estimates are provisional; rubric section 9 calibration has not been performed.

## Summary

The slice has a useful separation between machine topology, diagram layout and object-state operations, substantially beyond the README's old placeholder description. Its main issue is keeping an editable draft alive while two independent resources are saved: a layout response can rebuild away unsaved topology before its own write succeeds. Both layout and object-state views lack latest-request ownership on route changes. A second machine cache used by generated controls is disconnected from the library's authoring writes. Existing HTTP-driven component tests provide a good base, but inspected cases do not exercise reversed response order or second-stage save failure.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FST-STA-01 | Two-resource saving can discard the topology draft | STA | 5 | 2 feature | M | High | 5.0 |
| FST-STA-02 | Route-scoped reads can publish obsolete results | STA | 4 | 2 feature | M | High | 4.0 |
| FST-STA-03 | Governed-machine cache is detached from authoring updates | STA | 3 | 2 feature | M | High | 3.0 |

## Findings

### FST-STA-01 — Two-resource saving can discard the topology draft

- **Category:** STA
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\js-shared\base-state-frontend\src\lib\feature\definition\state-modeler-tab.component.ts:178` — snapshots both payloads, saves layout first and then awaits machine update; the draft survives only as local variables in this method.
  - `libs\js-shared\base-state-frontend\src\lib\domain\modeler\data-access\diagram-definition.store.ts:80` — a successful layout save immediately replaces `currentEntity` and clears loading.
  - `libs\js-shared\base-state-frontend\src\lib\feature\definition\state-modeler-tab.component.ts:125` — that shared current layout is a canvas input.
  - `libs\js-shared\base-state-frontend\src\lib\feature\modeler\components\state-machine-canvas.component.ts:162` — any input change rebuilds the model from the current persisted machine/layout inputs, rather than preserving draft topology.
  - `libs\js-shared\base-state-frontend\src\lib\feature\definition\state-modeler-tab.component.ts:32` — Save is guarded only by machine presence and diagram loading, not the whole two-write operation.
  - `libs\js-shared\base-state-frontend\src\lib\feature\definition\state-modeler-tab.component.spec.ts:222` — tests the first-write failure path; the inspected save cases do not retain a changed draft after the topology write fails.
- **Why it is debt:** If layout persistence succeeds but topology persistence is delayed or rejected, the new layout input rebuilds the canvas from the old machine and removes unsaved state edits/additions from the visible draft. The first write has already committed, while the only remaining copy of the intended topology belongs to a completed or failing save call. Enabling Save again during the second request compounds the ambiguity; the UI needs a coherent save/draft lifecycle even when the two resources remain separate.
- **Suggested refactoring:** Own topology and layout drafts in an editor session, separate from server snapshots, and keep a single saving state across both writes. Preserve and display the topology draft and partial-success status on second-stage failure, allowing a safe retry; reconcile successful responses without replacing newer edits. Add a test that edits/adds a state, accepts the layout PUT, rejects the machine PUT, and verifies the draft and retry payload remain intact.
- **Depends on / blocks:** none

### FST-STA-02 — Route-scoped reads can publish obsolete results

- **Category:** STA
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\js-shared\base-state-frontend\src\lib\domain\modeler\data-access\diagram-definition.store.ts:54` — concurrent layout loads unconditionally update one shared current layout and loading flag on completion.
  - `libs\js-shared\base-state-frontend\src\lib\feature\definition\state-modeler-tab.component.ts:125` — forwards that layout without checking it belongs to the current machine; line 157 starts another load whenever the route identity changes.
  - `libs\js-shared\base-state-frontend\src\lib\domain\modeler\graph\converters\state-machine-graph.converter.ts:42` — joins layout rows by state/transition keys, without checking layout entity identity.
  - `libs\js-shared\base-state-frontend\src\lib\feature\definition\entity-state-machine-tab.component.ts:204` — the operation tab reloads on input changes, but its async load publishes the assembled view unconditionally at line 236.
  - `libs\js-shared\base-state-frontend\src\lib\domain\modeler\data-access\diagram-definition.store.spec.ts:28` — load coverage uses a single outstanding machine request, not reordered navigation requests.
- **Why it is debt:** A slow response for machine/object A can replace the view after navigation to B. In the modeler, common state keys can make A's geometry look plausibly applicable to B, including carrying the wrong layout revision into a save; in the operation tab, the displayed current state can belong to the previous object. Root scoping and `firstValueFrom` are not inherently wrong, but route identity must participate in publication and readiness decisions.
- **Suggested refactoring:** Introduce a request generation or cancellable keyed load for each view, and clear/validate current layout against the requested entity identity. Publish only the latest machine/object result, including errors and loading completion, and ignore obsolete save responses when selecting a view. Add A/B navigation tests with both response orders and matching state keys, plus read failure while another load is pending.
- **Depends on / blocks:** none

### FST-STA-03 — Governed-machine cache is detached from authoring updates

- **Category:** STA
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\js-shared\base-state-frontend\src\lib\domain\definition\governed-entity.registry.ts:32` — caches a separate promise/map of full machine definitions; reset is manual at line 57.
  - `libs\js-shared\base-state-frontend\src\lib\domain\definition\state-machine-definition.store.ts:12` — the authoring store is a separate root-scoped generic CRUD store with no registry invalidation feature.
  - `libs\js-shared\base-state-frontend\src\lib\feature\definition\state-modeler-tab.component.ts:187` — the modeler updates the authoring store without resetting the registry.
  - `libs\js-shared\base-state-frontend\src\lib\feature\operation\entity-state-control.provider.ts:17` — generated controls decide which attribute is state-governed from the registry cache.
  - `libs\js-shared\base-state-frontend\src\lib\domain\definition\governed-entity.registry.spec.ts:110` — the “authored machine is picked up” test calls `registry.reset()` itself rather than exercising an authoring action.
  - Scoped source search for registry/reset usage found no production reset call inside this slice; the explicit reset call is in that test.
- **Why it is debt:** After the registry has answered once, the supplied authoring paths can create, change or delete a machine while operation controls continue using the earlier definition or cached absence. Reloading a form does not refresh that root cache, so the generated control/tab decision and the newly saved metadata can disagree. A host can work around this through manual reset, but the library owns both caches and supplies no automatic consistency boundary between them.
- **Suggested refactoring:** Derive registry answers from a shared machine cache or invalidate them on successful create/update/delete/import through a central authoring boundary. Make cache scope explicit if a host changes organization, and avoid retaining obsolete in-flight results after reset. Test an initial negative lookup followed by creation, an attribute change, and deletion through the real authoring path without manually calling reset in the test.
- **Depends on / blocks:** FST-STA-01 — reconcile/invalidate only after the relevant topology write succeeds, not after layout-only partial success.

## Out-of-scope observations

- Generic base-entity cache/form behavior, backend transaction and revision enforcement, generated API schemas, and host organization switching were not inspected. The registry finding is about the supplied library paths, not proof that every host omits manual invalidation.
- `libs\js-shared\base-state-frontend\src\lib\domain\operation\entity-object-state.service.ts:23` captures a configured service root. Whether hosts recreate services on organization changes is unresolved and unscored.
- Package peers identify Angular 22. Some README status statements are older than the implemented source; stale prose alone is not scored.

## Strengths worth keeping

- Explicit mapper preservation of nested states, guards, actions, metadata and version: `libs\js-shared\base-state-frontend\src\lib\domain\definition\state-machine-definition.mapper.ts:91`. Full-document serialization is centralized rather than delegated to whatever is drawn.
- The graph converter separates topology from geometry and preserves undrawn root metadata: `libs\js-shared\base-state-frontend\src\lib\domain\modeler\graph\converters\state-machine-graph.converter.ts:88`.
- Operation transport is a narrow projection/command API rather than an inappropriate CRUD subclass: `libs\js-shared\base-state-frontend\src\lib\domain\operation\entity-object-state.service.ts:35` and `:48`. It passes a version for state transition commands.
- HTTP-driven modeler tests inspect actual layout and topology request bodies: `libs\js-shared\base-state-frontend\src\lib\feature\definition\state-modeler-tab.component.spec.ts:201`. This is a useful foundation for partial-failure and race tests; no tests were executed in this review.
