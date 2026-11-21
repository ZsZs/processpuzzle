import { describe, expect, it } from 'vitest';
import { sumMeasures } from './measure-display';

describe('sumMeasures', () => {
  it('adds money of one currency and quantities of one unit', () => {
    expect(sumMeasures([{ amount: '0.1', currency: 'EUR' }, { amount: '0.2', currency: 'EUR' }])).toEqual({ amount: '0.3', currency: 'EUR' });
    expect(sumMeasures([{ amount: '2', unit: 'pc' }, { amount: '3', unit: 'pc' }])).toEqual({ amount: '5', unit: 'pc' });
  });

  it('is undefined for nothing, mixed currencies or units, and values that are not measures', () => {
    expect(sumMeasures([])).toBeUndefined();
    expect(sumMeasures([1, 2])).toBeUndefined();
    expect(sumMeasures([{ amount: '1', currency: 'EUR' }, { amount: '1', currency: 'USD' }])).toBeUndefined();
    expect(sumMeasures([{ amount: '1', unit: 'kg' }, { amount: '1', unit: 'g' }])).toBeUndefined();
    expect(sumMeasures([{ amount: '1', unit: 'kg' }, { width: '1', height: '1', unit: 'kg' }])).toBeUndefined();
  });
});
