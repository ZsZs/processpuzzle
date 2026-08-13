# AppDefinition Refinement Plan

Status: **accepted, in progress** · Agreed 2026-08-13 · Owner: ZsZs

Refines `base-app`'s `AppDefinition` around four decisions: routing becomes the spine of content
definition, `Module` is introduced as the unit of decomposition, widget *definitions* become a
standalone resource distinct from widget *configuration*, and tabs are solved with existing
mechanisms rather than a new `base-content` concept.

## Decisions

| # | Question | Decision |
| --- | --- | --- |
| 1 | What drives content definition? | The **route tree**. A route names its target: widgets, a document, an entity, or a module. |
| 2 | How is a large app decomposed? | **`ModuleDefinition`** — a route subtree + its own transloco scope + its own resource namespace, stored as its own aggregate. |
| 3 | Design-time vs run-time widgets | Three tiers: **`WidgetDefinition`** (the type, versioned), **`WidgetInstance`** (a placement with bound props, lives in its container), **widget state** (per-user/per-record, out of scope for now). |
| 4 | Region contribution per module | **Kept simple.** Sidenav items live in `AppDefinition`. Modules stay loosely coupled: a `NavItem` references a route by *path string*, and a dangling reference is a validation **warning**, not an error. |
| 5 | Where does `WidgetDefinition` live? | A new **`base-widget`** lib pair. Widget count is expected to grow, especially from contributors. |
| 6 | Name collision with `libs/js-shared/widgets` | Migrate that lib into **`base-widget-frontend`**, splitting infrastructure out (see Phase 2). |
| 7 | Tabs | **No new concept.** Deep-linkable tabs are child routes; ephemeral tabs are a container widget. `base-document` stays closed textual/image content. |

### Rationale for the tier split (decision 3)

The axis is not "static vs dynamic" but *does this travel with the app's published version, or is it
data?* Configuration → versioned with its container. Content → `base-document`, which already has its
own `DRAFT`/`PUBLISHED` lifecycle per translation. Per-user state → its own storage, deferred.

## Phase 1 — Unify the widget instance schema ✅ DONE (2026-08-13)

**Contract-first, no lib changes.** `WidgetRef` (base-app, local copy) and the widget half of
`DocumentBlock` (base-document) were the same thing with divergent fields — `DocumentBlock` carried
`inputBindings`/`outputBindings`, `WidgetRef` did not.

`shared-api.yaml` already declared a `WidgetRef` documented as the shared one, but **nothing
referenced it**. This phase made that declaration real.

**Sharing mechanism:** local declaration in each spec + `schemaMappings` onto
`com.processpuzzle.shared.model.WidgetInstance` — the pattern `ErrorResponse` and `ImportResult`
already use. Cross-file `$ref` is deliberately **not** introduced: no spec uses it today, and the
generator's `$ref` handling has bitten this workspace on Windows before. The YAML text stays
duplicated; the *generated Java type* becomes single.

### What shipped

- `shared-api.yaml`: `WidgetRef` → `WidgetInstance`, gaining `inputBindings` / `outputBindings`.
- `base-app-api.yaml`: local `WidgetRef` → `WidgetInstance` + `schemaMappings` entry. **Verified:**
  `app/model/WidgetRef.java` is no longer generated at all and `PageDefinition.widgets` binds to
  `List<com.processpuzzle.shared.model.WidgetInstance>`.
- Java: `AppMapper`, `AppDefinitionValidator` and four test classes renamed onto the shared type;
  stale comments in `domain/Widget.java` and `domain/WidgetPlacement.java` corrected.
- Frontend: `WidgetInstance` + `WidgetPlacement` now live in
  `base-entity-frontend/src/lib/widget-registry/widget-instance.ts`, beside `WIDGET_REGISTRY` and for
  the same stated reason. base-app re-exports them; `DocumentBlock` is now
  `interface DocumentBlock extends Partial<WidgetInstance>`. `widget-ref.descriptors.ts` renamed.
- `WidgetPlacement` is a const-object merged with a type of the same name, so
  `WidgetPlacement.STANDALONE` (base-document's existing style) and the `WIDGET_PLACEMENTS` array
  (`toSelectables`, base-app's style) both come off one declaration. A TS `enum` gives only the first.

Verified green: 289 base-app-backend + 312 base-document-backend Java tests, 712 frontend tests
across the three libs, lint (0 errors), and all three library builds.

### Two findings that changed the plan

1. **`props` keeps its `new HashMap<>()` initialiser, and that is correct.** The memory-flagged
   free-form-object trap is real, but base-app's `WidgetRef.props` *already* behaved this way, so
   "fixing" it here would have been an unrelated wire change. The lever is `nullable: true`, which
   base-document already used — applied to the two **new** binding fields only, so base-app payloads
   do not gain two permanently-empty maps.
2. **base-document cannot map onto the shared DTO.** `DocumentBlockInput` is deliberately flat and
   relaxes requiredness (a TEXT block has neither `id` nor `type`; a `WidgetInstance` requires both).
   So it keeps a hand-maintained copy, now labelled as such in the contract with an explicit
   "copies drift, mirror changes by hand" warning. **Nesting the widget fields under a `widget`
   property is the real fix and remains open** — it is a breaking payload change across the Tiptap
   editor, `DocumentContentStore`, the Java `DocumentBlock` record and the Firebase functions.
   The frontend `Partial<WidgetInstance>` is the half of the unification that *was* achievable now.

## Phase 2 — `base-widget-frontend` (rename + split)

Create the lib **by renaming `widgets`**, not from scratch: every registration file the new-lib
checklist demands already exists for `widgets` and gets edited rather than added.

1. `cp -r` then `rm -rf` — directory rename is denied on this Windows setup.
2. Package `@processpuzzle/base-widget`; the tsconfig paths key must be **byte-identical** to it, or
   ng-packagr crashes. Nx project name `base-widget-frontend`.
3. **Move infrastructure out** — `navigate-back` (imported by 8 files in `auth`), `error-snackbar`,
   `transloco/*`, `app-property` → `util`; `design-button` → `design`. Requires adding
   `@angular/material` and `@jsverse/transloco` to `util`'s peer deps, which it currently lacks.
4. **Keep and register as widgets** — `mat-cards-grid`, `markdown-page`, `like-button`,
   `share-button`, `version-button`, `language-selector`; a `provideXWidget()` per component.
5. Move `WIDGET_REGISTRY` out of `base-entity-frontend` (its own placement comment says it is parked
   there only for want of a better home) into this lib.
6. Theme path `src/theme/pp-colors.css` changes → update `apps/processpuzzle-testbed/project.json`
   styles and the README theming section.
7. Rename the Sonar project (`processpuzzle_widgets` → `processpuzzle_base_widget_frontend`) via the
   admin API; rename both GitHub workflows.

**Blast radius outside the lib:** 8 files in `auth`, 1 in `design`, ~4 in `processpuzzle-testbed`,
3 dependent `package.json`s, `tsconfig.base.json`, `nx.json`, root `package.json`,
`release-js-lib.mjs`, `README.md`.

⚠️ `@processpuzzle/widgets` is published at 0.9.1 — the rename breaks external consumers. Pre-1.0, so
hard-rename rather than dual-publish.

> Note on the split: only 6 of the 13 public-API exports are widget-registry candidates. A wholesale
> rename would put the transloco loader and error snackbar in a lib called "base-widget", a worse name
> for them than "widgets" was. Hence step 3.

## Phase 3 — `base-widget-backend` + the `WidgetDefinition` resource

The genuinely new library; full 13-file registration checklist applies.

```
WidgetDefinition {
  key, name, description, category, icon,
  propsSchema,            // JSON Schema — drives the designer's generated props form
  inputPorts[], outputPorts[],
  status, version, orgKey
}
```

Port shapes reuse `base-document`'s existing `PortType` / `AttributeVisibility`, promoted into the
shared spec rather than duplicated.

Modulith module `widget` under `com.processpuzzle.widget`. Verify with
`mvn test -pl apps/processpuzzle-backend -Dtest=ModularityTests` and grep the log for a
`# Base Widget` heading.

**The payoff to build for:** `propsSchema` turns widget props into a *generated form* in the designer
— the same descriptor machinery `base-entity` already has — instead of a raw JSON editor. Prototype
this within the phase to prove the schema is expressive enough before Phase 4 depends on it.

## Phase 4 — Route tree replaces `pages`

- `PageDefinition[]` → recursive `RouteDefinition[]`:

```
RouteDefinition { path, title, translocoId, icon?, roles?, target, children? }
target: { kind: WIDGETS,  widgets: WidgetInstance[] }
      | { kind: DOCUMENT, documentKey }
      | { kind: ENTITY,   entityName, mode: LIST | DETAILS }
      | { kind: MODULE,   moduleKey }
```

- `NavItem.pageId` → `NavItem.routePath`.
- **Drop `RegionType.content`** — already vestigial: `widgets` is header/footer-only and `navItems`
  sidenav-only, leaving `content` a region with no field. Once routes own content, the content region
  *is* the router outlet.
- Endpoint `/app-definitions/{appId}/pages/{pageId}` → `/routes/{routePath}`.
- `AppDefinitionValidator` grows: duplicate sibling segments, dangling document/entity refs, nav items
  pointing at nonexistent routes.
- `base-app.routes.ts`: the `APP_PAGE` branch becomes a self-nesting `APP_ROUTE` branch, same thunk
  pattern `navItemRoute()` already uses.

⚠️ **Prerequisite, not a footnote:** the open self-nesting defect — a self-referential
`EMBEDDED_COMPONENTS` attribute saves a row as its own child — will hit `RouteDefinition`. Fix it
first or this phase ships broken.

Hard data migration, no back-compat: base-app frontend is a scaffold per the README maturity table.
Touches `DefaultAppLoader`, `ImportAppDefinitions`, sample data, `tools/mock-backend/db.json`, and the
testbed e2e specs.

## Phase 5 — `ModuleDefinition`

Own aggregate: `{ key, orgKey, basePath, routes[], translocoScope, status, version }`.
`AppDefinition` gains `modules: [{ moduleKey, basePath }]`. Sidenav stays wholly in `AppDefinition`
(decision 4).

Transloco scope per module, with `alias` **always** set explicitly — both `-` and `_` in a scope name
get camelCased into a wrong default.

**Be honest in the docs about "lazy":** metadata, documents, descriptors and translations load lazily;
widget *code* is bundled at compile time via the registry. A metadata module cannot lazy-load
components unless it also maps to a real Angular `loadChildren` over a lib. The genuine win is that
`AppDefinition` stops being one aggregate that must be loaded, locked and published atomically — and
that a module becomes the unit of authoring permission and versioning. Do not justify Module on a
performance claim it cannot cash.

## Phase 6 — Tabs

No new schema.

- **Deep-linkable** (URL per tab, back button works) → child `RouteDefinition`s + a `tab-container`
  widget rendering `<router-outlet>`.
- **Ephemeral** (no URL) → container widget, `props.tabs: [{ label, translocoId, childId }]`, children
  marked `REFERENCED`. This is what `childIds` / `REFERENCED` was built for.

Ship `tab-container` as a `base-widget` widget with a real `propsSchema` — the best test of whether
Phase 3's schema is expressive enough.

## Sequencing

1 → 2 → 3 → 4 → 5, with 6 after 4. Phases 2 and 3 can run in parallel with 4 (disjoint files) **if**
the embedded self-nesting defect is fixed first.

## Explicitly out of scope

- **Tier-3 runtime widget state** (per-user persisted widget state). Nothing needs it yet, and it
  wants its own storage decision.
- **Region contribution per module** — rejected in favour of decision 4's simpler model. Revisit only
  if modules genuinely need to own their own sidenav.
