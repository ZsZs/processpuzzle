import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Route, Routes } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { TranslocoHttpLoader } from '@processpuzzle/util';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppDefinition, RouteDefinition } from '../../domain/app-definition';
import { AppDefinitionService } from '../../domain/app-definition.service';
import { AppDefinitionStore } from '../../domain/app-definition.store';
import { ModuleDefinition } from '../../domain/module-definition';
import { ModuleDefinitionService } from '../../domain/module-definition.service';
import { ModuleDefinitionStore } from '../../domain/module-definition.store';
import { AppAssetService } from '../../domain/app-asset.service';
import { ModuleAssetService } from '../../domain/module-asset.service';
import { ModuleTranslationService } from '../../domain/module-translation.service';
import { APP_ASSET_CONTEXT, MODULE_ASSET_CONTEXT } from '@processpuzzle/widgets';

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
  const shippedTranslations = { getTranslation: vi.fn() };
  const moduleTranslations = { bundle: vi.fn() };
  const moduleAssets = { content: vi.fn() };
  const appAssets = { content: vi.fn() };

  function factory(): AppShellRoutesFactory {
    TestBed.configureTestingModule({
      providers: [
        { provide: AppDefinitionStore, useValue: appStore },
        { provide: AppDefinitionService, useValue: appService },
        { provide: ModuleDefinitionStore, useValue: moduleStore },
        { provide: ModuleDefinitionService, useValue: moduleService },
        { provide: TranslocoService, useValue: { getAvailableLangs: () => ['en', { id: 'de', label: 'Deutsch' }], getActiveLang: () => 'en' } },
        { provide: TranslocoHttpLoader, useValue: shippedTranslations },
        { provide: ModuleTranslationService, useValue: moduleTranslations },
        { provide: ModuleAssetService, useValue: moduleAssets },
        { provide: AppAssetService, useValue: appAssets },
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
    shippedTranslations.getTranslation.mockReturnValue(of({}));
    moduleTranslations.bundle.mockReturnValue(of({}));
  });

  it('renders one route per authored route, through AppRouteRenderer', async () => {
    appStore.loadById.mockReturnValue(
      new AppDefinition({ id: 'demo', routes: [widgetsRoute('orders'), new RouteDefinition({ path: 'claims', title: 'Claims', kind: 'ENTITY', entityName: 'Claim' })] }),
    );

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

  describe('a mounted module' + "'s translations", () => {
    type Loaders = Record<string, () => Promise<Record<string, string>>>;

    /** The inline loaders the module's scope was registered with, once something navigates into the mount. */
    async function loadersOf(definition: AppDefinition): Promise<Loaders> {
      const children = await factory().childrenFor(definition);
      const mount = children.find((route) => route.path === 'back-office') as Route;
      const wrapper = (await expand(mount))[0];
      const scopes = (wrapper.providers?.flat() ?? []) as Array<{ useValue: { loader?: Loaders } }>;
      return scopes[0].useValue.loader as Loaders;
    }

    const mountOf = (fields: Partial<AppDefinition> = {}) => new AppDefinition({ id: 'demo', modules: [{ moduleKey: 'order-admin', basePath: 'back-office' }], mountedModules: undefined, ...fields });

    beforeEach(() => {
      moduleStore.loadById.mockReturnValue(new ModuleDefinition({ id: 'order-admin', translocoScope: 'order_admin', routes: [widgetsRoute('lines')] }));
    });

    it('registers a loader for every language transloco is configured for, definitions of the language included', async () => {
      expect(Object.keys(await loadersOf(mountOf()))).toEqual(['en', 'de']);
    });

    it('lays what was uploaded over what the scope already ships, so a seeded bundle keeps its other words', async () => {
      shippedTranslations.getTranslation.mockReturnValue(of({ route: { orders: 'Alle Aufträge' }, 'module.name': 'Alt' }));
      moduleTranslations.bundle.mockReturnValue(of({ 'module.name': 'Auftragsverwaltung' }));

      const loaded = await (await loadersOf(mountOf()))['de']();

      expect(shippedTranslations.getTranslation).toHaveBeenCalledWith('order_admin/de', { scope: 'order_admin' });
      expect(loaded).toEqual({ route: { orders: 'Alle Aufträge' }, 'module.name': 'Auftragsverwaltung' });
    });

    it('reads the draft translations for a draft definition, as the designer previews', async () => {
      await (await loadersOf(mountOf()))['de']();

      expect(moduleTranslations.bundle).toHaveBeenCalledWith('order-admin', 'de', 'draft');
    });

    it('reads the released translations for a published layout, which carries released modules only', async () => {
      const released = mountOf({ mountedModules: [new ModuleDefinition({ id: 'order-admin', translocoScope: 'order_admin', routes: [widgetsRoute('lines')] })] });

      await (await loadersOf(released))['de']();

      expect(moduleTranslations.bundle).toHaveBeenCalledWith('order-admin', 'de', 'released');
    });

    /** The asset context the module's wrapper provides, once something navigates into the mount. */
    async function assetContextOf(definition: AppDefinition): Promise<{ blob(path: string): unknown }> {
      const children = await factory().childrenFor(definition);
      const wrapper = (await expand(children.find((route) => route.path === 'back-office') as Route))[0];
      const providers = (wrapper.providers?.flat() ?? []) as Array<{ provide?: unknown; useValue: { blob(path: string): unknown } }>;
      return providers.find((provider) => provider.provide === MODULE_ASSET_CONTEXT)?.useValue as { blob(path: string): unknown };
    }

    it('gives the module its draft assets in the Preview', async () => {
      (await assetContextOf(mountOf())).blob('docs/a.md');

      expect(moduleAssets.content).toHaveBeenCalledWith('order-admin', 'docs/a.md', 'draft');
    });

    it('gives the module its released assets in a published layout', async () => {
      const released = mountOf({ mountedModules: [new ModuleDefinition({ id: 'order-admin', routes: [widgetsRoute('lines')] })] });

      (await assetContextOf(released)).blob('docs/a.md');

      expect(moduleAssets.content).toHaveBeenCalledWith('order-admin', 'docs/a.md', 'released');
    });

    /** The asset context every top-level route provides, found on the application's own page or on a module mount. */
    async function appAssetContextsOf(definition: AppDefinition, paths: string[]): Promise<Array<{ blob(path: string): unknown }>> {
      const children = await factory().childrenFor(definition);
      return paths.map((path) => appAssetContextIn(children, path));
    }

    function appAssetContextIn(children: Routes, path: string): { blob(path: string): unknown } {
      const providers = ((children.find((route) => route.path === path) as Route).providers?.flat() ?? []) as Array<{ provide?: unknown; useFactory?: () => { blob(path: string): unknown } }>;
      const provider = providers.find((candidate) => candidate.provide === APP_ASSET_CONTEXT);
      return TestBed.runInInjectionContext(() => (provider as { useFactory: () => { blob(path: string): unknown } }).useFactory());
    }

    it('gives the application own pages and its module mounts the draft assets of the application in the Preview', async () => {
      const preview = mountOf({ routes: [widgetsRoute('orders')] });
      for (const context of await appAssetContextsOf(preview, ['orders', 'back-office'])) {
        context.blob('cards/a.svg');
      }

      expect(appAssets.content).toHaveBeenCalledTimes(2);
      expect(appAssets.content).toHaveBeenCalledWith(preview.id, 'cards/a.svg', 'draft');
    });

    it('gives them the released assets in a published layout', async () => {
      const released = mountOf({ routes: [widgetsRoute('orders')], mountedModules: [new ModuleDefinition({ id: 'order-admin', routes: [widgetsRoute('lines')] })] });

      (await appAssetContextsOf(released, ['orders']))[0].blob('cards/a.svg');

      expect(appAssets.content).toHaveBeenCalledWith(released.id, 'cards/a.svg', 'released');
    });

    it('keeps the titles readable when the uploaded translations cannot be read', async () => {
      shippedTranslations.getTranslation.mockReturnValue(of({ 'module.name': 'Auftragsverwaltung' }));
      moduleTranslations.bundle.mockReturnValue(throwError(() => new Error('boom')));

      expect(await (await loadersOf(mountOf()))['de']()).toEqual({ 'module.name': 'Auftragsverwaltung' });
    });
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

  describe('a layout that carries its modules', () => {
    const published = new AppDefinition({
      id: 'demo',
      routes: [widgetsRoute('orders')],
      modules: [
        { moduleKey: 'order-admin', basePath: 'back-office' },
        { moduleKey: 'unreleased', basePath: 'later' },
      ],
      mountedModules: [new ModuleDefinition({ id: 'order-admin', routes: [widgetsRoute('lines')] })],
    });

    it('mounts the modules it carries, and never reads a module on its own', async () => {
      moduleStore.loadById.mockReturnValue(new ModuleDefinition({ id: 'order-admin', routes: [widgetsRoute('draft-only')] }));

      const mount = (await factory().childrenFor(published)).find((route) => route.path === 'back-office');
      const [wrapper] = await expand(mount as Route);

      expect(wrapper.children?.map((route) => route.path)).toEqual(['lines']);
      expect(moduleStore.loadById).not.toHaveBeenCalled();
      expect(moduleService.findById).not.toHaveBeenCalled();
    });

    it('contributes nothing for a mount whose module it does not carry', async () => {
      const mount = (await factory().childrenFor(published)).find((route) => route.path === 'later');

      expect(await expand(mount as Route)).toEqual([]);
      expect(moduleService.findById).not.toHaveBeenCalled();
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
    const built: Routes = [
      { path: '', pathMatch: 'full', redirectTo: 'orders' },
      { path: 'orders', component: RouteWidgetsComponent },
    ];
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
