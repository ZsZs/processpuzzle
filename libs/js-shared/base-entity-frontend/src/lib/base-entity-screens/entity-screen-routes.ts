import { Route, Routes } from '@angular/router';
import { baseEntityRoutes } from '../base-entity.routes';
import { snakeCaseName } from '../base-form-navigator/base-form-navigator.store';
import { ENTITY_NAME_ROUTE_DATA_KEY } from '../base-form-navigator/entity-route.registry';
import { ENTITY_DESCRIPTOR_ROUTE_DATA_KEY, BaseEntityScreensComponent, REQUESTED_ENTITY_ROUTE_DATA_KEY } from './entity-screens.component';
import { EntityScreens } from './entity-screens.resolver';

export interface EntityScreenRouteOptions {
  /** Entity whose screens to mount, by descriptor name. */
  entityName: string | undefined;
  /**
   * What {@link EntityScreenResolver} answered. Absent — an entity with neither a facade nor a definition —
   * yields a leaf route whose component renders "no entity type registered" instead of no route at all.
   */
  screens?: EntityScreens;
  /**
   * Path this route is mounted at, if any. Only read to decide whether the entity's own segment is already
   * there; pass the route's own `path`, not the URL prefix above it.
   */
  hostPath?: string;
  /** Extra route `data` the host wants alongside what this sets. */
  data?: Record<string, unknown>;
}

/**
 * One entity's screens as a `Route` — component, `data` and the children that make List and Details real
 * routes.
 *
 * The `Route` is returned rather than registered, so it composes: a hand-written application spreads it into
 * its own route config, and base-app's `AppRouteRenderer` spreads it into what an `AppDefinition` authored.
 * `path` is deliberately **not** set — that belongs to whoever mounts this.
 *
 * Everything about the URL shape lives here rather than in any one host, because the rules are
 * base-entity's: `BaseFormNavigatorSingletonStore` composes every entity URL as
 * `<base>/<snakeCaseName(entityName)>/list` or `<base>/<snakeCaseName(entityName)>/<id>/details`, and
 * derives `<base>` by counting segments back from the current URL. A host that got this wrong would see
 * every tab click and row link do nothing at all — see {@link entityScreenChildren}.
 */
export function entityScreenRoute({ entityName, screens, hostPath, data }: EntityScreenRouteOptions): Route {
  const route: Route = {
    component: BaseEntityScreensComponent,
    data: {
      [ENTITY_DESCRIPTOR_ROUTE_DATA_KEY]: screens?.descriptor,
      [REQUESTED_ENTITY_ROUTE_DATA_KEY]: entityName,
      ...data,
    },
  };

  if (screens && entityName) route.children = entityScreenChildren(entityName, screens, hostPath);
  return route;
}

/**
 * The children of an entity-screens route: `baseEntityRoutes()` mounted so the URLs it produces are the ones
 * the form navigator builds.
 *
 * The entity's own segment has to be **in** the URL, which is why there is a `<snake>` child rather than the
 * list and details routes sitting directly here: a route mounted at `order-list` cannot host `list`
 * directly, because the navigator would then build `…/order/list` for a screen mounted at
 * `…/order-list/list` and nothing would match.
 *
 * When `hostPath` already ends in the snake-case name — a route simply called `order` — the group is mounted
 * path-less instead, so the URL stays `…/order/list` rather than becoming `…/order/order/list`, and
 * `baseEntityRoutes`' own `'' -> list` redirect does the landing.
 *
 * `ENTITY_NAME_ROUTE_DATA_KEY` goes on whichever route contributes the `<snake>` segment, and only there:
 * `readEmbeddedRouteChain` reads it to place the level an embedded drill-down hangs off, and it has to sit on
 * the route *before* the one declaring `:entityId` for the row's id to be attributed to the right level.
 */
function entityScreenChildren(entityName: string, screens: EntityScreens, hostPath: string | undefined): Routes {
  const snakeCaseEntityName = snakeCaseName(entityName);
  const entityRoutes = baseEntityRoutes(screens.embeddedChildren);
  const data = { [ENTITY_NAME_ROUTE_DATA_KEY]: entityName };

  if (lastSegmentOf(hostPath) === snakeCaseEntityName) return [{ path: '', data, children: entityRoutes }];

  return [
    // `pathMatch: 'full'`, or this would prefix-match the entity's own children and redirect in a loop.
    { path: '', pathMatch: 'full', redirectTo: `${snakeCaseEntityName}/list` },
    { path: snakeCaseEntityName, data, children: entityRoutes },
  ];
}

function lastSegmentOf(path: string | undefined): string | undefined {
  const segments = (path ?? '').split('/').filter((segment) => segment.length > 0);
  return segments[segments.length - 1];
}
