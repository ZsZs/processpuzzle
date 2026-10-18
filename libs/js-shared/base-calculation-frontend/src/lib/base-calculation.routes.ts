import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ACTIVE_ENTITY_FACADE, BaseEntityContainerComponent, baseEntityRoutes } from '@processpuzzle/base-entity';
import { BASE_CALCULATION_TRANSLOCO_SCOPE, BASE_ENTITY_TRANSLOCO_SCOPE } from './base-calculation.i18n';
import { CALCULATION_DEFINITION_ENTITY_NAME } from './domain/calculation-entity-names';
import { CalculationDefinitionFacade } from './feature/calculation-definition.facade';
import { CALCULATION_TABS } from './feature/calculation-tabs';
import { CalculationRunsComponent } from './feature/runs/calculation-runs.component';

/** Both transloco scopes, with their aliases spelled out — see base-event's routes. */
function translocoScopes() {
  return provideTranslocoScope(
    { scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE },
    { scope: BASE_CALCULATION_TRANSLOCO_SCOPE, alias: BASE_CALCULATION_TRANSLOCO_SCOPE },
  );
}

/**
 * The authoring branch: the list and form of a `Calculation Definition`, and its Source, Decision tables and
 * Evaluate tabs. The path segment is `snakeCaseName('Calculation Definition')`, which the form navigator builds
 * the details URL from.
 */
export const BASE_CALCULATION_ROUTES: Routes = [
  {
    path: 'calculation-definition',
    title: 'ProcessPuzzle - Calculations',
    data: { icon: 'calculate', menuTitle: 'calculation.definitions', entityName: CALCULATION_DEFINITION_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: CalculationDefinitionFacade }, translocoScopes()],
    children: baseEntityRoutes([], CALCULATION_TABS),
  },
];

/** The Calculation runs screen, standalone: runs outlive the definitions they ran. */
export const CALCULATION_RUNS_ROUTES: Routes = [
  {
    path: 'calculation-runs',
    title: 'ProcessPuzzle - Calculation runs',
    data: { icon: 'history', menuTitle: 'calculation.runs' },
    component: CalculationRunsComponent,
    providers: [translocoScopes()],
  },
];
