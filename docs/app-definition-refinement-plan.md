# AppDefinition Refinement Plan

Status: **accepted, in progress** · Agreed 2026-08-13 · Owner: ZsZs

Refines `base-app`'s `AppDefinition` around four decisions: routing becomes the spine of content
definition, `Module` is introduced as the unit of decomposition, widget *definitions* become a
standalone resource distinct from widget *configuration*, and tabs are solved with existing
mechanisms rather than a new `base-content` concept.

## Decisions

| # | Question | Decision |
| --- | --- | --- |
| 1 | What drives content definition? | **Routing.** A route names its target: widgets, a document, or an entity. |
| 1b | Nested or flat routes? | **Flat.** `path` may be multi-segment (`claims/open`); there is no `children` array. Angular nesting is *derived* at registration time where it is genuinely needed. See below. |
| 2 | How is a large app decomposed? | **`ModuleDefinition`** — its own flat route list + transloco scope + resource namespace, its own aggregate, mounted by the app at a `basePath`. This is the *only* recursion in the system. |
| 3 | Design-time vs run-time widgets | Three tiers: **`WidgetDefinition`** (the type, versioned), **`WidgetInstance`** (a placement with bound props, lives in its container), **widget state** (per-user/per-record, out of scope for now). |
| 4 | Region contribution per module | **Kept simple.** Sidenav items live in `AppDefinition`. Modules stay loosely coupled: a `NavItem` references a route by *path string*, and a dangling reference is a validation **warning**, not an error. |
| 5 | Where does `WidgetDefinition` live? | A new **`base-widget`** lib pair. Widget count is expected to grow, especially from contributors. |
| 6 | Name collision with `libs/js-shared/widgets` | Migrate that lib into **`base-widget-frontend`**, splitting infrastructure out (see Phase 2). |
| 7 | Tabs | **No new concept.** Deep-linkable tabs are child routes; ephemeral tabs are a container widget. `base-document` stays closed textual/image content. |
| 8 | Registry token + `WidgetInstance`: one lib or two? | **One lib.** `WIDGET_REGISTRY`, `WidgetInstance` and the concrete widget components all live in `base-widget-frontend`. `base-document-frontend` takes a dependency on it, because a document can embed widgets. No separate `base-widget-core`. |

### Flat routes, derived nesting (decision 1b)

The constraint driving this: **no giant nested routing system.** A recursive `RouteDefinition` with a
`children` array would put an arbitrarily deep tree inside one aggregate — hard to author, hard to
show in a designer, and hard to reason about. So:

- A route's `path` may be multi-segment. `claims`, `claims/open` and `claims/:id` are three sibling
  entries in one flat list, not a three-level tree.
- Angular's real nesting — a parent route hosting a `<router-outlet>` for deep-linkable tabs — is
  **derived** by the route builder from path prefixes at registration time. Nesting is a rendering
  concern, so it is computed, not authored.
- **Decomposition happens at the module boundary, not by nesting.** A `ModuleDefinition` owns its own
  flat route list and the app mounts it at a `basePath`. That is the one place structure recurses,
  and it recurses exactly one level: a module does not mount modules.
- **Menu hierarchy is a separate axis** and already exists: `NavItem` nests, because a collapsible
  sidenav group is presentation, not routing. Conflating the two is what would have forced routes to
  nest in the first place.

Two consequences worth stating plainly:

1. **The embedded self-nesting defect is no longer a Phase 4 prerequisite.** `RouteDefinition` never
   nests in itself, so the defect cannot bite here. It still affects `NavItem`, which does self-nest
   today, and remains worth fixing on its own terms.
2. **`ModuleDefinition`'s contract moves into Phase 4**, because the route model is not coherent
   without it — modules are what makes flat routes sufficient. Phase 5 keeps the runtime wiring
   (lazy metadata loading, per-module transloco scope).

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
   `/home` ⇄ `/design` toggle for the app-shell header — it knows nothing of the `design` lib's
   domain, and it is a button, which is a building block. Confirmed by the owner after a brief
   round-trip. Its hardcoded `/home` and `/design` routes are really app configuration; that smell
   is worth revisiting, but not by relocating it into the wrong library.

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

**Phase 3 complete (2026-08-13): 3a (contract) ✅ · 3b (Maven module) ✅ · 3c (registration) ✅ ·
3d (props form prototype) ✅.**

### 3a — contract ✅ (2026-08-13)

- **Ports promoted to `shared-api.yaml`**: `PortType`, `AttributeVisibility`, and new canonical
  `InputPort` / `OutputPort`. Two features declare ports — a widget type declares what it offers, a
  document declares what its content exposes — and a `WidgetInstance`'s bindings are what join them.
- **`base-widget-api.yaml`**: `WidgetDefinition` CRUD + publish, `WidgetKey` pattern,
  `WidgetDefinitionStatus`, paged list. Generates into `com.processpuzzle.widget.{api,model}`.
- **Verified**: `widget/model/` contains only the four widget types — ports, `ErrorResponse` and
  `OrganizationKey` all bind to shared/JDK types via `schemaMappings`, so nothing is duplicated.
- **`propsSchema` generates as a bare `Map<String, Object>`** with no `new HashMap<>()`, because it
  is declared `nullable: true`. That distinction is load-bearing: null means "props unconstrained"
  (the honest state for an undescribed widget), whereas `{}` would assert the widget takes no props.

`base-document` still generates `DocumentInputPort` / `DocumentOutputPort` from identical shapes.
Converging them onto the shared types renames a generated Java type and its frontend class, so it is
a follow-up mapping change, deliberately not part of introducing this module.

### 3b — the module ✅ (2026-08-13)

`libs/java-shared/base-widget-backend`, Modulith module `widget` under `com.processpuzzle.widget`,
`allowedDependencies = {"core", "shared"}` — **no `app`, no `document`, and none may be added**:
widgets are building blocks, aggregators depend on them and not the reverse.

Shape notes, each a deliberate departure from base-app's:

- **One `WidgetDefinitionCrud` service, not six use-case classes.** base-app splits them because each
  carries real behaviour (graph conversion, rule validation, publish snapshotting). These are plain
  CRUD over one row with one shared validation routine; six near-empty classes would be ceremony.
  Splitting one out later is a rename.
- **One `Port` record for both directions.** The contract separates `InputPort` / `OutputPort`
  because an input has `required` / `defaultValue` / `defaultRsqlFilter`; in the domain those are
  simply null on an output. Nothing here branches on direction, so `WidgetMapper` is where the
  distinction is re-established.
- **`version` is a plain column, not `@Version`** — same reasoning as `AppDefinition.revision`:
  status is derived as `publishedVersion == version`, and Hibernate would bump a managed version on
  the publish flush itself, reporting unpublished edits on every freshly published widget. Pinned by
  `WidgetDefinitionTest`.
- **`propsSchema` is stored verbatim and never validated**, and a test asserts a structurally
  nonsensical schema is accepted — so a future "helpful" schema check breaks a test rather than
  quietly changing the contract.

17 module tests green.

### 3c — registration ✅ (2026-08-13)

Root pom (module + `base-widget-backend.version` + dependencyManagement), `release-java-lib.mjs`,
`build`/`release-base-widget-backend.yml`, `sonar-project.properties`, nx `project.json`, and the
backend app's pom dependency + `implicitDependencies`.

The nx `project.json` deliberately omits `-am` from **both** `build` and `test`. The `base-state-backend`
template it was copied from uses `mvn -pl … -am test`, which is the pattern that races on shared
upstream modules like `api-contracts` under parallel `run-many`.

**Verified the way the checklist demands**: `mvn test -pl apps/processpuzzle-backend
-Dtest=ModularityTests` prints `# Base Widget` among the detected modules and passes, so the module
is genuinely wired rather than merely compiling. Full backend suite green.

⚠️ SonarCloud project `processpuzzle_base_widget_backend` also needs creating, alongside
`processpuzzle_base_widget_frontend`.

### 3d — props form prototype ✅ (2026-08-13)

`propsSchemaToDescriptors()` in `base-widget-frontend/src/widget-definition/` turns a widget type's
`propsSchema` into base-entity form descriptors, so a designer editing a `WidgetInstance` gets a
typed control per prop instead of a raw key/value editor. Plus a `WidgetDefinition` frontend model.

**The expressiveness question is answered: yes.** Measured against the props of widgets that
actually exist — `entity-grid` exactly as documented in base-app-api.yaml, and `cards-grid` — the
mapping covers string, integer, boolean, string-array, enum, date-format and long-text, producing
TEXT_BOX / TAGS / CHECKBOX / DROPDOWN / DATE / TEXTAREA with `required`, labels, placeholders and
numeric `inputType` all carried through. 13 tests, green first run.

**The fallback is the load-bearing design decision, not a gap.** Only the keywords that map onto a
control are read; `oneOf`, `$ref`, nested object schemas and tuple `items` are not interpreted and
fall back to `ADDITIONAL_PROPERTIES` — the same open editor used when there is no schema at all. A
test asserts every exotic prop is *kept*, because refusing to render or silently dropping a prop
would turn an unrecognised keyword into a data-loss bug. Widening the subset later is safe precisely
because the fallback is never wrong, only less specific.

`hasDescribedProps()` exists to keep two contract states apart that the form must also distinguish:
*no schema* (props unconstrained → open editor) versus *a schema declaring no props* (→ nothing).

**Not yet wired into the live form.** `createWidgetInstanceDescriptor()` builds descriptors
synchronously and has no access to loaded definitions; feeding it one means giving `AppWidgetFacade`
the definition for the instance's `type`, which is a data-loading change rather than a mapping one.
The mapping — the part that answers whether the schema carries enough — is done and tested.

### Remaining

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

## Phase 4 — Flat routes replace `pages`

> ⚠️ **Verify with `clean`.** `mvn compile` / `mvn install` on `api-contracts` **report a false green**
> across a contract change: `generate-sources` does not clean `target/`, so a deleted schema's stale
> `.java` lingers and gets packaged, and `mvn compile` skips incrementally, reusing `.class` files from
> before the change. Only `mvn clean compile` / `clean test-compile` / `clean test` show the truth.
> Two false greens in this work came from exactly that.
>
> A second one, specific to rules: `base-app-backend` reads `sample-rules/processpuzzle-rules.yaml`
> from **base-rule-backend's installed jar**, not from the reactor. Editing that file has no effect on
> `AppRuleValidatorTest` until `mvn install -DskipTests -pl libs/java-shared/base-rule-backend` runs.

**Progress: 4a (contract) ✅ · 4b (Java) ✅ · 4c (frontend) ✅.**

### 4a — contract ✅ (2026-08-13)

- `PageDefinition` → **flat `RouteDefinition`**: `path` (multi-segment, `:param` allowed), `title`,
  `translocoId`, `icon`, `roles`, `target`. **No `children`** — verified in the generated DTO.
- `RouteTarget`, a flat discriminated union on `kind`: `WIDGETS` | `DOCUMENT` | `ENTITY`. Flat rather
  than `oneOf` because the generator turns `oneOf` into an interface plus a class per variant, which
  the frontend's generic form cannot edit — the same reason `DocumentBlock` keeps its kinds
  side by side.
  - **Deliberately no `MODULE` kind.** A module is mounted by the app, not reached through a route
    target; a MODULE target would let a route point into a module that points at another module,
    which is the unbounded structure this design exists to prevent.
- `AppDefinition.pages` → `routes` **+ `modules: ModuleMount[]`** (`moduleKey` + `basePath`).
- **`ModuleDefinition` / `ModuleDefinitionInput` + full CRUD** at `/organizations/{orgKey}/modules`,
  including the lazy `GET /{moduleKey}` the shell calls when something first navigates under a mount.
- `NavItem.pageId` → `routePath`, resolved by string; a dangling one is a **warning**, keeping
  modules loosely coupled (decision 4).
- **`RegionType.content` dropped** — it was already vestigial (`widgets` header/footer-only,
  `navItems` sidenav-only, so `content` had no field of its own). Once routes own content, the content
  area *is* the router outlet; its sizing stays in `LayoutDefinition.contentMaxWidth`.
- `/app-definitions/{appId}/pages/{pageId}` → `/routes/{routePath}`, URL-encoded so a multi-segment
  path stays one variable rather than needing a greedy wildcard that would be ambiguous against its
  siblings.

### 4b — Java ✅ (2026-08-13)

`AppPage` → `AppRoute` (flat, with a `RouteTarget`) and `AppGraph` gaining `routes`/`modules`;
`GetPageDefinition` → `GetRouteDefinition`; a `ModuleDefinition` entity (`@IdClass` on
`orgKey` + `key`, routes in a JSON column), repository and five CRUD use cases; `AppMapper`
(`applyToModule`, `toModel`); `AppDefinitionValidator.validateModule` plus the app-level checks;
`AppEndpoint`'s five module operations. `ProvisionOrganization`'s starter app is now genuinely empty —
`RegionType.content` is gone, so there is no content region to seed. **312 tests pass.**

Two consequences worth remembering, both of them the loose-coupling decision surfacing:

- **`unknown-route-reference` and `orphan-route` are WARNINGs**, so neither blocks a write. Three
  "an invalid definition is rejected" tests had to be rewritten around a structurally blocking error
  (`duplicate-route-path`) instead.
- **A module route nothing links to is not an orphan.** The sidenav that reaches it lives in the app,
  which the module aggregate cannot see; applying the app-level orphan check to `validateModule` would
  mean no module ever validates.

The shipped rules moved with the contract: `page-ids-are-route-safe` → `route-paths-are-route-safe`,
now checking each `/`-separated segment so a multi-segment path passes.

### 4c — Frontend ✅ (2026-08-13)

`app-definition.ts` follows the contract: flat `RouteDefinition` (no `children`), `ModuleMount`,
`NavItem.routePath`, no `RegionType.content`. `page-definition.descriptors` became
`route-definition.descriptors`, joined by `module-mount.descriptors`; both are registered as embedded
entities in `base-app.providers.ts` and reachable from the authoring routes, so the six-entity registry
is `APP_DEFINITION, APP_REGION, APP_ROUTE, APP_MODULE_MOUNT, APP_NAV_ITEM, APP_WIDGET`. Sample data
moved with it — `DefaultAppLoader`, `tools/mock-backend/db.json` (three routes, one of them an authored
prefix of another so the sample exercises derived nesting), the five testbed locales, and the e2e
relationship exclusions.

**`buildAppRoutes` (`feature/app-route-builder.ts`)** is the genuinely new piece. It builds a trie of
`/`-separated segments and emits Angular `Routes` from it, taking a renderer callback for everything
that is not structure:

- an **authored** prefix becomes the Angular parent — its rendered component must host a
  `<router-outlet>`;
- an **unauthored** prefix is folded into its descendants' paths rather than fabricating a parent
  nobody described;
- siblings are sorted **static-prefix-first**, so `claims/new` is matched before `claims/:id`;
- a **module mount** is a component-less parent at `basePath`, with the module's own flat routes run
  through the same derivation beneath it. An unloaded or empty module contributes nothing, which is what
  makes a dangling `moduleKey` a warning rather than a broken shell.

Three form-shaped concessions, each one a real row the generic form can produce: a route with no `path`
is skipped, an absent `path` **field** likewise (a form row is the raw JSON it arrived as), and of two
rows claiming one path the first wins — the backend already rejects the pair with `duplicate-route-path`.

`AppDefinitionMapper` **flattens `RouteTarget` onto the route on read and re-nests it on save**, the
same trick `theme` and `layout` already use: the generic form addresses one property per control and
cannot edit a nested discriminated union.

**Verified:** `base-app-frontend` 16 files / **157 tests** pass (97.05% statements, 93.84% branches;
the route builder at 100%); lint and build green for `base-app-frontend`, `processpuzzle-testbed`,
`processpuzzle-testbed-e2e` and `processpuzzle-ui`. The mock backend's 13 rules are now field-for-field
identical to `sample-rules/processpuzzle-rules.yaml`, so json-server and Spring return the same verdicts
and the same messages.

### Original sketch, for reference

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

## Phase 5 — `ModuleDefinition` ✅ DONE (2026-08-13)

The contract and the Java side shipped with Phase 4 (`/organizations/{orgKey}/modules`,
`ModuleDefinition` + `ModuleDefinitionInput`, `ModuleMount` on `AppDefinition`, the five use cases and
`validateModule`). Phase 5 was the **frontend** half.

### What "lazy" means, precisely

A mount whose module the shell has not already got becomes an Angular **`loadChildren`**, so nothing is
fetched until something navigates under its base path. What that defers is **metadata** — one
`GET /modules/{key}`, and with it the module's routes, its transloco scope and the documents and
descriptors those name. It does **not** defer a *bundle*: widget components are bundled at compile time
and resolved through the frontend registry, so a metadata module cannot split code on its own. The two
real wins are that `AppDefinition` stops being one aggregate that has to be loaded, locked and published
atomically, and that a module becomes the unit of authoring permission and versioning. Nothing here is
justified on bundle size, and the same paragraph is on `ModuleDefinition`'s class doc so the claim cannot
drift.

### What shipped

- **`domain/module-definition.ts`** — `{ id, name, translocoId, description, translocoScope, routes,
  orgKey, version, createdAt, updatedAt }`. The contract's `key` is renamed onto **`id`** by
  `ModuleDefinitionMapper`, because base-entity keys every store, URL and reference on `id`; a second
  identity field beside it could only ever disagree. `moduleTranslocoScope()` is the one place the
  "empty scope means the key" default is applied — resolving it in the mapper would put a scope in the
  form the designer never authored. No `basePath`: where a module is mounted is the mount's business,
  not the module's, which is what lets two apps mount one module at different paths.
- **Mapper, service, store, facade, container** — the routable four, mirroring `AppDefinition`'s, plus
  `RouteTarget` flattening reused verbatim from `AppDefinitionMapper`. Deliberately **no `publish`**:
  status/publication stays an app-level act. The specs assert those absences (`'publish' in store` is
  `false`, the container's `extraFormActionsTemplate` is `undefined`) so a later helpful addition fails
  the build.
- **`module-definition.descriptors.ts`** — routable, not embedded; `id` labelled *Key* with the
  contract's own `^[a-z0-9]+(-[a-z0-9]+)*$`; `routes` an `EMBEDDED_COMPONENTS` attribute pointing at the
  **existing** `App Route` descriptor.
- **`App Route` gained a second parent.** An app's `routes` and a module's are the same rows edited by
  the same descriptor, so `componentParents` is now `[App Definition, Module Definition]` — the only
  level in this graph with two, and the embedded control throws on the module's form without it. The
  registry is therefore two routable definitions over five embedded levels.
- **`BASE_APP_ROUTES`** grew a `module-definition` branch beside `app-definition`, with both transloco
  scopes and only `['app-route']` below the details route: a region is application chrome and a module
  has none.
- **`buildAppRoutes`** takes an optional `loadModule: (key) => Promise<ModuleDefinition | undefined>`.
  A mount already present in `moduleRoutes` is still emitted eagerly (an empty array counts as present
  — the shell knows the module has no routes); otherwise `lazyMount` emits `loadChildren`, which fetches
  once, runs the module's flat routes through the same trie derivation, and hangs them under a
  component-less `path: ''` wrapper. That wrapper exists to carry
  `provideTranslocoScope({ scope, alias: scope })` — the alias is spelled out every time, because both
  `-` and `_` in a scope name get camelCased into a wrong default. An unknown or route-less module
  resolves to `[]`, which is what keeps a dangling `moduleKey` a warning rather than a broken shell.
- **Sample data** — `processpuzzle-testbed-modules` in `tools/mock-backend/db.json` holds
  `order-admin` (scope `order_admin`, two `Order` routes), mounted by the testbed app at
  `basePath: back-office`; plus the five testbed locales and the e2e relationship exclusions.

### A shared-library defect found on the way

`BaseEntityRestService.findById` built its URL from `resourceUrl` **without** the `/%{id}` suffix that
`delete` and `update` use, so it GET the collection and the mapper saw a list where it expected one
record. The module store's lazy read is the first production caller of `findById` — grep found no other —
and the Firestore and embedded implementations of the same interface do address one document, so the
one-line fix makes the three agree. Its own spec had cemented the bug: titled *"builds an id-scoped
URL"* while asserting `endsWith('/node')`. Both fixed.

**Verified:** `base-app-frontend` 23 files / **209 tests** (97.02% statements, 93.6% branches);
`base-entity-frontend` green; lint and build green for both libraries, `processpuzzle-testbed`,
`processpuzzle-testbed-e2e` and `processpuzzle-ui`.

## Phase 5.5 — One Application section, three tabs ✅ DONE (2026-08-13)

A correction to the *design surface*, not to the schema, made after Phase 5 put a second base-app item
in the `/design` sidenav. An application, the modules it mounts and the widget types those place are
three views of one authoring subject, so they share one page:

- `DESIGN_ROUTES` now has a single **Application** section (`/design/application`,
  `ApplicationDesignerComponent`) whose children are `[redirect → app-definition, ...BASE_APP_ROUTES,
  ...BASE_WIDGET_ROUTES]`. A `mat-tab-nav-bar` drives the three tabs from
  `APPLICATION_DESIGNER_TABS`; `design.routes.spec.ts` asserts every tab path is mounted as a child, so
  the explicit list cannot drift from the routes.
- `BASE_APP_ROUTES` is spread **unchanged** — its branches keep their own scopes and facades and stay
  mountable elsewhere (the testbed still mounts them at `/base-app/samples`). URLs simply gained a
  segment: `/design/application/app-definition/list`.
- `design.modules` was referenced by base-app's route while existing in none of the five `design`
  locale files — the sidenav rendered the raw key. Fixed, and `design.i18n.spec.ts` now derives the
  expected key set from `DESIGN_ROUTES` recursively, so the next omission fails the build.
- The Widgets tab needed the whole `WidgetDefinition` authoring stack, which did not exist: mapper
  (renames the contract's `key` onto `id`, and carries **every** field in both directions — the form
  PUTs the whole input schema, so a field the mapper drops is destroyed on the next Save), service with
  `publish`, store, descriptors, two port descriptors, three facades, container with a Publish action,
  `BASE_WIDGET_ROUTES`, and the `base_widget` transloco scope in five locales. `propsSchema` is
  deliberately **not** on the form — arbitrary nested JSON Schema through a flat key/value editor would
  mangle it; that stays Phase 6's business.
- Sample data, mock backend: a hand-written `processpuzzle-testbed-widget-definitions` in
  `tools/mock-backend/db.json` (`cards-grid`, `markdown-page`) plus `widget-definitions` in
  `org-scope.js`.
- Sample data, Java backend: `DefaultWidgetLoader` +
  `base-widget-backend/src/main/resources/default-widgets/processpuzzle-testbed-widgets.yaml`, gated by
  `base-widget.loadDefaultWidgets` (`yes` in the application, `no` under `unit-test`). The same two
  widget types, seeded as **drafts** through `WidgetEndpoint` — so a default passes exactly the
  validation a designer's definition does, and the Publish action has something to act on. Filename
  before `-widgets.yaml` is the owning organization, as in `<orgKey>-apps.yaml`; existing keys are left
  untouched, so a restart cannot overwrite edited definitions. This was not optional polish: the
  generated `[Widget Definition] LIST` e2e spec asserts the list is non-empty, and `defineEntityListSuite`
  has no exclusion mechanism, so a routable entity with no rows fails the suite on the Java backend.
- A **Phase 5 defect** the e2e verification surfaced, fixed here: `RouteDefinition.path`,
  `ModuleMount.moduleKey` and `ModuleMount.basePath` carry a `pattern` in the contract but declared none
  on their descriptors, so the form accepted values the backend's `@Pattern` refuses. It read as an
  embedded-persistence bug — the row appears on Save and is gone after reload — because the generated
  fixture value reaches the PUT as prose and comes back `400 request.validation-failed`, which the backend
  does not log. A descriptor `pattern` is therefore load-bearing twice over: `BaseEntityFormBuilder` turns
  it into a `Validators.pattern`, and the e2e registry turns it into a dashed fixture token.

## Phase 6 — Tabs

No new schema. Read against the Application section above: a `tab-container` widget is authored on the
Widgets tab and placed from the Applications tab.

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
