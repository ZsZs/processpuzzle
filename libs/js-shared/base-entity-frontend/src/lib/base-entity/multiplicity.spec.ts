import { describe, expect, it } from 'vitest';
import { isBoundedByMaxOccurs, isMultiValued, MULTIPLICITIES, upperBound } from './multiplicity';

describe('multiplicity', () => {
  it('lists the five forms', () => {
    expect(MULTIPLICITIES).toEqual(['0..1', '0..x', '0..n', '1..x', '1..n']);
  });

  it('upperBound follows the form', () => {
    expect(upperBound({})).toBe(1);
    expect(upperBound({ multiplicity: '0..1' })).toBe(1);
    expect(upperBound({ multiplicity: '0..x', maxOccurs: 3 })).toBe(3);
    expect(upperBound({ multiplicity: '1..x' })).toBe(1);
    expect(upperBound({ multiplicity: '0..n' })).toBe(Infinity);
    expect(upperBound({ multiplicity: '1..n', maxOccurs: 2 })).toBe(Infinity);
  });

  it('isMultiValued means an upper bound above 1', () => {
    expect(isMultiValued({})).toBe(false);
    expect(isMultiValued({ multiplicity: '1..x', maxOccurs: 1 })).toBe(false);
    expect(isMultiValued({ multiplicity: '1..x', maxOccurs: 2 })).toBe(true);
    expect(isMultiValued({ multiplicity: '0..n' })).toBe(true);
  });

  it('isBoundedByMaxOccurs is true only for the x forms', () => {
    expect(MULTIPLICITIES.filter(isBoundedByMaxOccurs)).toEqual(['0..x', '1..x']);
    expect(isBoundedByMaxOccurs(undefined)).toBe(false);
  });
});
