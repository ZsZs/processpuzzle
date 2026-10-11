# Architecture review: base-widget-frontend

- **Slice code:** FWI
- **Paths in scope:** `libs\js-shared\base-widget-frontend`
- **Reviewed at commit:** `5714af4efcc879e0a6d5ae6935588e0cec65dbfb`
- **Date:** 2026-10-10
- **Tools run:** IDE file reads, scoped glob/rg, source/test/manifest inspection, and read-only report-reference/schema checks. Static review only; tests inspected, not executed. No build, lint, package-consumer installation, or runtime stack was run.
- **Calibration:** Human/reviewer calibration was not performed; effort estimates are provisional. Rubric scales are unchanged.

## Summary

The registry, shared host, application-context token and scoped theme service establish sensible boundaries between reusable widgets and their aggregators. Signal inputs and computed projections are used deliberately, while contract and mapper tests protect important metadata invariants. The shared host nevertheless treats two different placement identities as the same component lifetime, retaining output callbacks addressed to the old placement. The published package also leaves direct runtime dependencies undeclared, and the photo album does not reconcile selection with replacement image data. These are ownership and evolution gaps, not objections to RxJS or intentional metadata-schema subsets.

## Findings table

| ID | Title | Cat | Crit | Blast | Effort | Conf | Priority |
|---|---|---|---|---|---|---|---|
| FWI-STA-01 | Placement identity is absent from the host lifetime boundary | STA | 3 | 3 platform | M | High | 4.5 |
| FWI-ABS-01 | Published runtime imports are absent from the package dependency contract | ABS | 3 | 3 platform | M | High | 4.5 |
| FWI-STA-02 | Photo selection outlives the collection it indexes | STA | 2 | 2 feature | M | High | 2.0 |

## Findings

### FWI-STA-01 — Placement identity is absent from the host lifetime boundary

- **Category:** STA
- **Criticality:** 3
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.5
- **Evidence:**
  - `libs\js-shared\base-widget-frontend\src\widget-host\widget-host.component.ts:68` — the remount key includes type, input names and output bindings, but not placement ID.
  - `libs\js-shared\base-widget-frontend\src\widget-host\widget-host.component.ts:77` — one effect mounts from that key; another pushes changed input values into the retained instance.
  - `libs\js-shared\base-widget-frontend\src\widget-host\widget-bindings.ts:60` — output callbacks capture the mounted widget object and emit its ID.
  - `libs\js-shared\base-widget-frontend\src\widget-host\widget-host.component.spec.ts:52` — value-update coverage intentionally preserves an instance, but uses the same placement ID.
- **Why it is debt:** Replacing a host input from placement A to B with the same type, input names and output bindings updates displayed values without remounting or rebinding outputs. The resulting event contains A's ID while presenting B's data, and local component state also crosses that identity boundary. This affects the shared hosting foundation for app/document consumers; a container that always destroys hosts on ID changes avoids it, but that lifetime requirement is neither enforced nor part of the host input contract.
- **Suggested refactoring:** Include placement identity in the component-lifetime key, retaining value-only updates for the same identity. Add a host test that replaces A with B, emits an output, and asserts both the new ID and fresh local state; keep the existing same-ID preservation tests.
- **Depends on / blocks:** none

### FWI-ABS-01 — Published runtime imports are absent from the package dependency contract

- **Category:** ABS
- **Criticality:** 3
- **Blast radius:** 3 platform
- **Effort:** M
- **Confidence:** High
- **Priority score:** 4.5
- **Evidence:**
  - `libs\js-shared\base-widget-frontend\src\app-property\app-property.ts:2` — the production entity imports `uuid`.
  - `libs\js-shared\base-widget-frontend\src\app-property\app-property.store.ts:2` — the production signal store imports `@angular-architects/ngrx-toolkit`.
  - `libs\js-shared\base-widget-frontend\src\widget-definition\widget-definition.store.ts:2` — widget-definition publishing also imports the toolkit.
  - `libs\js-shared\base-widget-frontend\src\public-api.ts:46` — the application-property store is publicly exported; the widget-definition stores are exported at line 97.
  - `libs\js-shared\base-widget-frontend\package.json:13` — neither package appears in the peer dependencies; the dependencies section at line 35 contains only `tslib`.
- **Why it is debt:** The independently published widget package relies on dependencies that its own install contract does not request. Hoisted workspace packages or incidental transitive dependencies can satisfy those imports locally while a different consumer layout or upstream dependency change cannot. The blast radius is platform-wide because this is a shared published building block, although no isolated-consumer failure was executed during this review.
- **Suggested refactoring:** Declare each runtime import as a supported peer or owned dependency according to the package's distribution policy. Add a packed-package consumer check outside the workspace dependency tree so publication validation exercises the actual installation contract.
- **Depends on / blocks:** none

### FWI-STA-02 — Photo selection outlives the collection it indexes

- **Category:** STA
- **Criticality:** 2
- **Blast radius:** 2 feature
- **Effort:** M
- **Confidence:** High
- **Priority score:** 2.0
- **Evidence:**
  - `libs\js-shared\base-widget-frontend\src\photo-album\photo-album.component.ts:160` — independently owned `selectedIndex` is used directly to index the current `images` input, without reconciliation.
  - `libs\js-shared\base-widget-frontend\src\photo-album\photo-album.component.ts:13` — the embedded image and its navigation controls are all conditional on a valid current image.
  - `libs\js-shared\base-widget-frontend\src\photo-album\photo-album.component.ts:27` — the viewer and close button also disappear when the current image becomes absent.
  - `libs\js-shared\base-widget-frontend\src\photo-album\photo-album.component.ts:186` — opening acquires a sidenav-restoration callback, released only on close or destruction.
  - `libs\js-shared\base-widget-frontend\src\photo-album\photo-album.component.spec.ts:28` — viewer navigation/closing is tested against an unchanged image collection.
- **Why it is debt:** After selecting the second image, replacing the input with a one-image collection makes `currentImage` undefined and removes all embedded navigation, despite valid content remaining. If the viewer was open, it also removes its close button without releasing the acquired layout state; Escape or destruction is then needed to restore it. This violates the live-input contract of a widget whose host deliberately retains instances during prop updates and affects its embedding consumers, not just its internal presentation.
- **Suggested refactoring:** Define and implement selection reconciliation when image identity or collection bounds change, including an explicit empty-collection close/release policy. Test shorter replacement, empty replacement while open, and same-placement live updates independently of host remount behavior.
- **Depends on / blocks:** none

## Out-of-scope observations

- Aggregator routing, authorization and action dispatch were not inspected. FWI-STA-01 establishes the public host's behavior, not that every current aggregator reuses hosts across IDs.
- `libs\js-shared\base-widget-frontend\src\widget-contract.spec.ts:16` names an external backend seed catalogue. Its cross-layer assertion was inspected, but the backend file was not read and catalogue equality was not executed.
- The generic base-entity persistence implementation and server-side concurrency handling are outside scope. No lost-update or authorization finding is inferred from calls to those abstractions.
- This package's undeclared imports are its own dependency contract; the similar previously recorded FDN-ABS-01 is a systemic packaging theme, not a prerequisite or the same manifest defect.

## Strengths worth keeping

- The registry composes sibling registrations and parent injectors, rejects conflicting components, and imports no aggregator: `libs\js-shared\base-widget-frontend\src\widget-registry\widget-registry.token.ts:78`.
- Application context is a narrow, signal-based interface rather than an import of the app-shell implementation: `libs\js-shared\base-widget-frontend\src\app-context\application-context.ts:28`.
- Scoped theme state does not modify the document; root application is separated, and storage parsing tolerates obsolete values: `libs\js-shared\base-widget-frontend\src\themes-button\theme.service.ts:55`; the scoped isolation test is `libs\js-shared\base-widget-frontend\src\themes-button\theme.service.spec.ts:69`.
- Component inputs/outputs are checked against registered metadata: `libs\js-shared\base-widget-frontend\src\widget-contract.spec.ts:30`.
- Mapper tests explicitly protect unrendered nested metadata during a full round trip: `libs\js-shared\base-widget-frontend\src\widget-definition\widget-definition.mapper.spec.ts:34`.
