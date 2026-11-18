import { describe, expect, it } from 'vitest';
import { Route } from '@angular/router';
import { baseEntityRoutes, embeddedChildRoute, EmbeddedChildRoute } from '../base-entity.routes';
import { contextObjectGuard } from '../working-context/context-object.guard';
import { WORKING_CONTEXT_ROUTE_DATA_KEY } from '../working-context/working-context.url';

class PartFacade {}
const PARTS: EmbeddedChildRoute[] = [
  { entityName: 'Demo Component One', facade: PartFacade as never },
  { entityName: 'Demo Component Two', facade: PartFacade as never },
];

describe('baseEntityRoutes of an entity that canContextObject', () => {
  const contextRoute = (routes: Route[]) => routes.find((route) => route.path === ':entityId');

  it('adds nothing for an ordinary entity', () => {
    expect(contextRoute(baseEntityRoutes(PARTS))).toBeUndefined();
  });

  it('adds nothing when there is no part to list', () => {
    expect(contextRoute(baseEntityRoutes([], [], { entityName: 'Demo Root' }))).toBeUndefined();
  });

  it('adds the aggregate\'s own id route after list, guarded, landing on the first part and naming the context', () => {
    const routes = baseEntityRoutes(PARTS, [], { entityName: 'Demo Root' });
    const route = contextRoute(routes);

    expect(routes.indexOf(route as Route)).toBeGreaterThan(routes.findIndex((candidate) => candidate.path === 'list'));
    expect(route?.canActivate).toEqual([contextObjectGuard]);
    expect(route?.data?.[WORKING_CONTEXT_ROUTE_DATA_KEY]).toBe('demo-root');
    expect(route?.children?.[0]).toMatchObject({ path: '', pathMatch: 'full', redirectTo: 'demo-component-one/list' });
    expect(route?.children?.slice(1).map((child) => child.path)).toEqual(['demo-component-one', 'demo-component-two']);
  });

  it('gives a part a list route below the aggregate only, and none below an entity\'s details', async () => {
    const listsOf = async (route: Route) => ((await (route.loadChildren as () => Promise<Route[]>)()) as Route[]).map((child) => child.path);

    expect(await listsOf(embeddedChildRoute(PARTS[0], true))).toContain('list');
    expect(await listsOf(embeddedChildRoute(PARTS[0]))).not.toContain('list');
  });
});
