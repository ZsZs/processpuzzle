import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Route, Routes } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDefinition, RouteDefinition } from '../../domain/app-definition';
import { AppDefinitionService } from '../../domain/app-definition.service';
import { AppDefinitionStore } from '../../domain/app-definition.store';
import { ModuleDefinition } from '../../domain/module-definition';
import { ModuleDefinitionService } from '../../domain/module-definition.service';
import { ModuleDefinitionStore } from '../../domain/module-definition.store';

import { BaseEntityScreensComponent } from '@processpuzzle/base-entity';
import { RouteWidgetsComponent } from '../route-widgets.component';
import { AppShellRoutesFactory, appShellRoutesGuard } from './app-shell-routes';

function widgetsRoute(path: string): RouteDefinition {
  return new RouteDefinition({ path, title: path, kind: 'WIDGETS' });
}

/** `loadChildren` resolves the routes a lazy module mount contributes. */
async function expand(route: Route): Promise<Routes> {
  return route.loadChildren ? ((await route.loadChildren()) as Routes) : [];
}

describe('AppShellRoutesFactory', () => {
  const appStore = { loadById: vi.fn() };
  const appService = { findById: vi.fn() };
  const moduleStore = { loadById: vi.fn() };
  const moduleService = { findById: vi.fn() };

  function factory(): AppShellRoutesFactory {
    TestBed.configureTestingModule({
      providers: [
        { provide: AppDefinitionStore, useValue: appStore },
        { provide: AppDefinitionService, useValue: appService },
        { provide: ModuleDefinitionStore, useValue: moduleStore },
        { provide: ModuleDefinitionService, useValue: moduleService },
      ],
    });
    return TestBed.inject(AppShellRoutesFactory);
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.resetAllMocks();
    appStore.loadById.mockReturnValue(undefined);
    moduleStore.loadById.mockReturnValue(undefined);
    appService.findById.mockReturnValue(of(undefined));
    moduleService.findById.mockReturnValue(of(undefined));
  });

  it('renders one route per authored route, through AppRouteRenderer', async () => {
    appStore.loadById.mockReturnValue(new AppDefinition({ id: 'demo', routes: [widgetsRoute('orders'), new RouteDefinition({ path: 'claims', title: 'Claims', kind: 'ENTITY', entityName: 'Claim' })] }));

    const children = await factory().childrenOf('demo');

    expect(children.map((route) => route.path)).toEqual(['', 'orders', 'claims']);
    expect(children.find((route) => route.path === 'orders')?.component).toBe(RouteWidgetsComponent);
    expect(children.find((route) => route.path === 'claims')?.component).toBe(BaseEntityScreensComponent);
  });

  it('lands the application root on its first authored route', async () => {
    appStore.loadById.mockReturnValue(new AppDefinition({ id: 'demo', routes: [widgetsRoute('orders'), widgetsRoute('claims')] }));

    const children = await factory().childrenOf('demo');

    // pathMatch full, or an empty path would prefix-match every URL below it.
    expect(children[0]).toEqual({ path: '', pathMatch: 'full', redirectTo: 'orders' });
  });

  it('falls back to the first built route when the app declares none of its own', async () => {
    // Every screen comes from a mounted module, so the definition offers no path to land on.
    appStore.loadById.mockReturnValue(new AppDefinition({ id: 'demo', modules: [{ moduleKey: 'order-admin', basePath: 'back-office' }] }));
    moduleStore.loadById.mockReturnValue(new ModuleDefinition({ id: 'order-admin', routes: [widgetsRoute('lines')] }));

    const children = await factory().childrenOf('demo');

    expect(children[0]).toEqual({ path: '', pathMatch: 'full', redirectTo: 'back-office' });
  });

  it('adds no index route when the app has no routes at all', async () => {
    appStore.loadById.mockReturnValue(new AppDefinition({ id: 'demo' }));

    expect(await factory().childrenOf('demo')).toEqual([]);
  });

  it('mounts a module under its base path', async () => {
    appStore.loadById.mockReturnValue(new AppDefinition({ id: 'demo', routes: [widgetsRoute('orders')], modules: [{ moduleKey: 'order-admin', basePath: 'back-office' }] }));
    moduleStore.loadById.mockReturnValue(new ModuleDefinition({ id: 'order-admin', routes: [widgetsRoute('lines')] }));

    const children = await factory().childrenOf('demo');
    const mount = children.find((route) => route.path === 'back-office');

    expect(await expand(mount as Route)).not.toEqual([]);
  });

  describe('reading the definition', () => {
    it('prefers the store, so a route added by a Save is picked up without a second GET', async () => {
      appStore.loadById.mockReturnValue(new AppDefinition({ id: 'demo', routes: [widgetsRoute('orders')] }));

      await factory().childrenOf('demo');

      expect(appService.findById).not.toHaveBeenCalled();
    });

    it('fetches when the store cannot serve it, as on a deep link', async () => {
      appService.findById.mockReturnValue(of(new AppDefinition({ id: 'demo', routes: [widgetsRoute('orders')] })));

      const children = await factory().childrenOf('demo');

      expect(appService.findById).toHaveBeenCalledWith('demo');
      expect(children.map((route) => route.path)).toEqual(['', 'orders']);
    });

    it('yields no routes rather than failing the navigation when the definition cannot be read', async () => {
      appService.findById.mockReturnValue(throwError(() => new Error('boom')));

      expect(await factory().childrenOf('demo')).toEqual([]);
    });

    it('yields no routes for a definition the backend does not have', async () => {
      appService.findById.mockReturnValue(of(undefined));

      expect(await factory().childrenOf('missing')).toEqual([]);
    });
  });

  describe('reading a mounted module', () => {
    beforeEach(() => {
      appStore.loadById.mockReturnValue(new AppDefinition({ id: 'demo', routes: [widgetsRoute('orders')], modules: [{ moduleKey: 'ghost', basePath: 'ghost-town' }] }));
    });

    it('contributes nothing for a mount naming a module nobody authored', async () => {
      // A dangling moduleKey is a validation warning by contract, so it must not break navigation.
      moduleService.findById.mockReturnValue(of(undefined));

      const mount = (await factory().childrenOf('demo')).find((route) => route.path === 'ghost-town');

      expect(await expand(mount as Route)).toEqual([]);
    });

    it('contributes nothing when reading the module fails', async () => {
      moduleService.findById.mockReturnValue(throwError(() => new Error('boom')));

      const mount = (await factory().childrenOf('demo')).find((route) => route.path === 'ghost-town');

      expect(await expand(mount as Route)).toEqual([]);
    });
  });
});

describe('appShellRoutesGuard', () => {
  const factoryStub = { childrenOf: vi.fn() };

  function runGuard(route: Route, appId: string | undefined) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [{ provide: AppShellRoutesFactory, useValue: factoryStub }] });
    const snapshot = { paramMap: { get: () => appId ?? null } } as never;

    // The guard calls inject(), which the router does for it via runInInjectionContext.
    return runInInjectionContext(TestBed.inject(EnvironmentInjector), () => appShellRoutesGuard(route, [], snapshot));
  }

  beforeEach(() => vi.resetAllMocks());

  it('fills the tab route’s children with the previewed application’s routes', async () => {
    const built: Routes = [{ path: '', pathMatch: 'full', redirectTo: 'orders' }, { path: 'orders', component: RouteWidgetsComponent }];
    factoryStub.childrenOf.mockResolvedValue(built);
    const route: Route = { path: ':entityId/preview', children: [] };

    await expect(runGuard(route, 'demo')).resolves.toBe(true);
    expect(factoryStub.childrenOf).toHaveBeenCalledWith('demo');
    expect(route.children).toBe(built);
  });

  it('matches without touching children when the URL carries no entity id', () => {
    const route: Route = { path: ':entityId/preview', children: [] };

    expect(runGuard(route, undefined)).toBe(true);
    expect(route.children).toEqual([]);
    expect(factoryStub.childrenOf).not.toHaveBeenCalled();
  });

  it('still matches when the application has no routes, so the tab renders an empty outlet', async () => {
    factoryStub.childrenOf.mockResolvedValue([]);
    const route: Route = { path: ':entityId/preview', children: [] };

    await expect(runGuard(route, 'demo')).resolves.toBe(true);
    expect(route.children).toEqual([]);
  });
});
