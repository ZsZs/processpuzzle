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
- Next: phase 2 (path API and `dataSource` binding).

Libraries are released one at a time, per phase.
