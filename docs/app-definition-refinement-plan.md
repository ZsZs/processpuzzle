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
| 8 | Registry token + `WidgetInstance`: one lib or two? | **One lib.** `WIDGET_REGISTRY`, `WidgetInstance` and the concrete widget components all live in `base-widget-frontend`. `base-document-frontend` takes a dependency on it, because a document can embed widgets. No separate `base-widget-core`. |

### Building blocks vs aggregators

The layering rule the whole widget design follows:

> **Widgets are building blocks. Apps and documents are aggregators.** Both aggregators can embed
> widgets; neither is embeddable *into* a widget.

So `base-widget-frontend` sits low in the dependency graph — below both `base-app-frontend` and
`base-document-frontend` — and may never depend on either. A widget that renders an aggregator's
content is owned by that aggregator, not by the widget library: **`document-viewer` belongs in
`base-document-frontend`**, because it is what embeds a document *into an app*, not something you
embed into a document. It registers itself through `provideDocumentViewerWidget()`, which is exactly
what `provideWidget()`'s composability is for.

Test for where a new widget belongs: *is this a building block, or does it surface an aggregator's
content?* Building block → `base-widget-frontend`. Surfaces an aggregator → that aggregator's lib.

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

**Progress: 2a (rename) ✅ · 2b (registry move) ✅ · 2c (infrastructure split) ✅ · 2d (widget
registration) ✅ — Phase 2 complete (2026-08-13).** Split into four independently verifiable steps
rather than one commit, because the infrastructure split changes `util`'s dependency surface.

- **2a — pure rename.** `widgets` → `base-widget-frontend`, `@processpuzzle/widgets` →
  `@processpuzzle/base-widget`. All 9 registration sites, both workflows (renamed), the Sonar key,
  the theme path, and ~25 importers. nx tags aligned to the `base-*` convention
  (`scope:base-widget` / `type:domain`). The three dependents' peer ranges were pointing at
  versions that will never exist under the new name (`^0.3.1`, `^0.8.0`, `^0.8.3`) and are now
  `^0.9.1`, the carried-over version.
- **2b — registry move.** `widget-registry/` (token + `widget-instance.ts`) moved from
  `base-entity-frontend` to `base-widget-frontend`; `base-document-frontend` gained the
  `@processpuzzle/base-widget` peer dep. `base-entity-frontend` now has **no** widget
  responsibility, which is what the token's placement comment had been asking for. Layering
  verified: `base-widget-frontend` imports nothing from base-app or base-document.

- **2c — infrastructure split.** `navigate-back`, `error-snackbar` and `transloco/*` moved to `util`,
  which gained `@angular/material`, `@angular/router` and `@jsverse/transloco` peer deps and its
  first `test-setup.ts` (it had no component specs before, so jest-dom's matchers were unregistered
  and both moved specs failed on `toHaveClass` / `toHaveTextContent`). `auth` now depends only on
  `util` and dropped `@processpuzzle/base-widget` entirely.
- **2d — widget registration.** Six widgets registered in `base-widget.providers.ts` under semantic
  keys (`cards-grid`, not `mat-cards-grid` — that it is built from Material cards is not something a
  designer picking a widget should have to know). Per-widget `provide*Widget()` plus a
  `provideBaseWidgets()` convenience, because an app should be able to allow a share button without
  a language selector. **The keys are contract**: renaming one orphans every stored `WidgetInstance`
  that references it.

### Two deviations from the plan, both from reading the code

1. **`app-property` stays in `base-widget-frontend`.** The plan said move it to `util`; that is
   impossible. It is a full base-entity domain entity (`BaseEntityStore`, `BaseEntityContainerStore`,
   `BaseEntityTabsStore`), so `util` would have to depend on `base-entity`, which already depends on
   `util` — a cycle. It is not a widget and does not really belong here either; a proper home is an
   open question, not urgent.
2. **`design-button` stays too.** The plan said move it to `design`. Reading it, it is a
   `/home` ⇄ `/design` toggle for the app-shell header whose only consumer is the testbed — it knows
   nothing of the `design` lib's domain, and moving it there would make the shell header depend on a
   feature lib for a button. It also hardcodes both routes, which is really app configuration; that
   smell is worth revisiting, but not by relocating it into the wrong library.

### Defect found and fixed: `provideWidget` did not compose within one injector

`provideWidget` merged via `@Optional() @SkipSelf()` alone, and its doc claimed "multiple calls
compose". `SkipSelf` skips the *injector*, not the sibling provider — so N calls in the **same**
injector each resolved an empty parent and the last one won. Registering all six widgets yielded a
registry containing exactly one. It stayed invisible because nothing had ever been registered.

Fixed by adding an internal `multi: true` `WIDGET_REGISTRATIONS` token: `multi` collects siblings
within an injector, `@SkipSelf()` still chains across injectors, so both directions now compose.
`provideWidget` returns `Provider[]` instead of `Provider` (assignable, so callers are unaffected).
Covered by three specs, including the cross-injector merge an aggregator relies on.

⚠️ **The transloco scope is deliberately still `widgets`**, not renamed with the library. It is a
runtime i18n namespace with a deployment footprint — it names the served asset path
(`assets/i18n/widgets/*.json`) and every `base_widget.*`-style key would shift with it. Renaming it
also trips the alias trap: `provideTranslocoScope({ scope: 'base-widget' })` camelCases to a default
alias of `baseWidget`, so the rename would have to set `alias` explicitly everywhere. Worth doing as
its own change; not smuggled into a library rename.

⚠️ **The Sonar project must be created before CI runs green.** `sonar-project.properties` now says
`processpuzzle_base_widget_frontend`, but that project does not exist on SonarCloud yet — the old
`processpuzzle_widgets` does. Creating it is an outward-facing action; do it deliberately.

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
5. Move `WIDGET_REGISTRY` **and** `widget-instance.ts` out of `base-entity-frontend` into this lib.
   Both were parked there only because base-app and base-document both need them while neither
   depends on the other, and `base-entity-frontend` was the one lib both already had — the token's
   own placement comment says as much. This lib is the right home.

   Per decision 8 they live **alongside the concrete widget components**, not in a separate
   `base-widget-core`. The consequence is deliberate: **`base-document-frontend` gains a dependency
   on `@processpuzzle/base-widget`**, which is honest — a document can embed widgets. It costs
   nothing at runtime; `sideEffects: false` keeps unreferenced components out of the bundle, so the
   weight is in the dependency graph, not the payload.

   Concretely: add `@processpuzzle/base-widget` to `base-document-frontend`'s `peerDependencies`,
   and drop the two widget exports from `base-entity-frontend`'s `public-api.ts` (added in Phase 1).
   Re-point every importer — `base-app-frontend`'s `app-definition.ts` and descriptors,
   `base-document-frontend`'s `base-document.ts` — from `@processpuzzle/base-entity` to
   `@processpuzzle/base-widget`. `base-entity-frontend` then has no widget responsibility at all,
   which is the cleanup the token's comment asked for.
6. Theme path `src/theme/pp-colors.css` changes → update `apps/processpuzzle-testbed/project.json`
   styles and the README theming section.
7. Rename the Sonar project (`processpuzzle_widgets` → `processpuzzle_base_widget_frontend`) via the
   admin API; rename both GitHub workflows.

**Blast radius outside the lib:** 8 files in `auth`, 1 in `design`, ~4 in `processpuzzle-testbed`,
`base-entity-frontend` (`public-api.ts` + the `widget-registry/` directory moves out),
`base-document-frontend` and `base-app-frontend` (imports re-pointed, `package.json` deps),
`tsconfig.base.json`, `nx.json`, root `package.json`, `release-js-lib.mjs`, `README.md`.

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
