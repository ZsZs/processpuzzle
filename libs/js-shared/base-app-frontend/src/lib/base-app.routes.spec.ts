import { Route, Routes } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { BASE_APP_ROUTES } from './base-app.routes';
import { APP_PREVIEW_TAB } from './feature/app-preview-tab';

/** The branches an embedded level mounts, expanded one navigation at a time by `loadChildren`. */
async function embeddedBranchesOf(route: Route | undefined): Promise<Routes> {
  const loadChildren = route?.loadChildren;
  return loadChildren ? ((await loadChildren()) as Routes) : [];
}

describe('BASE_APP_ROUTES', () => {
  const [appDefinitionRoute] = BASE_APP_ROUTES;
  const detailsRoute = appDefinitionRoute.children?.find((child) => child.path === ':entityId/details');

  it('registers the two routable aggregates as siblings', () => {
    expect(BASE_APP_ROUTES.map((route) => route.path)).toEqual(['app-definition', 'module-definition']);
  });

  it('uses the snake-cased entity name as path, as the form navigator expects', () => {
    expect(appDefinitionRoute.path).toBe('app-definition');
  });

  it('advertises itself to the design sidenav and to the entity route registry', () => {
    expect(appDefinitionRoute.title).toBeTruthy();
    expect(appDefinitionRoute.data).toEqual({ icon: 'web', menuTitle: 'design.applications', entityName: 'App Definition' });
  });

  it('registers both the generic and this library scope for itself and its children', () => {
    // base_entity is not inherited: the generic tabs resolve `base_entity.tabs.*` and a route that declares
    // TRANSLOCO_SCOPE replaces the inherited collection. The aliases are asserted too, because left to
    // transloco's default they would be camel-cased (`baseApp`, `baseEntity`) and no key would resolve.
    const scopeProviders = (appDefinitionRoute.providers?.flat() ?? []) as Array<{ useValue: unknown }>;

    expect(scopeProviders.map((provider) => provider.useValue)).toEqual([
      { scope: 'base_entity', alias: 'base_entity' },
      { scope: 'base_app', alias: 'base_app' },
    ]);
  });

  it('nests the generic list and details routes', () => {
    expect(appDefinitionRoute.children?.map((child) => child.path)).toEqual(['', ':entityId/details', ':entityId/preview', 'list']);
  });

  it('gives the preview screen a route of its own', () => {
    const previewRoute = appDefinitionRoute.children?.find((child) => child.path === ':entityId/preview');

    expect(previewRoute?.component).toBe(APP_PREVIEW_TAB.component);
    expect(APP_PREVIEW_TAB.segment).toBe('preview');
  });

  it('hangs the regions, the routes and the module mounts below the definition being edited', async () => {
    const branches = await embeddedBranchesOf(detailsRoute);

    // Below the details route, not beside it: an embedded row has no id to be looked up by, so the
    // owner's segments are what address it — and what make it unreachable except through the owner.
    expect(branches.map((branch) => branch.path)).toEqual(['app-region', 'app-route', 'app-module-mount']);
    expect(branches.map((branch) => branch.data?.['entityName'])).toEqual(['App Region', 'App Route', 'App Module Mount']);
    branches.forEach((branch) => expect(branch.data?.['embeddedEntity']).toBe(true));
  });

  it('hangs the nav items and the widgets below the region, and the widgets below the route', async () => {
    const [regionBranch, routeBranch] = await embeddedBranchesOf(detailsRoute);

    expect((await embeddedBranchesOf(await deepestDetailsOf(regionBranch))).map((branch) => branch.path)).toEqual(['app-nav-item', 'app-widget']);
    expect((await embeddedBranchesOf(await deepestDetailsOf(routeBranch))).map((branch) => branch.path)).toEqual(['app-widget']);
  });

  it('stops the module mount branch at itself, a module being an aggregate of its own', async () => {
    const [, , moduleBranch] = await embeddedBranchesOf(detailsRoute);

    expect(await embeddedBranchesOf(await deepestDetailsOf(moduleBranch))).toEqual([]);
  });

  it('lets a nav item nest in itself, as a group node does, and stops the widget branch at one level', async () => {
    const [regionBranch] = await embeddedBranchesOf(detailsRoute);
    const [navItemBranch, widgetBranch] = await embeddedBranchesOf(await deepestDetailsOf(regionBranch));

    expect((await embeddedBranchesOf(await deepestDetailsOf(navItemBranch))).map((branch) => branch.path)).toEqual(['app-nav-item']);
    // A container widget places siblings of this same level by id, so there is no widget-in-widget URL.
    expect(await embeddedBranchesOf(await deepestDetailsOf(widgetBranch))).toEqual([]);
  });

  it('gives an embedded level a details form and no list of its own', async () => {
    const [regionBranch] = await embeddedBranchesOf(detailsRoute);

    // The rows are already listed on the owner's form, which is also the only place they are reachable
    // from, so a list route here would be a second door to the same room.
    expect((await embeddedBranchesOf(regionBranch)).map((route) => route.path)).toEqual([':entityId/details']);
  });
});

describe('BASE_APP_ROUTES module definition branch', () => {
  const moduleDefinitionRoute = BASE_APP_ROUTES[1];
  const detailsRoute = moduleDefinitionRoute.children?.find((child) => child.path === ':entityId/details');

  it('uses the snake-cased entity name as path, as the form navigator expects', () => {
    expect(moduleDefinitionRoute.path).toBe('module-definition');
  });

  it('advertises itself to the design sidenav and to the entity route registry', () => {
    expect(moduleDefinitionRoute.title).toBeTruthy();
    expect(moduleDefinitionRoute.data).toEqual({ icon: 'extension', menuTitle: 'design.modules', entityName: 'Module Definition' });
  });

  it('registers the same two scopes the app branch does', () => {
    const scopeProviders = (moduleDefinitionRoute.providers?.flat() ?? []) as Array<{ useValue: unknown }>;

    expect(scopeProviders.map((provider) => provider.useValue)).toEqual([
      { scope: 'base_entity', alias: 'base_entity' },
      { scope: 'base_app', alias: 'base_app' },
    ]);
  });

  // Routes and nothing else: a module has no regions of its own — a region is chrome, and the chrome
  // belongs to the app that mounts the module.
  it('hangs only the routes below the module being edited', async () => {
    const branches = await embeddedBranchesOf(detailsRoute);

    expect(branches.map((branch) => branch.path)).toEqual(['app-route']);
    expect(branches[0].data?.['entityName']).toBe('App Route');
    expect(branches[0].data?.['embeddedEntity']).toBe(true);
  });

  /** The same `App Route` branch the app definition gets, differing only in the URL prefix above it. */
  it('gives a module route the same widget branch below it', async () => {
    const [routeBranch] = await embeddedBranchesOf(detailsRoute);

    expect((await embeddedBranchesOf(await deepestDetailsOf(routeBranch))).map((branch) => branch.path)).toEqual(['app-widget']);
  });
});

/** The `:entityId/details` route of an embedded branch — the level its own children hang off. */
async function deepestDetailsOf(branch: Route | undefined): Promise<Route | undefined> {
  return (await embeddedBranchesOf(branch)).find((route) => route.path === ':entityId/details');
}
