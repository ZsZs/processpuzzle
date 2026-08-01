import { describe, expect, it } from 'vitest';
import { BASE_APP_ROUTES } from './base-app.routes';

describe('BASE_APP_ROUTES', () => {
  const [appDefinitionRoute] = BASE_APP_ROUTES;

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
});
