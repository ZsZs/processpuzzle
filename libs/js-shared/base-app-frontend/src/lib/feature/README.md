# Application Preview — route rendering

How an `AppDefinition`'s flat `routes[]` becomes real, navigable Angular routes inside the shell.

## The pieces

- `app-route-builder.ts` — `buildAppRoutes()` derives Angular's nested `Routes` from the flat authored
  list, and mounts each `ModuleMount` under its `basePath`. Asynchronous, because a renderer may have
  to *fetch* what a route renders — an entity's descriptor, see the ENTITY note below. Children a renderer
  contributes and children derived from authored paths are concatenated, not one replacing the other.
- `app-route-renderer.ts` — `AppRouteRenderer`, the injectable `RouteRenderer` implementation.
  Dispatches on `RouteDefinition.kind`.
- `route-widgets.component.ts` — renders `kind: 'WIDGETS'`. Same WIDGET_REGISTRY +
  NgComponentOutlet pattern `DocumentEditorComponent` uses for STANDALONE widget blocks.
- `kind: 'ENTITY'` is **not rendered here.** base-entity owns it: `AppRouteRenderer` calls
  `EntityScreenResolver.resolve(entityName)` and spreads the `Route` that `entityScreenRoute()` returns,
  adding only what an application definition contributes — the authored title, `entityMode` and
  `rsqlFilter`. The screens themselves, their URL shape and the component hosting them
  (`BaseEntityScreensComponent`) live in `base-entity-frontend/src/lib/base-entity-screens/`, because
  mounting an entity's generated screens is base-entity's job and an application that does it needs no
  dependency on base-app at all. The resolver answers alike for an entity with a compile-time facade and one
  that exists only as a `BaseEntityDefinition`.
- `route-unsupported.component.ts` — fallback for `kind: 'DOCUMENT'` (no runtime-parameterized
  document viewer exists yet) and any unrecognized kind. Renders the reason as text rather
  than throwing during route registration.
- `shell/app-shell-routes.ts` — `AppShellRoutesFactory` + `appShellRoutesGuard`, which register an
  application's routes as the children of the Preview tab's route.

## URL shape of an ENTITY route

Owned by base-entity (`entityScreenRoute`), not by anything here. In the Preview tab it comes out as:

```
app-definition/demo/preview/order-list/order/list
app-definition/demo/preview/order-list/order/<uuid>/details
app-definition/demo/preview/order-list/order/<uuid>/details/order-line/<productName>/details
```

## Deliberately not done here

- `roles` -> `canMatch` guards — not guessed at.
- `entityMode` — carried on the route data, applied by nothing. A `DETAILS` route renders the entity's
  screens starting on the list, from which the row is one click away. Landing straight on the form means
  carrying the authored route's own `:id` into the child URL, and the `RedirectFunction` that would do it
  receives a *partial* route snapshot whose parameter inheritance at recognition time the typings do not
  promise.
- `rsqlFilter` — carried on the route data, applied by nothing. It is a per-*route* filter, while one facade
  and one store serve every route of an entity, so it cannot live in the repository; it needs a design pass
  on the store's query surface.
- `kind: 'DOCUMENT'` rendering — needs its own small design pass.
