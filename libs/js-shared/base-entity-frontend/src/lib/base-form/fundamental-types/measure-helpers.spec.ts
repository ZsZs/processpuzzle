import { FormControl } from '@angular/forms';
import { STANDARD_UNIT_CATALOG } from '@processpuzzle/util';
import { describe, expect, it } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { amountValidator, cleanAmount, decimalPlaces, isAmountText, paddedToPlaces } from './amount-text';
import { formatDimensions, formatMeasure, formatMoney, formatQuantity } from './measure-format';
import { convertAmount, currenciesOf, DEFAULT_CONVERSION_SCALE, defaultCurrencyOf, defaultUnitOf, dimensionUnitsOf, groupByKind, quantityUnitsOf } from './measure-options';
import { dimensionsOf, moneyOf, quantityOf } from './measure-values';

const catalog = STANDARD_UNIT_CATALOG;

function attr(type: FormControlType, init: Partial<BaseEntityAttrDescriptor> = {}): BaseEntityAttrDescriptor {
  return Object.assign(new BaseEntityAttrDescriptor('a', type, 'A'), init);
}

describe('amount text', () => {
  it('pads an amount to the minor units, and never shortens one', () => {
    expect(paddedToPlaces('1299.9', 2)).toBe('1299.90');
    expect(paddedToPlaces('1299', 2)).toBe('1299.00');
    expect(paddedToPlaces('1299.999', 2)).toBe('1299.999');
    expect(paddedToPlaces('1299', 0)).toBe('1299');
    expect(paddedToPlaces('1299', undefined)).toBe('1299');
  });

  it('reads what a user types: no white space, a decimal comma is a point', () => {
    expect(cleanAmount(' 1 234,5 ')).toBe('1234.5');
    expect(cleanAmount(null)).toBe('');
  });

  it('accepts plain decimals only', () => {
    expect(['0', '-1', '12.50', '0.001'].every(isAmountText)).toBe(true);
    expect(['', '-', '1.', '.5', '1e3', '1.2.3', 'abc'].some(isAmountText)).toBe(false);
    expect(decimalPlaces('12.345')).toBe(3);
    expect(decimalPlaces('12')).toBe(0);
  });

  it('validates an optional amount, and its decimal places when limited', () => {
    const limited = amountValidator(() => 2);
    expect(limited(new FormControl(''))).toBeNull();
    expect(limited(new FormControl(null))).toBeNull();
    expect(limited(new FormControl('12,34'))).toBeNull();
    expect(limited(new FormControl('12.345'))).toEqual({ scale: { max: 2 } });
    expect(limited(new FormControl('12.x'))).toEqual({ amount: true });
    expect(amountValidator(() => undefined)(new FormControl('1.123456789'))).toBeNull();
    expect(amountValidator(() => 0)(new FormControl('1.5'))).toEqual({ scale: { max: 0 } });
  });
});

describe('measure values', () => {
  it('reads a quantity, accepting a numeric amount a hand-written payload may hold', () => {
    expect(quantityOf({ amount: '12.50', unit: 'kg' })).toEqual({ amount: '12.5', unit: 'kg' });
    expect(quantityOf({ amount: 3, unit: 'm' })).toEqual({ amount: '3', unit: 'm' });
    expect([undefined, null, [], 'x', {}, { amount: 'x', unit: 'kg' }, { amount: '1' }, { amount: '1', unit: '' }, { amount: Number.NaN, unit: 'kg' }].map(quantityOf)).toEqual(Array(9).fill(undefined));
  });

  it('reads money', () => {
    expect(moneyOf({ amount: '19.90', currency: 'EUR' })).toEqual({ amount: '19.9', currency: 'EUR' });
    expect(moneyOf({ amount: '1', currency: '' })).toBeUndefined();
    expect(moneyOf({ currency: 'EUR' })).toBeUndefined();
  });

  it('reads dimensions with or without a depth', () => {
    expect(dimensionsOf({ width: '1.20', height: 2, unit: 'm' })).toEqual({ width: '1.2', height: '2', unit: 'm' });
    expect(dimensionsOf({ width: '1', height: '2', depth: '3', unit: 'm' })).toEqual({ width: '1', height: '2', depth: '3', unit: 'm' });
    expect(dimensionsOf({ width: '1', unit: 'm' })).toBeUndefined();
    expect(dimensionsOf({ width: '1', height: '2' })).toBeUndefined();
  });
});

describe('measure options', () => {
  it('offers the allowed units, else the kind, else every unit', () => {
    const codes = (units: readonly { code: string }[]) => units.map((unit) => unit.code);
    expect(codes(quantityUnitsOf(attr(FormControlType.QUANTITY, { allowedUnits: ['kg', 'g', 'parsec'] }), catalog))).toEqual(['kg', 'g']);
    expect(codes(quantityUnitsOf(attr(FormControlType.QUANTITY, { quantityKind: 'TIME' }), catalog))).toEqual(['ms', 's', 'min', 'h', 'd', 'wk']);
    expect(quantityUnitsOf(attr(FormControlType.QUANTITY, { quantityKind: 'NOPE' }), catalog).length).toBeGreaterThan(50);
    expect(quantityUnitsOf(attr(FormControlType.QUANTITY, { allowedUnits: ['parsec'] }), catalog).length).toBeGreaterThan(50);
  });

  it('offers lengths only to dimensions', () => {
    expect(dimensionUnitsOf(attr(FormControlType.DIMENSIONS), catalog).every((unit) => unit.kind === 'LENGTH')).toBe(true);
    expect(dimensionUnitsOf(attr(FormControlType.DIMENSIONS, { allowedUnits: ['cm', 'kg', 'mm'] }), catalog).map((unit) => unit.code)).toEqual(['cm', 'mm']);
    expect(dimensionUnitsOf(attr(FormControlType.DIMENSIONS, { allowedUnits: ['kg'] }), catalog).length).toBeGreaterThan(5);
  });

  it('starts in the default unit when offered, else the base unit, else the first', () => {
    const mass = quantityUnitsOf(attr(FormControlType.QUANTITY, { quantityKind: 'MASS' }), catalog);
    expect(defaultUnitOf(attr(FormControlType.QUANTITY, { defaultUnit: 'g' }), mass, catalog)).toBe('g');
    expect(defaultUnitOf(attr(FormControlType.QUANTITY, { defaultUnit: 'm' }), mass, catalog)).toBe('kg');
    const grams = quantityUnitsOf(attr(FormControlType.QUANTITY, { allowedUnits: ['g', 'mg'] }), catalog);
    expect(defaultUnitOf(attr(FormControlType.QUANTITY), grams, catalog)).toBe('g');
    expect(defaultUnitOf(attr(FormControlType.QUANTITY), [], catalog)).toBeUndefined();
  });

  it('offers the allowed currencies, else all, and starts in the default one when offered', () => {
    expect(currenciesOf(attr(FormControlType.MONEY, { allowedCurrencies: ['HUF', 'XXX', 'EUR'] }), catalog)).toEqual(['HUF', 'EUR']);
    expect(currenciesOf(attr(FormControlType.MONEY), catalog).length).toBeGreaterThan(140);
    expect(currenciesOf(attr(FormControlType.MONEY, { allowedCurrencies: ['XXX'] }), catalog).length).toBeGreaterThan(140);
    expect(defaultCurrencyOf(attr(FormControlType.MONEY, { defaultCurrency: 'EUR' }), ['HUF', 'EUR'])).toBe('EUR');
    expect(defaultCurrencyOf(attr(FormControlType.MONEY, { defaultCurrency: 'USD' }), ['HUF', 'EUR'])).toBe('HUF');
    expect(defaultCurrencyOf(attr(FormControlType.MONEY), [])).toBeUndefined();
  });

  it('groups units by kind in catalog order, leaving empty kinds out', () => {
    const groups = groupByKind(quantityUnitsOf(attr(FormControlType.QUANTITY, { allowedUnits: ['g', 'm', 'kg'] }), catalog), catalog);
    expect(groups.map((group) => [group.kind, group.units.map((unit) => unit.code)])).toEqual([
      ['LENGTH', ['m']],
      ['MASS', ['g', 'kg']],
    ]);
  });

  it('converts, rounded to the scale — six places when the attribute sets none', () => {
    expect(DEFAULT_CONVERSION_SCALE).toBe(6);
    expect(convertAmount('1', 'm', 'ft', undefined, catalog)).toBe('3.28084');
    expect(convertAmount('1', 'm', 'ft', 2, catalog)).toBe('3.28');
    expect(convertAmount('1000', 'g', 'kg', undefined, catalog)).toBe('1');
    expect(convertAmount('100', 'degC', 'degF', undefined, catalog)).toBe('212');
  });

  it('declines a conversion it cannot make, so the caller keeps the number', () => {
    expect(convertAmount('1', 'kg', 'm', undefined, catalog)).toBeUndefined();
    expect(convertAmount('x', 'kg', 'g', undefined, catalog)).toBeUndefined();
    expect(convertAmount('1', 'parsec', 'm', undefined, catalog)).toBeUndefined();
  });
});

describe('formatMeasure', () => {
  it('formats whichever measure the value is, and nothing else', () => {
    expect(formatMeasure({ amount: '1234.5', currency: 'EUR' }, 'en')).toBe('€1,234.50');
    expect(formatMeasure({ amount: '2', unit: 'dz' }, 'en')).toBe('2 dz');
    expect(formatMeasure({ width: '2', height: '3', unit: 'm' }, 'en')).toBe('2 × 3 m');
    expect(formatMeasure('text', 'en')).toBeUndefined();
    expect(formatMeasure({ street: 'Main' }, 'en')).toBeUndefined();
    expect(formatMeasure(null, 'en')).toBeUndefined();
  });
});

describe('measure formatting', () => {
  it('writes a quantity in the locale’s notation with the unit’s symbol', () => {
    expect(formatQuantity({ amount: '1234.5', unit: 'kg' }, 'en')).toBe('1,234.5 kg');
    expect(formatQuantity({ amount: '1234.5', unit: 'kg' }, 'de')).toBe('1.234,5 kg');
    expect(formatQuantity({ amount: '25', unit: 'degC' }, 'en')).toBe('25 °C');
    expect(formatQuantity({ amount: '1', unit: 'zork' }, 'en')).toBe('1 zork');
    expect(formatQuantity(null, 'en')).toBe('');
  });

  it('keeps every decimal a stored amount has', () => {
    expect(formatQuantity({ amount: '0.33333333333333333333', unit: 'm' }, 'en')).toBe('0.33333333333333333333 m');
  });

  it('writes money with the currency’s minor units, and more when the amount has more', () => {
    expect(formatMoney({ amount: '1234.5', currency: 'EUR' }, 'en')).toBe('€1,234.50');
    expect(formatMoney({ amount: '2.5', currency: 'JPY' }, 'en')).toBe('¥2.5');
    expect(formatMoney({ amount: '5.3973', currency: 'EUR' }, 'en')).toBe('€5.3973');
    expect(formatMoney({ amount: '1.5', currency: 'KWD' }, 'en')).toContain('1.500');
    expect(formatMoney({}, 'en')).toBe('');
  });

  it('falls back to the code for a currency Intl does not know', () => {
    expect(formatMoney({ amount: '12.5', currency: 'ZZZZ' }, 'en')).toBe('12.5 ZZZZ');
  });

  it('writes dimensions as lengths joined by ×', () => {
    expect(formatDimensions({ width: '1.2', height: '0.8', unit: 'm' }, 'en')).toBe('1.2 × 0.8 m');
    expect(formatDimensions({ width: '120', height: '80', depth: '50', unit: 'cm' }, 'en')).toBe('120 × 80 × 50 cm');
    expect(formatDimensions({ width: '1' }, 'en')).toBe('');
  });
});
