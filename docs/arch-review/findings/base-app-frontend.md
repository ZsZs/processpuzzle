# Architecture review: base-app-frontend

- **Slice code:** FAP
- **Paths in scope:** `libs\js-shared\base-app-frontend`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** Static IDE source/directory inspection, scoped file reads and ripgrep. Inspected manifests, public exports, descriptors/mapping, store publication behavior, preview, route construction, navigation, shell context/theme lifetime and associated tests. No build, tests, lint or Nx boundary verification executed; no `project.json` read or Nx configuration query needed. Angular 22 is confirmed by the slice manifest; Nx 23 is the supplied review baseline.
- **Calibration:** Owner/reviewer calibration was not performed. Effort estimates are provisional; rubric scales and effort points are unchanged.

## Summary

The library contains a substantial metadata authoring and runtime shell implementation despite its scaffold README. Signals own shell projections, theme/context providers are shell-local, and the route builder has useful independent tests. The remaining findings concern three seams between independently correct-looking pieces: nested route construction versus render outlets, navigation matching versus route-pattern semantics, and changing preview identity versus one-shot store selection. No finding is based merely on RxJS usage or explicitly deferred document/ENTITY rendering capabilities.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FAP-MET-01 | Nested widget routes have no child rendering outlet | MET | 3 | 2 feature | M | High | 3.0 |
| FAP-MET-02 | Navigation and route construction disagree on parameterized paths | MET | 3 | 2 feature | M | High | 3.0 |
| FAP-STA-01 | Preview identity changes do not reselect their definition | STA | 3 | 2 feature | M | High | 3.0 |

## Findings

### FAP-MET-01 — Nested widget routes have no child rendering outlet

- **Category:** MET
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\js-shared\base-app-frontend\src\lib\feature\app-route-builder.ts:166` — an authored prefix becomes the parent route and receives its authored descendants as `children` at lines 178–179.
  - `libs\js-shared\base-app-frontend\src\lib\feature\app-route-renderer.ts:43` — WIDGETS routes use `RouteWidgetsComponent`, including routes with authored descendants.
  - `libs\js-shared\base-app-frontend\src\lib\feature\route-widgets.component.ts:18` — the component imports only `WidgetListComponent` and renders only that list, with no child outlet.
  - `libs\js-shared\base-app-frontend\src\lib\feature\widget-list.component.ts:22` — the list renders widget hosts, not a route outlet; an empty widget list renders no host at all.
  - `libs\js-shared\base-app-frontend\src\lib\feature\app-route-builder.spec.ts:49` — nested-prefix tests verify the route tree using the component-free renderer at line 8, not the actual WIDGETS renderer.
- **Why it is debt:** The builder supports hierarchical metadata, but the concrete renderer does not fulfill the outlet requirement that hierarchy creates. For WIDGETS routes `claims` and `claims/open`, an empty or ordinary-widget parent has no outlet in which Angular can activate the child view, even though the generated route tree contains it. The feature's authored navigation therefore permits a composition that cannot render; a custom widget happening to supply an outlet is not a reliable framework contract.
- **Suggested refactoring:** Give routed WIDGETS content an explicit child outlet, or introduce a route-layout wrapper that owns both the parent widgets and that outlet while leaving region widget lists unchanged. Add a router integration test using the real builder and renderer that navigates through two nested WIDGETS levels and asserts visible child content.
- **Depends on / blocks:** none

### FAP-MET-02 — Navigation and route construction disagree on parameterized paths

- **Category:** MET
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\js-shared\base-app-frontend\src\lib\feature\app-route-builder.ts:183` — route paths are split into Angular path segments; lines 192–195 explicitly recognize parameter and wildcard segments.
  - `libs\js-shared\base-app-frontend\src\lib\feature\shell\app-shell.model.ts:93` — navigation receives the raw declared route patterns and module base paths.
  - `libs\js-shared\base-app-frontend\src\lib\feature\shell\region-nav.component.ts:44` — `toNavRows` accepts only literal equality or literal string-prefix matches; unresolved targets lose their link.
  - `libs\js-shared\base-app-frontend\src\lib\feature\shell\shell-application-context.ts:28` — the same matcher also supplies context-based navigation menus.
  - `libs\js-shared\base-app-frontend\src\lib\feature\app-route-builder.spec.ts:78` — tests explicitly support `claims/:id`; navigation tests at `libs\js-shared\base-app-frontend\src\lib\feature\shell\region-nav.component.spec.ts:22` instead cover static mount prefixes.
- **Why it is debt:** Two interpreters give the same route metadata different meanings. With only the declared pattern `claims/:id`, a navigation item targeting `claims/42` is rejected as unresolved even though the router can match it. This affects both shell navigation and application-context menus, forcing authors to introduce unrelated static parent routes or avoid supported parameterized destinations.
- **Suggested refactoring:** Extract a shared path-pattern matcher for navigation validation that understands parameter segments and preserves the deliberate uncertainty of lazy module mounts. Keep authored patterns distinct from concrete link targets rather than treating both as string prefixes. Test concrete parameter values, static siblings, mount prefixes and genuinely invalid targets against both navigation projection and actual routing.
- **Depends on / blocks:** none

### FAP-STA-01 — Preview identity changes do not reselect their definition

- **Category:** STA
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\js-shared\base-app-frontend\src\lib\feature\app-preview.component.ts:58` — `entityId` is a signal input, but selection occurs only in `ngOnInit` at lines 64–67.
  - `libs\js-shared\base-app-frontend\src\lib\feature\app-preview.component.ts:62` — the displayed application derives from the store's current entity, not from the current input identity.
  - `libs\js-shared\base-app-frontend\src\lib\feature\shell\app-shell-routes.ts:104` — the guard separately builds children from the current navigation's entity id.
  - `libs\js-shared\base-app-frontend\src\lib\feature\app-preview-tab.ts:18` — one reusable tab component and guard implement the preview route.
  - `libs\js-shared\base-app-frontend\src\lib\feature\app-preview.component.spec.ts:33` — inspected tests assert initial selection; they never change `entityId` on the existing instance.
- **Why it is debt:** Preview selection is initialization state even though identity is a changing component input. Updating that input on an existing preview leaves the selected definition untouched unless some external owner happens to update the shared store; meanwhile the route factory uses the new id. This can pair one application's routes with another application's theme, title and navigation, violating the promise that the preview is the same coherent shell used at runtime.
- **Suggested refactoring:** Reconcile selection whenever the identity input changes, clear or withhold mismatched definitions while loading, and make the shell projection require that its entity id matches the preview target. Coordinate route generation and shell data around that target rather than independent current-entity state. Add same-instance input-change and routed app-to-app navigation tests, including delayed selection.
- **Depends on / blocks:** none

## Out-of-scope observations

- Shared entity-store implementation, entity-screen resolver, widget runtime, authentication, backend contracts and deploying router configuration were not inspected. Host-specific compensation for preview selection is not assumed.
- `libs\js-shared\base-app-frontend\src\lib\feature\README.md:42` documents deliberately deferred ENTITY mode/filter behavior; that absence alone is not scored.
- The source manifest imports/dependency surface and public exports were inspected, but no package-consumer build or Nx boundary check was executed.

## Strengths worth keeping

- `libs\js-shared\base-app-frontend\src\lib\feature\shell\app-shell.component.ts:48` scopes theme service, application context and overlay/dialog providers to each shell instead of putting preview state in document-global services.
- `libs\js-shared\base-app-frontend\src\lib\feature\shell\shell-overlay-container.ts:21` moves the overlay container into the themed host; `libs\js-shared\base-app-frontend\src\lib\feature\shell\app-shell.component.ts:123` derives defaults from the input definition.
- `libs\js-shared\base-app-frontend\src\lib\feature\app-route-builder.ts:116` loads module metadata lazily and deliberately re-registers framework translation scopes rather than silently shadowing them.
- `libs\js-shared\base-app-frontend\src\lib\domain\app-definition.store.ts:31` applies publication responses to the matching entity and preserves a different current selection; failures become explicit error state.
- `libs\js-shared\base-app-frontend\src\lib\feature\app-preview-tab.ts:18` is a single descriptor shared by navigation and route registration, reducing link/route drift.
