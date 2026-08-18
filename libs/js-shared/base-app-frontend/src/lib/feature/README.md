# Application Preview — route rendering, first slice

Four new files under `libs/js-shared/base-app-frontend/src/lib/feature/`, plus an updated
`public-api.ts` with their exports appended (only additions — nothing existing was removed
or reordered beyond that).

Drop these into the matching paths in the real tree; `public-api.ts` is a full replacement
of that one file (diff it before overwriting, in case develop has moved since this was cut).

## What this is

The `RouteRenderer` that `buildAppRoutes()` (already existing, untouched) needs to actually
turn an `AppDefinition`'s flat `routes[]` into real, navigable Angular routes:

- `app-route-renderer.ts` — `AppRouteRenderer`, the injectable `RouteRenderer` implementation.
  Dispatches on `RouteDefinition.kind`.
- `route-widgets.component.ts` — renders `kind: 'WIDGETS'`. Same WIDGET_REGISTRY +
  NgComponentOutlet pattern DocumentEditorComponent already uses for STANDALONE widget blocks.
- `route-entity.component.ts` — renders `kind: 'ENTITY'`. Resolves a descriptor via
  `BaseEntityDescriptorRegistry` and hands it to `BaseEntityContainerComponent`. Only works
  today for entity types with a compile-time Facade registered — a designer-created type
  renders a plain "not registered yet" message instead of crashing.
- `route-unsupported.component.ts` — fallback for `kind: 'DOCUMENT'` (no runtime-parameterized
  document viewer exists yet) and any unrecognized kind. Renders the reason as text rather
  than throwing during route registration.

## Deliberately not done here (see conversation for why)

- `roles` -> `canMatch` guards — not guessed at.
- `entityMode` / `rsqlFilter` wiring into the resolved descriptor — read from route data but
  not yet applied; needs a look at how `BaseEntityTabsComponent`'s list/details switch and an
  RSQL pre-filter are meant to compose.
- `kind: 'DOCUMENT'` rendering — needs its own small design pass.
- Nothing calls `AppRouteRenderer` yet — no `AppShellComponent`, no Preview container. This is
  the connective layer only.
