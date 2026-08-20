import { Route, Routes } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { RouteDefinition } from '../domain/app-definition';
import { ModuleDefinition } from '../domain/module-definition';
import { buildAppRoutes } from './app-route-builder';

/** A renderer that records what it was given, so a spec can assert the definition reached it. */
const renderRoute = (definition: RouteDefinition): Route => ({ title: definition.title, data: { path: definition.path } });

function routeDefinitions(...paths: string[]): RouteDefinition[] {
  return paths.map((path) => new RouteDefinition({ path, title: path, kind: 'WIDGETS' }));
}

/** What a lazy mount contributes once something navigates into it. */
async function childrenOf(route: Route): Promise<Routes> {
  return route.loadChildren ? ((await route.loadChildren()) as Routes) : [];
}

/** The scopes a route's `provideTranslocoScope` providers register, alias included. */
function scopesOf(route: Route): unknown[] {
  return ((route.providers?.flat() ?? []) as Array<{ useValue: unknown }>).map((provider) => provider.useValue);
}

describe('buildAppRoutes', () => {
  it('emits a single-segment route as it was authored', async () => {
    const routes = await buildAppRoutes({ routes: routeDefinitions('orders') }, renderRoute);

    expect(routes).toEqual([{ path: 'orders', title: 'orders', data: { path: 'orders' } }]);
  });

  it('leaves the renderer everything but the path and the children', async () => {
    const routes = await buildAppRoutes({ routes: routeDefinitions('orders') }, () => ({ canMatch: [], title: 'Rendered' }));

    expect(routes[0].title).toBe('Rendered');
    expect(routes[0].canMatch).toEqual([]);
  });

  it('ignores a route with no path, there being no segment to mount it on', async () => {
    expect(await buildAppRoutes({ routes: [new RouteDefinition({ title: 'Nowhere' })] }, renderRoute)).toEqual([]);
  });

  // A row that came from the form is the raw JSON it arrived as, so the field may be absent rather than
  // empty — the type says otherwise, and the guard is what keeps that from becoming a route named 'undefined'.
  it('ignores a raw row whose path field is absent altogether', async () => {
    expect(await buildAppRoutes({ routes: [{ title: 'Nowhere' } as RouteDefinition] }, renderRoute)).toEqual([]);
  });

  // The rule the whole flat model rests on: nesting is derived here, not authored.
  it('makes an authored prefix the parent of the routes below it', async () => {
    const routes = await buildAppRoutes({ routes: routeDefinitions('claims', 'claims/open') }, renderRoute);

    expect(routes).toHaveLength(1);
    expect(routes[0].path).toBe('claims');
    expect(routes[0].children?.map((child) => child.path)).toEqual(['open']);
  });

  it('nests as deep as the authored paths reach', async () => {
    const routes = await buildAppRoutes({ routes: routeDefinitions('claims', 'claims/open', 'claims/open/:id') }, renderRoute);

    const open = routes[0].children?.[0];
    expect(open?.path).toBe('open');
    expect(open?.children?.map((child) => child.path)).toEqual([':id']);
  });

  it('folds a prefix nobody authored into the paths of its descendants rather than inventing a parent', async () => {
    const routes = await buildAppRoutes({ routes: routeDefinitions('claims/open', 'claims/closed') }, renderRoute);

    expect(routes.map((route) => route.path)).toEqual(['claims/open', 'claims/closed']);
    routes.forEach((route) => expect(route.children).toBeUndefined());
  });

  it('gives a leaf no children key at all, so the renderer decides whether it needs an outlet', async () => {
    const routes = await buildAppRoutes({ routes: routeDefinitions('orders') }, renderRoute);

    expect(routes[0]).not.toHaveProperty('children');
  });

  it('orders siblings static-prefix-first, so a parameter does not swallow its static neighbours', async () => {
    const routes = await buildAppRoutes({ routes: routeDefinitions('claims/:id', 'claims/new') }, renderRoute);

    expect(routes.map((route) => route.path)).toEqual(['claims/new', 'claims/:id']);
  });

  it('orders nested siblings the same way', async () => {
    const routes = await buildAppRoutes({ routes: routeDefinitions('claims', 'claims/:id', 'claims/new') }, renderRoute);

    expect(routes[0].children?.map((child) => child.path)).toEqual(['new', ':id']);
  });

  it('keeps the first of two rows claiming one path, the backend having rejected the pair already', async () => {
    const first = new RouteDefinition({ path: 'orders', title: 'First' });
    const second = new RouteDefinition({ path: 'orders', title: 'Second' });

    const routes = await buildAppRoutes({ routes: [first, second] }, renderRoute);

    expect(routes).toHaveLength(1);
    expect(routes[0].title).toBe('First');
  });

  describe('mounted modules', () => {
    it('mounts a module as a component-less parent at its base path', async () => {
      const routes = await buildAppRoutes({ modules: [{ moduleKey: 'claims', basePath: 'claims-handling' }], moduleRoutes: { claims: routeDefinitions('open') } }, renderRoute);

      expect(routes).toHaveLength(1);
      expect(routes[0].path).toBe('claims-handling');
      expect(routes[0]).not.toHaveProperty('component');
      expect(routes[0].children?.map((child) => child.path)).toEqual(['open']);
    });

    it('derives the nesting inside a module by the same rules', async () => {
      const routes = await buildAppRoutes({ modules: [{ moduleKey: 'claims', basePath: 'claims' }], moduleRoutes: { claims: routeDefinitions('cases', 'cases/:id', 'cases/new') } }, renderRoute);

      const cases = routes[0].children?.[0];
      expect(cases?.path).toBe('cases');
      expect(cases?.children?.map((child) => child.path)).toEqual(['new', ':id']);
    });

    it('mounts the modules after the app own routes', async () => {
      const routes = await buildAppRoutes({ routes: routeDefinitions('orders'), modules: [{ moduleKey: 'claims', basePath: 'claims' }], moduleRoutes: { claims: routeDefinitions('open') } }, renderRoute);

      expect(routes.map((route) => route.path)).toEqual(['orders', 'claims']);
    });

    // A dangling module key is a warning, not an error: the mount simply contributes nothing until the
    // module it names exists and has been loaded.
    it('skips a mount whose module has not been loaded', async () => {
      expect(await buildAppRoutes({ modules: [{ moduleKey: 'claims', basePath: 'claims' }] }, renderRoute)).toEqual([]);
    });

    it('skips a mount whose module loaded no routes', async () => {
      expect(await buildAppRoutes({ modules: [{ moduleKey: 'claims', basePath: 'claims' }], moduleRoutes: { claims: [] } }, renderRoute)).toEqual([]);
    });

    it('skips a mount with no base path, a module needing a prefix of its own', async () => {
      expect(await buildAppRoutes({ modules: [{ moduleKey: 'claims', basePath: '' }], moduleRoutes: { claims: routeDefinitions('open') } }, renderRoute)).toEqual([]);
    });
  });

  /**
   * What is deferred is the module's *metadata* — one `GET /modules/{key}` per module the user actually
   * visits — not a bundle: the widgets its routes render are part of the application's own build either way.
   */
  describe('lazily mounted modules', () => {
    const moduleOf = (routes: RouteDefinition[], fields: Partial<ModuleDefinition> = {}) => new ModuleDefinition({ id: 'claims', name: 'Claims', routes, ...fields });

    it('mounts a module the shell has not got as a loadChildren at its base path', async () => {
      const loadModule = vi.fn().mockResolvedValue(moduleOf(routeDefinitions('open')));

      const routes = await buildAppRoutes({ modules: [{ moduleKey: 'claims', basePath: 'claims-handling' }], loadModule }, renderRoute);

      expect(routes).toHaveLength(1);
      expect(routes[0].path).toBe('claims-handling');
      expect(routes[0]).not.toHaveProperty('children');
      // Nothing is fetched while the routes are being built: that is the whole point of the deferral.
      expect(loadModule).not.toHaveBeenCalled();
      expect(typeof routes[0].loadChildren).toBe('function');
    });

    it('fetches the module by the key the mount names, on first navigation into it', async () => {
      const loadModule = vi.fn().mockResolvedValue(moduleOf(routeDefinitions('open')));

      await childrenOf((await buildAppRoutes({ modules: [{ moduleKey: 'claims', basePath: 'claims' }], loadModule }, renderRoute))[0]);

      expect(loadModule).toHaveBeenCalledWith('claims');
    });

    // The loaded routes hang under a component-less wrapper rather than being returned bare, because the
    // wrapper is what carries the module's transloco scope.
    it('registers the loaded routes under a wrapper carrying the module scope', async () => {
      const loadModule = vi.fn().mockResolvedValue(moduleOf(routeDefinitions('cases', 'cases/:id', 'cases/new')));

      const children = await childrenOf((await buildAppRoutes({ modules: [{ moduleKey: 'claims', basePath: 'claims' }], loadModule }, renderRoute))[0]);

      expect(children.map((child) => child.path)).toEqual(['']);
      expect(children[0]).not.toHaveProperty('component');
      const cases = children[0].children?.[0];
      expect(cases?.path).toBe('cases');
      expect(cases?.children?.map((child) => child.path)).toEqual(['new', ':id']);
    });

    /** The alias is spelled out, or transloco camel-cases it and misses every key below the scope. */
    it('scopes the loaded routes by the key when the module names no scope of its own', async () => {
      const loadModule = vi.fn().mockResolvedValue(moduleOf(routeDefinitions('open')));

      const children = await childrenOf((await buildAppRoutes({ modules: [{ moduleKey: 'claims', basePath: 'claims' }], loadModule }, renderRoute))[0]);

      expect(scopesOf(children[0])).toContainEqual({ scope: 'claims', alias: 'claims' });
    });

    it('honours the scope the module named', async () => {
      const loadModule = vi.fn().mockResolvedValue(moduleOf(routeDefinitions('open'), { translocoScope: 'claims_module' }));

      const children = await childrenOf((await buildAppRoutes({ modules: [{ moduleKey: 'claims', basePath: 'claims' }], loadModule }, renderRoute))[0]);

      expect(scopesOf(children[0])).toContainEqual({ scope: 'claims_module', alias: 'claims_module' });
    });

    // The regression this guards: TRANSLOCO_SCOPE is a `multi` provider and Angular does not merge those
    // across injectors, so the wrapper's array *replaces* whatever the host registered. Naming only the
    // module's scope left every `base_entity.*` label below a mounted module unresolved — the generic tabs
    // in particular, which a module's ENTITY routes render.
    it('re-registers the framework scopes it would otherwise shadow', async () => {
      const loadModule = vi.fn().mockResolvedValue(moduleOf(routeDefinitions('open'), { translocoScope: 'claims_module' }));

      const children = await childrenOf((await buildAppRoutes({ modules: [{ moduleKey: 'claims', basePath: 'claims' }], loadModule }, renderRoute))[0]);

      expect(scopesOf(children[0])).toEqual([
        { scope: 'claims_module', alias: 'claims_module' },
        { scope: 'base_entity', alias: 'base_entity' },
        { scope: 'base_app', alias: 'base_app' },
      ]);
    });

    // A dangling module key stays a warning at run-time too: the mount resolves to nothing rather than
    // failing the navigation, which is what keeps the two aggregates independently authorable.
    it('resolves a mount naming an unknown module to no routes at all', async () => {
      const loadModule = vi.fn().mockResolvedValue(undefined);

      expect(await childrenOf((await buildAppRoutes({ modules: [{ moduleKey: 'gone', basePath: 'gone' }], loadModule }, renderRoute))[0])).toEqual([]);
    });

    it('resolves a module with no routes to no routes either, rather than an empty wrapper', async () => {
      const loadModule = vi.fn().mockResolvedValue(moduleOf([]));

      expect(await childrenOf((await buildAppRoutes({ modules: [{ moduleKey: 'claims', basePath: 'claims' }], loadModule }, renderRoute))[0])).toEqual([]);
    });

    // An already-known module is emitted now, so the deferral never becomes a second fetch of something
    // the shell is holding — and an empty array there says "no routes", which is honoured as such.
    it('never loads a module the shell already has, empty routes included', async () => {
      const loadModule = vi.fn();

      await buildAppRoutes(
        {
          modules: [
            { moduleKey: 'claims', basePath: 'claims' },
            { moduleKey: 'empty', basePath: 'empty' },
          ],
          moduleRoutes: { claims: routeDefinitions('open'), empty: [] },
          loadModule,
        },
        renderRoute,
      );

      expect(loadModule).not.toHaveBeenCalled();
    });

    it('defers only the mounts it has to, emitting the loaded ones straight away', async () => {
      const routes = await buildAppRoutes(
        {
          modules: [
            { moduleKey: 'claims', basePath: 'claims' },
            { moduleKey: 'orders', basePath: 'orders' },
          ],
          moduleRoutes: { claims: routeDefinitions('open') },
          loadModule: vi.fn(),
        },
        renderRoute,
      );

      expect(routes.map((route) => route.path)).toEqual(['claims', 'orders']);
      expect(routes[0].children).toBeDefined();
      expect(routes[1].loadChildren).toBeDefined();
    });
  });

  it('builds nothing from an empty definition', async () => {
    expect(await buildAppRoutes({}, renderRoute)).toEqual([]);
  });
});
