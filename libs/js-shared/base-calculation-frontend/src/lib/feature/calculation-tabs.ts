import type { EntityTabDescriptor } from '@processpuzzle/base-entity';
import { CALCULATION_EVALUATE_I18N_SCOPE, CALCULATION_SOURCE_I18N_SCOPE, CALCULATION_TABLES_I18N_SCOPE } from '../base-calculation.i18n';
import { CalculationEvaluateTabComponent } from './evaluate/calculation-evaluate-tab.component';
import { CalculationSourceTabComponent } from './source/calculation-source-tab.component';
import { CalculationTablesTabComponent } from './tables/calculation-tables-tab.component';

/**
 * The three screens of a calculation beyond List and Details, each declared once and consumed twice — by
 * {@link BASE_CALCULATION_ROUTES}, which mounts `calculation-definition/:entityId/<segment>`, and by the facade,
 * which puts them on the descriptor so the tab bar renders the links. See `WORKFLOW_MODELER_TAB`.
 */
export const CALCULATION_SOURCE_TAB: EntityTabDescriptor = {
  segment: 'source',
  i18nKey: `${CALCULATION_SOURCE_I18N_SCOPE}.tab`,
  component: CalculationSourceTabComponent,
};

export const CALCULATION_TABLES_TAB: EntityTabDescriptor = {
  segment: 'tables',
  i18nKey: `${CALCULATION_TABLES_I18N_SCOPE}.tab`,
  component: CalculationTablesTabComponent,
};

export const CALCULATION_EVALUATE_TAB: EntityTabDescriptor = {
  segment: 'evaluate',
  i18nKey: `${CALCULATION_EVALUATE_I18N_SCOPE}.tab`,
  component: CalculationEvaluateTabComponent,
};

export const CALCULATION_TABS: EntityTabDescriptor[] = [CALCULATION_SOURCE_TAB, CALCULATION_TABLES_TAB, CALCULATION_EVALUATE_TAB];
