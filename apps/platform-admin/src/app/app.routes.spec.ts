import { describe, expect, it } from 'vitest';
import { Route } from '@angular/router';
import { ENTITY_NAME_ROUTE_DATA_KEY, snakeCaseName } from '@processpuzzle/base-entity';
import { PLATFORM_ADMIN_ROUTES } from '@processpuzzle/platform-admin';
import { appRoutes } from './app.routes';

/**
 * Guards the two mounting mistakes that fail *silently* rather than loudly.
 *
 * First, the segment. `BaseFormNavigatorSingletonStore` rebuilds the details URL from the entity name
 * — `baseUrl + '/' + snakeCaseName(entityName) + …` — so a route mounted at anything but that segment
 * produces links that match no route and do nothing: no error, no console warning, the row just never
 * opens. Second, the depth. Because the navigator builds from the root, nesting the library's routes
 * under a prefix here would make every one of those URLs resolve one level too high, with the same
 * silent result. Hence the spread at the top level, asserted below.
 */
describe('appRoutes', () => {
  const routeFor = (path: string): Route => {
    const route = appRoutes.find((candidate) => candidate.path === path);
    if (!route) throw new Error(`no route '${path}' among [${appRoutes.map((candidate) => candidate.path).join(', ')}]`);
    return route;
  };

  it('mounts the platform-admin screens at the top level, unnested', () => {
    for (const libraryRoute of PLATFORM_ADMIN_ROUTES) {
      expect(appRoutes, `'${libraryRoute.path}' is not a top-level route`).toContain(libraryRoute);
    }
  });

  it.each(PLATFORM_ADMIN_ROUTES.map((route) => [route.path as string]))('%s is mounted at snakeCaseName(entityName)', (path) => {
    const route = routeFor(path);
    const entityName = route.data?.[ENTITY_NAME_ROUTE_DATA_KEY];

    expect(entityName, `route '${path}' mounts generated screens but declares no ${ENTITY_NAME_ROUTE_DATA_KEY}`).toEqual(expect.any(String));
    expect(path).toBe(snakeCaseName(entityName as string));
  });

  it('lands on home and gives every navigable route an icon and a menu title', () => {
    expect(routeFor('').redirectTo).toBe('home');

    const navigable = appRoutes.filter((route) => route.title !== null && route.title !== undefined);
    expect(navigable.length).toBe(PLATFORM_ADMIN_ROUTES.length + 1);
    for (const route of navigable) {
      expect(route.data?.['icon'], `route '${route.path}' has no icon`).toEqual(expect.any(String));
      expect(route.data?.['menuTitle'], `route '${route.path}' has no menuTitle`).toEqual(expect.any(String));
    }
  });

  it('keeps the auth branch out of navigation', () => {
    const authRoute = appRoutes.find((route) => route.matcher !== undefined);
    expect(authRoute).toBeDefined();
    expect(authRoute?.title).toBeUndefined();
  });
});
