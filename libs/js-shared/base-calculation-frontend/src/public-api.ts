/*
 * Public API Surface of @processpuzzle/base-calculation
 */
export {
  BASE_CALCULATION_TRANSLATION_SOURCE,
  BASE_CALCULATION_TRANSLOCO_SCOPE,
  CALCULATION_DEFINITION_I18N_SCOPE,
  CALCULATION_EVALUATE_I18N_SCOPE,
  CALCULATION_RUNS_I18N_SCOPE,
  CALCULATION_SOURCE_I18N_SCOPE,
  CALCULATION_TABLES_I18N_SCOPE,
} from './lib/base-calculation.i18n';
export { CalculationDefinition, NEW_CALCULATION_SOURCE, type CalculationParameter } from './lib/domain/calculation-definition';
export { CALCULATION_DEFINITION_ENTITY_NAME, createCalculationDefinitionDescriptor } from './lib/domain/calculation-definition.descriptors';
export { CalculationDefinitionMapper, type CalculationDefinitionDto } from './lib/domain/calculation-definition.mapper';
export { CalculationDefinitionService } from './lib/domain/calculation-definition.service';
export { CalculationDefinitionStore } from './lib/domain/calculation-definition.store';
export { CalculationApiService } from './lib/domain/calculation-api.service';
export type { CalculationRun, DecisionNode, EvaluationResult, Revision, RunStatus, TraceEntry, ValidationReport } from './lib/domain/calculation-api';
export { HIT_POLICIES, newTable, readTables, removeTable, writeTable, yamlErrorsOf, type DecisionTableModel, type HitPolicy, type TableRuleModel } from './lib/domain/calculation-source';
export { CalculationDefinitionFacade } from './lib/feature/calculation-definition.facade';
export { CALCULATION_EVALUATE_TAB, CALCULATION_SOURCE_TAB, CALCULATION_TABLES_TAB, CALCULATION_TABS } from './lib/feature/calculation-tabs';
export { CalculationSourceEditorComponent } from './lib/feature/source/calculation-source-editor.component';
export { CalculationSourceTabComponent } from './lib/feature/source/calculation-source-tab.component';
export { CalculationTablesTabComponent } from './lib/feature/tables/calculation-tables-tab.component';
export { CalculationEvaluateTabComponent } from './lib/feature/evaluate/calculation-evaluate-tab.component';
export { CalculationRunsComponent } from './lib/feature/runs/calculation-runs.component';
export { CalculationTraceComponent } from './lib/feature/runs/calculation-trace.component';
export { BASE_CALCULATION_ENTITY_FACADES, BASE_CALCULATION_FACADE_PROVIDERS } from './lib/base-calculation.providers';
export { BASE_CALCULATION_ROUTES, CALCULATION_RUNS_ROUTES } from './lib/base-calculation.routes';
