import { describe, expect, it } from 'vitest';
import { quantityBaseAmount } from './quantity';
import { STANDARD_UNIT_CATALOG } from './unit-catalog';

const pallet = { kind: 'MASS', code: 'pallet', symbol: 'plt', name: 'Pallet', factor: '500' };

describe('unit catalog with an organization’s units', () => {
  it('is the same catalog when there is nothing to add', () => {
    expect(STANDARD_UNIT_CATALOG.withUnits([])).toBe(STANDARD_UNIT_CATALOG);
  });

  it('adds a unit to its kind, converting like any other, and leaves the standard catalog alone', () => {
    const acme = STANDARD_UNIT_CATALOG.withUnits([pallet]);

    expect(acme.unit('pallet')).toMatchObject({ kind: 'MASS', divisor: '1', offset: '0', retired: false });
    expect(acme.unitsOf('MASS').map((unit) => unit.code)).toContain('pallet');
    expect(quantityBaseAmount({ amount: '2', unit: 'pallet' }, acme)).toBe('1000');
    expect(STANDARD_UNIT_CATALOG.findUnit('pallet')).toBeUndefined();
  });

  it('keeps a retired unit readable but does not offer it for new values', () => {
    const acme = STANDARD_UNIT_CATALOG.withUnits([{ ...pallet, retired: true }]);

    expect(acme.findUnit('pallet')?.retired).toBe(true);
    expect(acme.activeUnitsOf('MASS').map((unit) => unit.code)).not.toContain('pallet');
    expect(acme.activeUnitsOf('MASS').map((unit) => unit.code)).toContain('kg');
  });

  it('refuses an unknown kind and a code that is already taken', () => {
    expect(() => STANDARD_UNIT_CATALOG.withUnits([{ ...pallet, kind: 'NO_SUCH_KIND' }])).toThrow(/unknown kind/);
    expect(() => STANDARD_UNIT_CATALOG.withUnits([{ ...pallet, code: 'kg' }])).toThrow(/declared twice/);
    expect(() => STANDARD_UNIT_CATALOG.withUnits([pallet, pallet])).toThrow(/declared twice/);
  });
});
