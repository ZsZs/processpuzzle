import { describe, expect, it } from 'vitest';
import { BaseEntityContainerComponent } from '@processpuzzle/base-entity';
import { BASE_CALCULATION_ROUTES, CALCULATION_RUNS_ROUTES } from './base-calculation.routes';
import { CalculationRunsComponent } from './feature/runs/calculation-runs.component';
import { CalculationSourceTabComponent } from './feature/source/calculation-source-tab.component';

describe('BASE_CALCULATION_ROUTES', () => {
  const [definitionRoute] = BASE_CALCULATION_ROUTES;

  it('registers the calculation definition at the snake-cased entity name', () => {
    expect(definitionRoute.path).toBe('calculation-definition');
    expect(definitionRoute.data).toEqual({ icon: 'calculate', menuTitle: 'calculation.definitions', entityName: 'Calculation Definition' });
    expect(definitionRoute.component).toBe(BaseEntityContainerComponent);
  });

  it('registers both transloco scopes with their aliases', () => {
    const providers = (definitionRoute.providers?.flat() ?? []) as Array<{ useValue?: unknown }>;

    expect(providers.slice(1).map((provider) => provider.useValue)).toEqual([
      { scope: 'base_entity', alias: 'base_entity' },
      { scope: 'base_calculation', alias: 'base_calculation' },
    ]);
  });

  it('mounts the list, the details and the three tabs', () => {
    expect(definitionRoute.children?.map((child) => child.path)).toEqual(['', ':entityId/details', ':entityId/source', ':entityId/tables', ':entityId/evaluate', 'list']);
    expect(definitionRoute.children?.find((child) => child.path === ':entityId/source')?.component).toBe(CalculationSourceTabComponent);
  });
});

describe('CALCULATION_RUNS_ROUTES', () => {
  it('mounts the runs screen on its own', () => {
    expect(CALCULATION_RUNS_ROUTES.map((route) => route.path)).toEqual(['calculation-runs']);
    expect(CALCULATION_RUNS_ROUTES[0].component).toBe(CalculationRunsComponent);
    expect(CALCULATION_RUNS_ROUTES[0].providers).toHaveLength(1);
  });
});
