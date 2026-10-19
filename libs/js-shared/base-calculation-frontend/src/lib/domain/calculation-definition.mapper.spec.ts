import { describe, expect, it } from 'vitest';
import { CalculationDefinition } from './calculation-definition';
import { CalculationDefinitionMapper } from './calculation-definition.mapper';
import { ORDER_TOTAL_DTO, SERIES_RANKING_DTO } from './test-calculation-definition';

describe('CalculationDefinitionMapper', () => {
  const mapper = new CalculationDefinitionMapper();

  it('reads everything the backend derived from the source', () => {
    const definition = mapper.fromDto(ORDER_TOTAL_DTO);

    expect(definition).toBeInstanceOf(CalculationDefinition);
    expect(definition).toMatchObject({ id: 'order-total', name: 'Order total', definitionVersion: 2, outputEntity: 'order', version: 4, releaseState: 'RELEASED' });
    expect(definition.parameters.map((parameter) => parameter.name)).toEqual(['order', 'customerTier']);
  });

  it('turns nulls into absent values', () => {
    const definition = mapper.fromDto({ ...SERIES_RANKING_DTO, parameters: undefined });

    expect(definition.name).toBeUndefined();
    expect(definition.parameters).toEqual([]);
  });

  it('sends the source and the lock only', () => {
    expect(mapper.toDto(mapper.fromDto(ORDER_TOTAL_DTO))).toEqual({ source: ORDER_TOTAL_DTO.source, version: 4 });
  });
});
