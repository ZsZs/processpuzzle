import { describe, expect, it } from 'vitest';
import { BASE_CALCULATION_ENTITY_FACADES, BASE_CALCULATION_FACADE_PROVIDERS } from './base-calculation.providers';
import { CalculationDefinitionFacade } from './feature/calculation-definition.facade';

describe('base-calculation providers', () => {
  it('provides the facade and registers it under the entity name', () => {
    expect(BASE_CALCULATION_FACADE_PROVIDERS).toEqual([CalculationDefinitionFacade]);
    expect(BASE_CALCULATION_ENTITY_FACADES).toEqual({ 'Calculation Definition': CalculationDefinitionFacade });
  });
});
