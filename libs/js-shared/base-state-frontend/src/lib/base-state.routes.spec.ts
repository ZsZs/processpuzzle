import { Route, Routes } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { BASE_STATE_ROUTES } from './base-state.routes';

/** The branches an embedded level mounts, expanded one navigation at a time by `loadChildren`. */
async function embeddedBranchesOf(route: Route | undefined): Promise<Routes> {
  const loadChildren = route?.loadChildren;
  return loadChildren ? ((await loadChildren()) as Routes) : [];
}

/** The `:entityId/details` route of an embedded branch — the level its own children hang off. */
async function deepestDetailsOf(branch: Route | undefined): Promise<Route | undefined> {
  return (await embeddedBranchesOf(branch)).find((route) => route.path === ':entityId/details');
}

describe('BASE_STATE_ROUTES', () => {
  const [definitionRoute] = BASE_STATE_ROUTES;
  const detailsRoute = definitionRoute.children?.find((child) => child.path === ':entityId/details');

  it('registers the state machine definition as its only routable aggregate', () => {
    expect(BASE_STATE_ROUTES.map((route) => route.path)).toEqual(['state-machine-definition']);
  });

  it('uses the snake-cased entity name as path, as the form navigator expects', () => {
    expect(definitionRoute.path).toBe('state-machine-definition');
  });

  it('advertises itself to the sidenav and to the entity route registry', () => {
    expect(definitionRoute.title).toBeTruthy();
    expect(definitionRoute.data).toEqual({ icon: 'flag_circle', menuTitle: 'state.machines', entityName: 'State Machine Definition' });
  });

  it('binds the container to this feature facade and registers both transloco scopes', () => {
    // base_entity is not inherited: the generic tabs resolve `base_entity.tabs.*` and a route that
    // declares TRANSLOCO_SCOPE replaces the inherited collection. The aliases are asserted too, because
    // left to transloco's default they would be camel-cased (`baseState`) and no key would resolve.
    const providers = (definitionRoute.providers?.flat() ?? []) as Array<{ useValue?: unknown; provide?: unknown }>;

    expect(providers[0].provide).toBeDefined();
    expect(providers.slice(1).map((provider) => provider.useValue)).toEqual([
      { scope: 'base_entity', alias: 'base_entity' },
      { scope: 'base_state', alias: 'base_state' },
    ]);
  });

  it('nests the generic list and details routes', () => {
    expect(definitionRoute.children?.map((child) => child.path)).toEqual(['', ':entityId/details', 'list']);
  });

  it('hangs the states and the transitions below the machine being edited', async () => {
    const branches = await embeddedBranchesOf(detailsRoute);

    // Below the details route, not beside it: an embedded row has no id to be looked up by, so the
    // owner's segments are what address it — and what make it unreachable except through the owner.
    expect(branches.map((branch) => branch.path)).toEqual(['state-machine-state', 'state-machine-transition']);
    expect(branches.map((branch) => branch.data?.['entityName'])).toEqual(['State Machine State', 'State Machine Transition']);
    branches.forEach((branch) => expect(branch.data?.['embeddedEntity']).toBe(true));
  });

  it('hangs the guards and the actions below the transition', async () => {
    const [, transitionBranch] = await embeddedBranchesOf(detailsRoute);

    const branches = await embeddedBranchesOf(await deepestDetailsOf(transitionBranch));
    expect(branches.map((branch) => branch.path)).toEqual(['state-transition-guard', 'state-transition-action']);
    expect(branches.map((branch) => branch.data?.['entityName'])).toEqual(['State Transition Guard', 'State Transition Action']);
  });

  // No parallel and no nested states in this version of the contract, so a state has no deeper level;
  // a bean reference contains nothing but its params.
  it('stops at the state, the guard and the action', async () => {
    const [stateBranch, transitionBranch] = await embeddedBranchesOf(detailsRoute);
    const [guardBranch, actionBranch] = await embeddedBranchesOf(await deepestDetailsOf(transitionBranch));

    expect(await embeddedBranchesOf(await deepestDetailsOf(stateBranch))).toEqual([]);
    expect(await embeddedBranchesOf(await deepestDetailsOf(guardBranch))).toEqual([]);
    expect(await embeddedBranchesOf(await deepestDetailsOf(actionBranch))).toEqual([]);
  });

  it('gives an embedded level a details form and no list of its own', async () => {
    const [stateBranch] = await embeddedBranchesOf(detailsRoute);

    // The rows are already listed on the owner's form, which is also the only place they are reachable
    // from, so a list route here would be a second door to the same room.
    expect((await embeddedBranchesOf(stateBranch)).map((route) => route.path)).toEqual([':entityId/details']);
  });
});
