# Architecture review: design

- **Slice code:** FDN
- **Paths in scope:** `libs\js-shared\design`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** Static IDE source/directory inspection, scoped file reads and ripgrep. Inspected the manifest, public API, aggregated routes, section tabs, landing cards, sidenav, route-awareness service and route/component/i18n test structure. No tests, build, lint, published-consumer install or Nx boundary verification executed; no `project.json` read or Nx configuration query needed. Manifest peers specify Angular 22; Nx 23 is the supplied baseline.
- **Calibration:** Owner/reviewer calibration was not performed. Effort estimates are provisional; rubric scales and effort points are unchanged.

## Summary

This slice is primarily a composition boundary rather than an editor or persistence owner. It correctly delegates feature authoring to public sibling route exports, keeps section tabs as real child routes and exposes route awareness as a read-only signal with subscription cleanup. The findings are confined to the ownership boundaries it does implement: imprecise recognition of the design area and an incomplete direct dependency contract for its published package. No duplicate tab-component/style finding is raised, and child editors' dirty/save behavior is not attributed to this slice.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FDN-STA-01 | Route awareness matches textual prefixes rather than the design branch | STA | 2 | 2 feature | S | High | 4.0 |
| FDN-ABS-01 | Published package omits directly imported framework peers | ABS | 3 | 2 feature | M | High | 3.0 |

## Findings

### FDN-STA-01 — Route awareness matches textual prefixes rather than the design branch

- **Category:** STA
- **Criticality:** 2
- **Blast radius:** 2 feature
- **Effort:** S
- **Confidence:** High
- **Priority score:** 4.0
- **Evidence:**
  - `libs\js-shared\design\src\lib\design-route\design-route.service.ts:33` — after parsing the URL, recognition uses `startsWith(prefix + '/design')`, with no segment-boundary check.
  - `libs\js-shared\design\src\lib\design-route\design-route.service.ts:16` — this predicate initializes the exported signal and is applied after every navigation at line 25.
  - `libs\js-shared\design\src\public-api.ts:11` — `DesignRouteService` is part of the library's public integration surface.
  - `libs\js-shared\design\src\lib\design-route\design-route.service.spec.ts:23` — test routes cover `/home`, `/design` and `/acme/design`, but not similarly prefixed sibling routes.
- **Why it is debt:** Route ownership is approximated by a string prefix rather than the router's path segments. `/designer`, `/design-system` or `/acme/designated` can therefore activate design-mode UI even though they are outside the configured design branch. This affects consuming host toolbars/navigation through the public signal; it is not evidence of an authorization bypass.
- **Suggested refactoring:** Match the parsed primary-outlet path segments exactly through the configured prefix and `design` segment, allowing genuine descendants and ignoring query/fragment decoration. Add sibling-prefix negative cases alongside the existing locale-serializer and tenant-prefix tests.
- **Depends on / blocks:** none

### FDN-ABS-01 — Published package omits directly imported framework peers

- **Category:** ABS
- **Criticality:** 3
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 3.0
- **Evidence:**
  - `libs\js-shared\design\src\lib\design.routes.ts:4` — production routes directly import `BASE_ENTITY_AUTHORING_ROUTES` from `@processpuzzle/base-entity`.
  - `libs\js-shared\design\src\lib\design-route\design-route.service.ts:4` — the public route-awareness service directly imports `DESIGN_ROUTE_PREFIX` from `@processpuzzle/util`.
  - `libs\js-shared\design\src\lib\design-sidenav\design-sidenav.component.ts:4` — the public sidenav also directly imports util's layout service and prefix token.
  - `libs\js-shared\design\package.json:18` — the peer list names the other composed feature packages but omits both `@processpuzzle/base-entity` and `@processpuzzle/util`; dependencies at lines 34–35 contain only tslib.
  - `libs\js-shared\design\src\public-api.ts:10` — routes and the util-dependent service/sidenav are publicly re-exported at lines 10–12.
- **Why it is debt:** The library's real import boundary is larger than its published installation/version contract. It relies on a workspace or another peer's transitive installation to make two directly imported packages available and compatible, so consuming it under strict dependency resolution or evolving peer versions can fail outside the monorepo. The concrete debt is the omitted declarations; no current build or package-manager failure is claimed without a consumer test.
- **Suggested refactoring:** Declare the two direct framework peers with compatible supported ranges using the workspace's package-management conventions. Add a packaged-consumer smoke check that imports the public route/service surface without relying on root workspace resolution, and keep manifest dependency validation alongside publishing checks. This estimate includes consumer validation rather than only editing two manifest entries.
- **Depends on / blocks:** none

## Out-of-scope observations

- Imported entity/rule/state/workflow/event/widget/starter implementations, shared util internals and host registration were not inspected. Their editing state, access control and transport behavior are unscored.
- `libs\js-shared\design\src\lib\design.routes.ts:38` explicitly delegates facade-provider assembly to the host; this review records that integration requirement without claiming any particular host omitted it.
- Public route composition statically imports sibling feature route arrays. No bundle analysis was executed, so there is no scored assertion about actual chunk size or lazy-loading cost.

## Strengths worth keeping

- `libs\js-shared\design\src\lib\design-route\design-route.service.ts:19` listens to successful navigations, cleans up with `takeUntilDestroyed` and exposes a read-only signal rather than an external mutable subject.
- `libs\js-shared\design\src\lib\application-designer\application-designer.component.ts:31` uses child-route links and an outlet, preserving deep links and browser history while drilling into forms.
- `libs\js-shared\design\src\lib\application-designer\application-designer.component.ts:28` places its translation scope on the directive instead of overriding child feature injectors' scope collections.
- `libs\js-shared\design\src\lib\design-sidenav\design-sidenav.component.ts:43` derives the sidebar from top-level route metadata instead of maintaining a separate section list.
- `libs\js-shared\design\src\lib\design.routes.spec.ts:36` checks that every application tab has a mounted route; similar workflow checks preserve composition without reaching into feature internals.
