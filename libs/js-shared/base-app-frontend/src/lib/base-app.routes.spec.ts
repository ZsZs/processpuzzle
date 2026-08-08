import { Route, Routes } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { BASE_APP_ROUTES } from './base-app.routes';

/** The branches an embedded level mounts, expanded one navigation at a time by `loadChildren`. */
async function embeddedBranchesOf(route: Route | undefined): Promise<Routes> {
  const loadChildren = route?.loadChildren;
  return loadChildren ? ((await loadChildren()) as Routes) : [];
}

describe('BASE_APP_ROUTES', () => {
  const [appDefinitionRoute] = BASE_APP_ROUTES;
  const detailsRoute = appDefinitionRoute.children?.find((child) => child.path === ':entityId/details');

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
    expect(appDefinitionRoute.children?.map((child) => child.path)).toEqual(['', ':entityId/details', 'list']);
  });

  it('hangs the regions and the pages below the definition being edited', async () => {
    const branches = await embeddedBranchesOf(detailsRoute);

    // Below the details route, not beside it: an embedded row has no id to be looked up by, so the
    // owner's segments are what address it — and what make it unreachable except through the owner.
    expect(branches.map((branch) => branch.path)).toEqual(['app-region', 'app-page']);
    expect(branches.map((branch) => branch.data?.['entityName'])).toEqual(['App Region', 'App Page']);
    branches.forEach((branch) => expect(branch.data?.['embeddedEntity']).toBe(true));
  });

  it('hangs the nav items and the widgets below the region, and the widgets below the page', async () => {
    const [regionBranch, pageBranch] = await embeddedBranchesOf(detailsRoute);

    expect((await embeddedBranchesOf(await deepestDetailsOf(regionBranch))).map((branch) => branch.path)).toEqual(['app-nav-item', 'app-widget']);
    expect((await embeddedBranchesOf(await deepestDetailsOf(pageBranch))).map((branch) => branch.path)).toEqual(['app-widget']);
  });

  it('lets a nav item and a widget nest in themselves, as a group node and a container widget do', async () => {
    const [regionBranch] = await embeddedBranchesOf(detailsRoute);
    const [navItemBranch, widgetBranch] = await embeddedBranchesOf(await deepestDetailsOf(regionBranch));

    expect((await embeddedBranchesOf(await deepestDetailsOf(navItemBranch))).map((branch) => branch.path)).toEqual(['app-nav-item']);
    expect((await embeddedBranchesOf(await deepestDetailsOf(widgetBranch))).map((branch) => branch.path)).toEqual(['app-widget']);
  });

  it('gives an embedded level a details form and no list of its own', async () => {
    const [regionBranch] = await embeddedBranchesOf(detailsRoute);

    // The rows are already listed on the owner's form, which is also the only place they are reachable
    // from, so a list route here would be a second door to the same room.
    expect((await embeddedBranchesOf(regionBranch)).map((route) => route.path)).toEqual([':entityId/details']);
  });
});

/** The `:entityId/details` route of an embedded branch — the level its own children hang off. */
async function deepestDetailsOf(branch: Route | undefined): Promise<Route | undefined> {
  return (await embeddedBranchesOf(branch)).find((route) => route.path === ':entityId/details');
}
