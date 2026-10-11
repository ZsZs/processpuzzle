# Architecture review: base-workflow-frontend

- **Slice code:** FWF
- **Paths in scope:** `libs\js-shared\base-workflow-frontend`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** Static review: IDEA file/symbol discovery, scoped glob/ripgrep, file reads and numbered PowerShell evidence checks. Git confirmed HEAD and the scoped tracked source match the requested commit. Inspected models/mappers, manifests/public exports, dashboard stores/actions/components, modeler loading/conversion/editing, and representative HTTP/component/unit tests. No build, test, lint or browser execution.
- **Calibration:** Effort estimates are provisional; rubric section 9 calibration has not been performed.

## Summary

The library uses signal projections effectively and keeps transport commands separate from generic CRUD screens. The principal consistency problems are identity ownership: completion drafts and feedback are not keyed to the task, and layout requests can publish state after their route is obsolete. The dashboard also presents one page of a shared instance cache as an inbox, while completion inputs are limited to strings rather than declared task metadata. Modeler display changes rebuild away unsaved geometry. These findings are about implemented behavior, not a preference against RxJS or a demand to finish every planned perspective.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FWF-STA-01 | Completion drafts and feedback have no task identity | STA | 5 | 2 feature | M | High | 5.0 |
| FWF-STA-02 | Layout request completion is not bound to the current route | STA | 4 | 2 feature | M | High | 4.0 |
| FWF-ABS-01 | The inbox is a projection of one unrelated instance page | ABS | 4 | 2 feature | L | High | 2.0 |
| FWF-MET-01 | Completion input cannot represent typed task context | MET | 4 | 2 feature | L | High | 2.0 |
| FWF-STA-03 | Display changes discard the modeler's unsaved geometry | STA | 3 | 1 slice | M | High | 1.5 |

## Findings

### FWF-STA-01 — Completion drafts and feedback have no task identity

- **Category:** STA
- **Criticality:** 5
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 5.0
- **Evidence:**
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\dashboard\task-detail.component.ts:68` — the completion form stays in the same conditional branch when selection changes between two owned ACTIVE tasks; no task identity is passed to it.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\dashboard\completion-form.component.ts:198` — context rows and skip reason are local signals with no task-keyed reset.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\dashboard\task-detail.component.ts:189` — submission takes the currently selected row and combines it with whatever context the existing form emits.
  - `libs\js-shared\base-workflow-frontend\src\lib\domain\dashboard\workflow-dashboard.store.ts:335` — selection clears feedback immediately, but line 373 later writes a completed request's refusal into the same unkeyed feedback field.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\dashboard\task-list.component.ts:44` — task selection remains available while actions are running.
- **Why it is debt:** Entering context for task A and selecting another owned ACTIVE task B can reuse the form with A's rows still present; submitting then addresses B. An in-flight refusal for A can likewise arrive after selecting B and appear beside B's form. This is business-data attribution risk, not merely stale presentation: the task identity and its editable draft need one owner even though the underlying instance data remains shared.
- **Suggested refactoring:** Key drafts, skip reasons and action outcomes by instance/task identity, or recreate/reset the editor explicitly when that identity changes. Capture the action target and a request generation when submitting; apply feedback only to that target and preserve or intentionally discard its draft after completion. Add component tests that type on A, switch directly to B, submit, and deliver A's deferred refusal after the switch.
- **Depends on / blocks:** none

### FWF-STA-02 — Layout request completion is not bound to the current route

- **Category:** STA
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\js-shared\base-workflow-frontend\src\lib\domain\modeler\data-access\workflow-diagram.store.ts:53` — each `firstValueFrom` load unconditionally patches shared current layout/loading state on completion; no latest-request token or cancellation is present.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\definition\workflow-modeler-tab.component.ts:265` — every route-triggered load sets the same settled signal to true in `finally`, regardless of whether the route changed.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\definition\workflow-modeler-tab.component.ts:230` — identity filtering prevents applying another workflow's layout, but does not prevent an obsolete response replacing the store's current result.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\definition\workflow-modeler-tab.component.ts:246` — Save is enabled by those shared settled/loading flags.
  - `libs\js-shared\base-workflow-frontend\src\lib\domain\modeler\data-access\workflow-diagram.store.spec.ts:29` — load tests exercise one request at a time, not route changes with reversed response order.
- **Why it is debt:** Open A, navigate to B, then let A's response finish: it can mark B's screen settled and not loading while B's request is still pending. The existing identity guard avoids using A's coordinates, but instead permits an automatic-layout fallback and potentially a save before B's saved arrangement/version has been read. If A completes last, it can also displace B's successful result; the problem is asynchronous request ownership, not use of promises itself.
- **Suggested refactoring:** Track the requested workflow identity and generation in one owner, cancel or ignore obsolete loads, and derive readiness from the result for that exact key. Distinguish failed lookup from a confirmed missing layout, and keep old save responses from selecting an obsolete workflow. Test A/B loads in both response orders, A's late failure, and navigation during a save.
- **Depends on / blocks:** none

### FWF-ABS-01 — The inbox is a projection of one unrelated instance page

- **Category:** ABS
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.0
- **Evidence:**
  - `libs\js-shared\base-workflow-frontend\src\lib\domain\dashboard\dashboard-task.ts:16` — explicitly documents that the inbox is only as wide as the paginated instance list.
  - `libs\js-shared\base-workflow-frontend\src\lib\domain\dashboard\workflow-dashboard.store.ts:125` — takes `instanceStore.entities()` as the entire input; lines 154 and 189 flatten and filter only those loaded instances.
  - `libs\js-shared\base-workflow-frontend\src\lib\domain\dashboard\workflow-dashboard.store.ts:313` — refresh reloads the generic instance list rather than an inbox-scoped query.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\dashboard\task-list.component.ts:61` — an empty loaded projection is rendered as the queue's empty state, without a paging/completeness indicator.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\dashboard\workflow-dashboard.component.ts:184` — badges count the projected queues rather than a complete task result.
- **Why it is debt:** Once relevant tasks are outside the loaded instance page, “My Tasks” and “Team” can omit real work while presenting an authoritative empty queue or count. Sharing entity records is useful, but sharing a generic list's query window makes operational correctness depend on a monitoring screen's pagination. This documented limitation is already part of the live dashboard architecture and affects workflow operation as data grows.
- **Suggested refactoring:** Define an inbox query boundary with user/role scope, task filters, paging and total counts; coordinate its transport with the API owner. Keep shared normalized instance/task entities if beneficial, but let each screen own its query membership and completeness. Until a dedicated query exists, explicitly expose partial results and a supported continuation strategy rather than claiming an empty inbox.
- **Depends on / blocks:** none

### FWF-MET-01 — Completion input cannot represent typed task context

- **Category:** MET
- **Criticality:** 4
- **Blast radius:** 2 feature
- **Effort:** L
- **Confidence:** High
- **Priority score:** 2.0
- **Evidence:**
  - `libs\js-shared\base-workflow-frontend\src\lib\domain\property-map.ts:13` — narrows maps to `Record<string, string>` even though its documentation describes arbitrary JSON values on the wire.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\dashboard\completion-form.component.ts:23` — explains that task definitions provide no completion-field metadata, leaving users to know variable names.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\dashboard\completion-form.component.ts:239` — emits text values verbatim through `Object.fromEntries`, with no typed editor or conversion.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\dashboard\completion-form.component.spec.ts:71` — explicitly expects a review score to be sent as `'7'`, not numeric `7`.
- **Why it is debt:** The implemented completion surface cannot produce boolean, numeric, structured or null context values even when an orchestrated task needs them. Users must also know undeclared variable names, making rule-compatible submission depend on instructions or downstream coercion instead of metadata. The debt is a gap in the current completion contract/editor design, not an assertion about any particular backend rule: the frontend's own type and test establish the representational limitation.
- **Suggested refactoring:** Separate expression maps from runtime JSON context types. Introduce a task-completion field schema or a host-supplied form-schema port with names, types, validation and defaults, then render typed controls and serialize their values without heuristic string coercion. Coordinate persistence/wire support with its owners and add number/boolean/null/object round trips.
- **Depends on / blocks:** FWF-STA-01 — preserve task-keyed draft ownership when introducing typed forms.

### FWF-STA-03 — Display changes discard the modeler's unsaved geometry

- **Category:** STA
- **Criticality:** 3
- **Blast radius:** 1 slice
- **Effort:** M
- **Confidence:** High
- **Priority score:** 1.5
- **Evidence:**
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\definition\workflow-modeler-tab.component.ts:62` — layer toggles change the computed graph supplied to the editable canvas.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\modeler\components\workflow-diagram.component.ts:255` — rebuilds geometry from automatic placement plus the last saved layout, then initializes a new model.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\modeler\components\workflow-diagram.component.ts:340` — explicitly preserves live viewport across rebuilds, but there is no equivalent preservation of live node/edge geometry.
  - `libs\js-shared\base-workflow-frontend\src\lib\domain\modeler\graph\workflow-diagram.converter.ts:83` — hidden-row preservation merges from the previous saved diagram, not an unsaved draft.
  - `libs\js-shared\base-workflow-frontend\src\lib\feature\modeler\components\workflow-diagram.component.spec.ts:315` — tests viewport preservation on rebuild, not unsaved drag/resize/routing preservation.
- **Why it is debt:** Dragging a task or changing edge routing and then toggling a display layer rebuilds the model from persisted geometry, discarding the unsaved edit. Even hidden-node preservation cannot recover it because it merges the last saved arrangement rather than the working draft. Display projection and draft ownership are conflated, making normal inspection gestures destructive to authoring work.
- **Suggested refactoring:** Keep a workflow-keyed draft layout outside the rendered graph, update it from canvas edits, and project visible layers from that draft. Reconcile catalog changes by stable node/edge identity without resetting unaffected geometry; reserve discard for an explicit action or confirmed navigation. Test drag/resize/routing followed by toggles, including toggling an edited node's own layer off and on.
- **Depends on / blocks:** FWF-STA-02 — settle request identity before reconciling persisted layout into a draft.

## Out-of-scope observations

- Backend handlers, generated schemas and generic base-entity store/form internals were not read. Statements in local comments about backend claim races are not treated as verified findings.
- Organization identity is represented locally through a configured service root (`libs\js-shared\base-workflow-frontend\src\lib\domain\dashboard\task-action.service.ts:66`), not a tenant-switch input. Host switching/recreation and generic cache invalidation were not traced, so no deployment-wide tenant-isolation claim is made.
- Current package peers name Angular 22. No conclusion about Nx enforcement or resolved task configuration is drawn; no Nx task/configuration query was needed.

## Strengths worth keeping

- Signal-based dashboard projections avoid copying catalog/instance data into a second mutable cache: `libs\js-shared\base-workflow-frontend\src\lib\domain\dashboard\workflow-dashboard.store.ts:113`. Query completeness still needs its own ownership as noted above.
- Dedicated command transport uses the task definition ID explicitly and separates HTTP success from business acceptance: `libs\js-shared\base-workflow-frontend\src\lib\domain\dashboard\task-action.service.ts:90`.
- The mapper preserves full-replacement metadata through explicit nested conversions rather than forwarding only visible form fields: `libs\js-shared\base-workflow-frontend\src\lib\domain\definition\workflow.mapper.ts:148`.
- Diagram gestures distinguish layout from semantics: `libs\js-shared\base-workflow-frontend\src\lib\feature\modeler\components\workflow-diagram.component.ts:87` restricts relinking to the same node, and `:100` refuses lane membership changes. Tests for these restrictions were inspected, not executed.
