import { InjectionToken } from '@angular/core';
import { Route, Routes } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { BaseEntityContainerComponent } from './base-entity-container.component';
import { baseEntityRoutes, BASE_ENTITY_ROUTES, EmbeddedChildRoute } from './base-entity.routes';
import { BaseEntityFormComponent } from './base-form/base-entity-form.component';
import { EMBEDDED_ENTITY_ROUTE_DATA_KEY, ENTITY_NAME_ROUTE_DATA_KEY } from './base-form-navigator/entity-route.registry';
import { BaseEntityListComponent } from './base-list/base-entity-list.component';

const embeddedComponentFacade = new InjectionToken<never>('EMBEDDED_COMPONENT_FACADE');
const embeddedDetailFacade = new InjectionToken<never>('EMBEDDED_DETAIL_FACADE');

function detailsRouteOf(routes: Routes): Route {
  const route = routes.find((candidate) => candidate.component === BaseEntityFormComponent);
  if (!route) throw new Error('no details route');
  return route;
}

/** `loadChildren` may return `Routes` synchronously, which is what lets a branch expand one level at a time. */
function expand(route: Route): Routes {
  return route.loadChildren?.() as Routes;
}

describe('baseEntityRoutes', () => {
  it('produces the list and details routes of a stand-alone entity', () => {
    const routes = baseEntityRoutes();

    expect(routes.map((route) => route.path)).toEqual(['', ':entityId/details', 'list']);
    expect(detailsRouteOf(routes).component).toBe(BaseEntityFormComponent);
    expect(routes.find((route) => route.path === 'list')?.component).toBe(BaseEntityListComponent);
  });

  it('leaves the details route childless when there is nothing embedded to mount', () => {
    expect(detailsRouteOf(baseEntityRoutes()).loadChildren).toBeUndefined();
    expect(detailsRouteOf(BASE_ENTITY_ROUTES).loadChildren).toBeUndefined();
  });

  it('hangs an embedded child below the details route, under its snake-cased entity name', () => {
    const routes = baseEntityRoutes([{ entityName: 'Embedded Component', facade: embeddedComponentFacade }]);

    const [child] = expand(detailsRouteOf(routes));

    expect(child.path).toBe('embedded-component');
    expect(child.component).toBe(BaseEntityContainerComponent);
    expect(child.data).toMatchObject({ [ENTITY_NAME_ROUTE_DATA_KEY]: 'Embedded Component', [EMBEDDED_ENTITY_ROUTE_DATA_KEY]: true });
    expect(child.canActivate).toHaveLength(1);
  });

  /** `.../test-entity/1/details/embedded-component/embedded_1_1/details` — the nesting carries the position. */
  it('gives the embedded child its own list and details routes one level down', () => {
    const routes = baseEntityRoutes([{ entityName: 'Embedded Component', facade: embeddedComponentFacade }]);

    const [child] = expand(detailsRouteOf(routes));

    expect(expand(child).map((route) => route.path)).toEqual(['', ':entityId/details', 'list']);
  });

  /**
   * The branch route names the entity, the details route below it names the row — so only the latter can tell
   * a link to a row that has since been deleted from a valid one.
   */
  it('guards the embedded details route as well as the branch above it, and neither of a stand-alone entity', () => {
    const routes = baseEntityRoutes([{ entityName: 'Embedded Component', facade: embeddedComponentFacade }]);

    const [child] = expand(detailsRouteOf(routes));

    expect(detailsRouteOf(expand(child)).canActivate).toEqual(child.canActivate);
    expect(detailsRouteOf(routes).canActivate).toBeUndefined();
  });

  it('carries the caller’s extra providers alongside the active facade', () => {
    const scope = { provide: new InjectionToken('SCOPE'), useValue: 'embedded_component' };
    const routes = baseEntityRoutes([{ entityName: 'Embedded Component', facade: embeddedComponentFacade, providers: [scope] }]);

    const [child] = expand(detailsRouteOf(routes));

    expect(child.providers).toHaveLength(2);
    expect(child.providers?.[1]).toBe(scope);
  });

  it('mounts several embedded children side by side', () => {
    const routes = baseEntityRoutes([
      { entityName: 'Embedded Component', facade: embeddedComponentFacade },
      { entityName: 'Embedded Detail', facade: embeddedDetailFacade },
    ]);

    expect(expand(detailsRouteOf(routes)).map((route) => route.path)).toEqual(['embedded-component', 'embedded-detail']);
  });

  it('nests a child of a child', () => {
    const routes = baseEntityRoutes([
      {
        entityName: 'Embedded Component',
        facade: embeddedComponentFacade,
        children: () => [{ entityName: 'Embedded Detail', facade: embeddedDetailFacade }],
      },
    ]);

    const [component] = expand(detailsRouteOf(routes));
    const [detail] = expand(detailsRouteOf(expand(component)));

    expect(detail.path).toBe('embedded-detail');
    expect(detail.data).toMatchObject({ [ENTITY_NAME_ROUTE_DATA_KEY]: 'Embedded Detail' });
  });

  /**
   * `App Nav Item` nests inside `App Nav Item`, so the branch is an infinite structure. Expanding it lazily —
   * one level per navigation — is what keeps it finite at any moment.
   */
  it('expands a self-referential child one level at a time instead of recursing forever', () => {
    const navItem: EmbeddedChildRoute = { entityName: 'App Nav Item', facade: embeddedComponentFacade, children: () => [navItem] };

    let level = expand(detailsRouteOf(baseEntityRoutes([navItem])))[0];
    const depths: string[] = [];
    for (let depth = 0; depth < 4; depth++) {
      depths.push(level.path as string);
      level = expand(detailsRouteOf(expand(level)))[0];
    }

    expect(depths).toEqual(['app-nav-item', 'app-nav-item', 'app-nav-item', 'app-nav-item']);
  });
});
