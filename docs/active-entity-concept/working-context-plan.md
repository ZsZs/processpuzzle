# Working Context – Implementation Plan

2026-11-15 · implements [working-context-spec.md](working-context-spec.md)

## Decisions

| Question                          | Decision                                                                                                                                                  |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| URL prefix                        | Fixed `ctx`: `/<app>/ctx/:contextName/:id/...`                                                                                                            |
| Recent contexts                   | localStorage, per device                                                                                                                                  |
| Clear context on a Running entity | Allowed, with the same confirmation as switching                                                                                                          |
| Chip templates (`{{name}}`)       | Reuse existing interpolation, or a minimal attribute-path resolver                                                                                        |
| Embedded path depth               | One level (`rounds`, `rounds/{itemId}`) in phase 2; nested paths later                                                                                    |
| Where the context is displayed    | **Not a separate chip.** The base-entity status bar's URL breadcrumb is extended: the active context becomes its root crumb (`Regatta › Race › Round 3`). |

## Spec vs. code – adaptations

| Spec                                        | Code today                                                                                                                                      | Adaptation                                                                                                                                                                                                                                                                                                                        |
| ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Top-level `contexts:` / `views:`            | `AppDefinition` / `ModuleDefinition` with `routes: RouteDefinition[]`; no views concept                                                         | `contexts: ContextDefinition[]` on `AppDefinition` and `ModuleDefinition`; `requiresContext`, `context: 'optional'`, `dataSource {context, path}` on `RouteDefinition` / `RouteTarget`                                                                                                                                            |
| Chip in "the status bar"                    | `BaseEntityStatusbarComponent` renders a URL breadcrumb, but only inside `BaseEntityTabsComponent`; shell regions are header / sidenav / footer | The status bar gets the context as root crumb (badge, menu: switch, clear, transitions). It becomes hostable outside the tabs (registered widget, new `statusbar` shell region), because context views may be WIDGETS pages. When a shell-level status bar is present, the tab-level one hides itself, so nothing is shown twice. |
| `ContextService`                            | `ACTIVE_ENTITY_FACADE` exists with another meaning                                                                                              | Named `WorkingContextService`                                                                                                                                                                                                                                                                                                     |
| "Set as active" row action                  | No per-row action extension point; Enter / click toggle selection; `SELECT_OR_CREATE` already uses the list as a picker                         | Generic list `rowActions` extension; picker drawer built on the `SELECT_OR_CREATE` pattern                                                                                                                                                                                                                                        |
| `/entities/{type}/{id}/{path}`, `jsonb_set` | `/organizations/{orgKey}/entities/{code}/{id}`, full PUT, one `@Version` per object, embedded items have no id / version, no PATCH / ETag       | Org-scoped paths; phase 2 versions on the object (`If-Match` = object version); per-item versions in phase 4 with an id backfill migration                                                                                                                                                                                        |
| Data-source binding                         | `EmbeddedEntityService` + `embedded-route-context.ts` read and write embedded rows through the root's Save                                      | Reused; a variant writes through the path endpoints immediately, without an open root form                                                                                                                                                                                                                                        |
| State badge / actions                       | `EntityObjectStateService.findState()` / `fireTransition()` in base-state-frontend                                                              | Wired through an injection token (like `ENTITY_STATE_CONTROL`), so base-entity does not depend on base-state                                                                                                                                                                                                                      |

## Placement

- **base-entity-frontend** `working-context/`: `WorkingContextService`, resolver + empty state with embedded picker, picker drawer, list `rowActions`; status bar extended with context crumbs and made hostable outside the tabs.
- **base-widget-frontend**: status bar registered as a widget.
- **base-app-frontend**: metadata types, `statusbar` region, route builder emitting `ctx/:contextName/:id/...` branches.
- **base-state-frontend**: state badge / transitions adapter for the token.
- **api-contracts + base-entity-backend**: embedded-collection endpoints.

## Phases

1. **Context core (frontend):** metadata types; `WorkingContextService` (URL source of truth, localStorage fallback, parent clears children, `confirmSwitchWhen`); resolver + empty state (no redirect); "Set as active" row action; picker drawer with Recent; context root crumb in the status bar with Clear / Switch; shell-hosted status bar.
2. **Path API + binding:** `GET/POST/PATCH/DELETE .../{id}/{attr}[/{itemId}]`, server-assigned item ids, `If-Match` on the object version, native `jsonb_set` / append; frontend `dataSource {context, path}`.
3. **State actions:** badge from `findState`, transitions in the crumb menu and on declaring routes, refresh after firing.
4. **Hardening:** per-item `id` / `version` + migration; nested contexts; `context: optional` with filter toggle (RSQL on embedded paths); e2e context scenario modelled on the living-docs harness.
5. **Starter:** Race context in the SailRC starter (processpuzzle-biz); a Warehouse fixture in the testbed proves "metadata only".

## Status

- **1a – done:** `WorkingContextService`, `workingContextRoutes`, picker / drawer / empty state, "Set as active", context crumbs in the status bar.
- **1b – done:** `contexts` on `AppDefinition`, `requiresContext` on routes and `statusbar` region (contract, backend graph, validation, designer form, route builder, shell). Contexts are declared on the app; the routes of its mounted modules may name them. A module cannot declare contexts of its own (yet).
- **2 – done:**
  - Backend: `GET/POST …/{id}/{attr}` and `PATCH/DELETE …/{id}/{attr}/{itemId}` (one level), items identified by `id` (assigned on append). The write is the existing update use case with one item changed, so validation, managed attributes and events stay in one place; the object `version` is the only version (`ETag` on every answer, optional `If-Match`). Calls naming no version are retried when another write wins the race, so concurrent appends both succeed. Deliberately **not** `jsonb_set`, which would write around validation and events.
  - Frontend: `EmbeddedCollectionService`, and `ContextCollectionComponent` — the list of an embedded collection of the **active** entity with add / edit / remove through a dialog built by the entity form builder. A route opts in with `requiresContext` + `contextPath` (the attribute) + an ENTITY target naming the item type.
  - Edit and delete send no `If-Match` for now: with one version per object, a second writer's unrelated change would make every other edit a 409. A patch is a merge of the changed members only. Per-item versions (phase 4) bring `If-Match` back.
- **3 – done:**
  - base-entity declares `ENTITY_STATE_ACTIONS` (state of an entity, transitions it can take, fire one); base-state implements it behind the same `provideEntityStateControl()` the forms use, so base-entity stays free of a base-state dependency and an app without it simply has no state to show.
  - `WorkingContextService` follows the active entities' state (re-read when the entity's version changes) and fires transitions, then reloads the entity.
  - The crumb's badge (`display.badge: state`) shows the state machine's name of the state; its menu lists the transitions, a refused one disabled with the reason. `state` in `confirmSwitchWhen` / `display` resolves through the machine's own state attribute, no longer through a guess from the descriptor.
  - "Views that declare them": the **context-actions** widget (`context`, optional `triggers`) puts the transitions as buttons on any page — no contract change, a widget on a route is how a view declares them.
- **4 – in progress**
  - **Slice 1 (done):** per-item versions. Every item of a multi-valued embedded collection gets an `id` and an `itemVersion` on the first save of its object (no bulk migration: the write path is the backfill). Item PATCH and DELETE check `If-Match` against the item's own version, so two people editing different rounds of one race no longer conflict; the object-level race is still retried. Edit and delete in the collection screen now send the item version. Item ETags carry the item version; the list keeps the object version.
  - **Slice 2 (done):** `context: optional` views. An ENTITY list route declares `filterByContext` (the context name) and does not require the context. Its list shows an "only the active …" toggle, on by default, that narrows the rows to `<context> == <active id>`. This also applies `rsqlFilter`, which was stored and never used; the list reads both from its screens route's own configuration, and the RSQL is combined in one query.
  - **Slice 3:** nested paths (`rounds/{id}/results`), and the e2e context scenario.


Libraries are released one at a time, per phase.
