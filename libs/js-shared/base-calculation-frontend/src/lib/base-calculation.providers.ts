import type { Provider } from '@angular/core';
import type { BaseEntityFacadeRegistry } from '@processpuzzle/base-entity';
import { CALCULATION_DEFINITION_ENTITY_NAME } from './domain/calculation-entity-names';
import { CalculationDefinitionFacade } from './feature/calculation-definition.facade';

/** The facade of the calculation definitions, to be spread into the application's `providers`. */
export const BASE_CALCULATION_FACADE_PROVIDERS: Provider[] = [CalculationDefinitionFacade];

/** The same facade keyed by entity name, to be spread into the application's `BASE_ENTITY_FACADE_REGISTRY`. */
export const BASE_CALCULATION_ENTITY_FACADES: BaseEntityFacadeRegistry = {
  [CALCULATION_DEFINITION_ENTITY_NAME]: CalculationDefinitionFacade,
};
