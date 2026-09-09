import { describe, expect, it, vi } from 'vitest';
import { Route } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { ENTITY_NAME_ROUTE_DATA_KEY, snakeCaseName } from '@processpuzzle/base-entity';
import { appRoutes } from './app.routes';
import { TestBed } from '@angular/core/testing';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE } from '@processpuzzle/auth';

/**
 * Guards the one thing about mounting a metadata-defined entity that fails *silently*.
 *
 * `readEmbeddedBreadcrumb` opens a breadcrumb level when it meets the route that **declares** the entity
 * name, and takes that level's `baseUrl` from the URL accumulated up to that point. Every sibling URL —
 * the Details link on the Name column, the Details tab, Cancel — is built as
 * `baseUrl + '/' + snakeCaseName(entityName) + …`. So the name has to sit on the route contributing the
 * entity's own segment: one route too deep and `baseUrl` already contains that segment, every URL built on
 * it doubles it, and the navigation matches no route and does nothing at all. No error, no console warning.
 */
describe('appRoutes — entity screen mount points', () => {
  const childrenOf = (path: string, ...rest: string[]): Route[] => {
    let routes: Route[] = appRoutes;
    for (const segment of [path, ...rest]) {
      const route = routes.find((candidate) => candidate.path === segment);
      if (!route) throw new Error(`no route '${segment}' among [${routes.map((candidate) => candidate.path).join(', ')}]`);
      routes = route.children ?? [];
    }
    return routes;
  };

  const mountedEntities = (...path: [string, ...string[]]): Array<{ path: string | undefined; entityName: unknown }> =>
    childrenOf(...path)
      .filter((route) => route.loadChildren !== undefined)
      .map((route) => ({ path: route.path, entityName: route.data?.[ENTITY_NAME_ROUTE_DATA_KEY] }));

  it.each([
    ['base-rule', ['base-rule', 'samples'] as [string, string]],
    ['base-entity', ['base-entity', 'samples'] as [string, string]],
  ])('%s samples declare the entity name on the segment-contributing route', (_label, path) => {
    const mounts = mountedEntities(...path);

    expect(mounts.length).toBeGreaterThan(0);
    for (const mount of mounts) {
      expect(mount.entityName, `route '${mount.path}' mounts generated screens but declares no ${ENTITY_NAME_ROUTE_DATA_KEY}`).toEqual(expect.any(String));
      // The segment and the name have to agree too: the navigator snake-cases the name to build the URL,
      // so a route mounted at anything else is a link that resolves nowhere.
      expect(mount.path).toBe(snakeCaseName(mount.entityName as string));
    }
  });

  it('mounts both rule samples', () => {
    expect(mountedEntities('base-rule', 'samples').map((mount) => mount.entityName)).toEqual(['Order', 'Special Order']);
  });

  it('exposes every primary feature as a titled navigation route', () => {
    expect(appRoutes.filter((route) => route.title).map((route) => route.path)).toEqual([
      'home', 'util', 'test-util', 'widgets', 'auth-lib', 'base-entity', 'base-rule', 'base-document', 'base-state', 'base-workflow', 'base-app', 'ci-cd',
    ]);
  });

  it('authenticates before rendering home', async () => {
    const authenticate = vi.fn().mockResolvedValue(undefined);
    TestBed.configureTestingModule({ providers: [{ provide: AUTHENTICATION_SERVICE, useValue: { authenticate } }] });
    const home = appRoutes.find((route) => route.path === 'home');

    await TestBed.runInInjectionContext(() => home?.resolve?.['auth']?.());

    expect(authenticate).toHaveBeenCalledOnce();
  });
});
