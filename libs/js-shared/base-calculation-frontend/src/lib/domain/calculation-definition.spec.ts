import { describe, expect, it } from 'vitest';
import { CalculationDefinition, NEW_CALCULATION_SOURCE } from './calculation-definition';

describe('CalculationDefinition', () => {
  it('starts a new definition from a source that validates', () => {
    const definition = new CalculationDefinition();

    expect(definition.id).toBe('');
    expect(definition.source).toBe(NEW_CALCULATION_SOURCE);
    expect(definition.parameters).toEqual([]);
  });

  it('takes what it is given', () => {
    const definition = new CalculationDefinition({ id: 'a', source: 'calculation: a\n', definitionVersion: 3, outputEntity: 'order', version: 1 });

    expect(definition).toMatchObject({ id: 'a', source: 'calculation: a\n', definitionVersion: 3, outputEntity: 'order', version: 1 });
  });
});
