import { describe, expect, it } from 'vitest';
import { Route } from '@angular/router';
import { ENTITY_NAME_ROUTE_DATA_KEY, snakeCaseName } from '@processpuzzle/base-entity';
import { ORG_ADMIN_ROUTES } from '@processpuzzle/org-admin';
import { createAppRoutes } from './app.routes';

/**
 * Guards the mounting mistakes that fail *silently* rather than loudly.
 *
 * `BaseFormNavigatorSingletonStore` rebuilds the details URL as `baseUrl + '/' +
 * snakeCaseName(entityName) + …`, so a route mounted at anything but that segment produces links
 * that match no route and simply do nothing — no error, no console warning, the row never opens.
 * The `baseUrl` half comes from the breadcrumb, accumulated from the root down to the route that
 * declares `entityName`, which is what makes the two-segment prefix below safe. Both halves are
 * asserted here because neither the compiler nor the build sees them.
 */
describe('createAppRoutes', () => {
  const routeFor = (routes: Route[], path: string): Route => {
    const route = routes.find((candidate) => candidate.path === path);
    if (!route) throw new Error(`no route '${path}' among [${routes.map((candidate) => candidate.path).join(', ')}]`);
    return route;
  };

  describe('with a tenant in the URL', () => {
    const routes = createAppRoutes('acme');

    it('mounts the org-admin routes under a literal <orgKey>/admin, not a parameter', () => {
      const branch = routeFor(routes, 'acme/admin');
      expect(branch.children).toBe(ORG_ADMIN_ROUTES);
      // A `:orgKey` parameter would let one loaded bundle serve two tenants, and the realm — hence
      // the token in every request — was settled for only one of them.
      expect(routes.some((route) => route.path?.includes(':'))).toBeFalsy();
    });

    it.each(ORG_ADMIN_ROUTES.filter((route) => route.data?.[ENTITY_NAME_ROUTE_DATA_KEY]).map((route) => [route.path as string]))(
      '%s is mounted at snakeCaseName(entityName)',
      (path) => {
        const route = ORG_ADMIN_ROUTES.find((candidate) => candidate.path === path) as Route;
        expect(path).toBe(snakeCaseName(route.data?.[ENTITY_NAME_ROUTE_DATA_KEY] as string));
      },
    );

    it('sends the tenant front door into the admin branch', () => {
      expect(routeFor(routes, 'acme').redirectTo).toBe('acme/admin');
    });

    it('gives every navigable route an icon and a menu title', () => {
      const navigable = routes.filter((route) => route.title !== null && route.title !== undefined);
      expect(navigable.map((route) => route.path)).toEqual(['home', 'acme/admin']);
      for (const route of navigable) {
        expect(route.data?.['icon'], `route '${route.path}' has no icon`).toEqual(expect.any(String));
        expect(route.data?.['menuTitle'], `route '${route.path}' has no menuTitle`).toEqual(expect.any(String));
      }
    });
  });

  describe('with no tenant in the URL', () => {
    const routes = createAppRoutes();

    it('offers home alone — there is no tenant to administer', () => {
      expect(routes.filter((route) => route.title !== null && route.title !== undefined).map((route) => route.path)).toEqual(['home']);
      expect(routes.some((route) => route.path?.endsWith('/admin'))).toBeFalsy();
    });

    it('lands on home', () => {
      expect(routeFor(routes, '').redirectTo).toBe('home');
    });
  });

  it('keeps the auth branch out of navigation', () => {
    const authRoute = createAppRoutes('acme').find((route) => route.matcher !== undefined);
    expect(authRoute).toBeDefined();
    expect(authRoute?.title).toBeUndefined();
  });
});
