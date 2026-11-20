import { describe, expect, it } from 'vitest';
import { decimalText, toDecimal } from './decimal';
import { convertDimensions, dimensionsArea, dimensionsVolume } from './dimensions';
import { FundamentalTypeError, FundamentalTypeErrorReason } from './fundamental-type.error';
import { isoDurationToQuantity, quantityToIsoDuration } from './iso-duration';
import { addMoney, compareMoney, divideMoney, multiplyMoney, parseMoney, roundMoney, subtractMoney } from './money';
import parity from './parity-cases.json';
import {
  addQuantities,
  compareQuantities,
  convertQuantity,
  divideQuantity,
  multiplyQuantity,
  parseQuantity,
  quantityBaseAmount,
  roundQuantity,
  subtractQuantities,
} from './quantity';
import { STANDARD_UNIT_CATALOG, UnitCatalog, UnitCatalogData } from './unit-catalog';

function reasonOf(action: () => unknown): FundamentalTypeErrorReason | undefined {
  try {
    action();
  } catch (error) {
    if (error instanceof FundamentalTypeError) return error.reason;
    throw error;
  }
  return undefined;
}

describe('fundamental types — parity cases shared with the backend', () => {
  it.each(parity.conversions)('converts $amount $from to $to', ({ amount, from, to, expected }) => {
    expect(convertQuantity({ amount, unit: from }, to)).toEqual({ amount: expected, unit: to });
  });

  it.each(parity.baseAmounts)('normalizes $amount $unit', ({ amount, unit, expected }) => {
    expect(quantityBaseAmount({ amount, unit })).toBe(expected);
  });

  it.each(parity.dimensions)('measures $width × $height × $depth $unit', ({ area, volume, ...dimensions }) => {
    expect(dimensionsArea(dimensions)).toEqual({ amount: area, unit: 'm2' });
    if (volume) expect(dimensionsVolume(dimensions)).toEqual({ amount: volume, unit: 'm3' });
  });

  it.each(parity.moneyRounding)('rounds $amount $currency', ({ amount, currency, expected }) => {
    expect(roundMoney({ amount, currency })).toEqual({ amount: expected, currency });
  });

  it.each(parity.toIsoDuration)('writes $amount $unit as $expected', ({ amount, unit, expected }) => {
    expect(quantityToIsoDuration({ amount, unit })).toBe(expected);
  });

  it.each(parity.fromIsoDuration)('reads $duration in $unit', ({ duration, unit, expected }) => {
    expect(isoDurationToQuantity(duration, unit)).toEqual({ amount: expected, unit });
  });

  it.each(parity.invalidIsoDurations)('refuses $duration', ({ duration, reason }) => {
    expect(reasonOf(() => isoDurationToQuantity(duration))).toBe(reason);
  });
});

describe('decimal', () => {
  it('reads numbers and decimal text, refuses anything else', () => {
    expect(decimalText(toDecimal(0.1))).toBe('0.1');
    expect(decimalText(toDecimal(' 12.50 '))).toBe('12.5');
    expect(reasonOf(() => toDecimal('1e5'))).toBe('INVALID_AMOUNT');
    expect(reasonOf(() => toDecimal('12,5'))).toBe('INVALID_AMOUNT');
    expect(reasonOf(() => toDecimal(Number.NaN))).toBe('INVALID_AMOUNT');
  });

  it('writes plain notation, never an exponent', () => {
    expect(decimalText(toDecimal('0.000000000001'))).toBe('0.000000000001');
    expect(decimalText(toDecimal('1000000000000000000000000'))).toBe('1000000000000000000000000');
  });
});

describe('unit catalog', () => {
  const catalog = STANDARD_UNIT_CATALOG;

  it('knows each unit by its code and the kind it measures', () => {
    expect(catalog.unit('degC')).toMatchObject({ symbol: '°C', kind: 'TEMPERATURE', offset: '273.15', divisor: '1' });
    expect(catalog.findUnit('parsec')).toBeUndefined();
    expect(catalog.unitsOf('TIME').map((unit) => unit.code)).toEqual(['ms', 's', 'min', 'h', 'd', 'wk']);
    expect(catalog.kinds().map((kind) => kind.code)).toContain('LENGTH');
  });

  it('declares every kind with a base unit of factor 1', () => {
    for (const kind of catalog.kinds()) {
      expect(catalog.unit(kind.baseUnit)).toMatchObject({ factor: '1', divisor: '1', offset: '0', kind: kind.code });
    }
  });

  it('knows ISO 4217 currencies with their minor units', () => {
    expect(catalog.currency('JPY').minorUnits).toBe(0);
    expect(catalog.findCurrency('XXX')).toBeUndefined();
    expect(catalog.currencies().length).toBeGreaterThan(140);
  });

  it('refuses unknown codes', () => {
    expect(reasonOf(() => catalog.unit('parsec'))).toBe('UNKNOWN_UNIT');
    expect(reasonOf(() => catalog.kind('LUMINOSITY'))).toBe('UNKNOWN_KIND');
    expect(reasonOf(() => catalog.currency('XXX'))).toBe('UNKNOWN_CURRENCY');
  });

  it('refuses a catalog with duplicates or without a proper base unit', () => {
    const unit = { code: 'm', symbol: 'm', name: 'metre', factor: '1' };
    const kind = (code: string, units = [unit]) => ({ code, name: code, baseUnit: 'm', units });
    const build = (data: UnitCatalogData) => () => new UnitCatalog(data);
    expect(reasonOf(build({ kinds: [kind('A'), kind('A', [])], currencies: [] }))).toBe('INVALID_CATALOG');
    expect(reasonOf(build({ kinds: [kind('A', [unit, unit])], currencies: [] }))).toBe('INVALID_CATALOG');
    expect(reasonOf(build({ kinds: [kind('A', [{ ...unit, factor: '2' }])], currencies: [] }))).toBe('INVALID_CATALOG');
    expect(reasonOf(build({ kinds: [kind('A', [])], currencies: [] }))).toBe('INVALID_CATALOG');
    const eur = { code: 'EUR', minorUnits: 2 };
    expect(reasonOf(build({ kinds: [], currencies: [eur, eur] }))).toBe('INVALID_CATALOG');
  });
});

describe('quantity', () => {
  it('compares across units of one kind', () => {
    expect(compareQuantities({ amount: '1', unit: 'kg' }, { amount: '999', unit: 'g' })).toBe(1);
    expect(compareQuantities({ amount: '1000', unit: 'g' }, { amount: '1', unit: 'kg' })).toBe(0);
    expect(compareQuantities({ amount: '0', unit: 'degC' }, { amount: '33', unit: 'degF' })).toBe(-1);
  });

  it('refuses to relate different kinds', () => {
    expect(reasonOf(() => compareQuantities({ amount: '1', unit: 'kg' }, { amount: '1', unit: 'm' }))).toBe('KIND_MISMATCH');
    expect(reasonOf(() => convertQuantity({ amount: '1', unit: 'kg' }, 'l'))).toBe('KIND_MISMATCH');
  });

  it('adds and subtracts in the first operand’s unit', () => {
    expect(addQuantities({ amount: '1', unit: 'kg' }, { amount: '250', unit: 'g' })).toEqual({ amount: '1.25', unit: 'kg' });
    expect(subtractQuantities({ amount: '1', unit: 'h' }, { amount: '15', unit: 'min' })).toEqual({ amount: '0.75', unit: 'h' });
  });

  it('scales by plain numbers', () => {
    expect(multiplyQuantity({ amount: '2.5', unit: 'm' }, 3)).toEqual({ amount: '7.5', unit: 'm' });
    expect(divideQuantity({ amount: '1', unit: 'm' }, '3')).toEqual({ amount: '0.33333333333333333333', unit: 'm' });
    expect(reasonOf(() => divideQuantity({ amount: '1', unit: 'm' }, 0))).toBe('DIVISION_BY_ZERO');
  });

  it('refuses arithmetic on offset scales', () => {
    const celsius = { amount: '20', unit: 'degC' };
    expect(reasonOf(() => addQuantities(celsius, celsius))).toBe('OFFSET_ARITHMETIC');
    expect(reasonOf(() => addQuantities({ amount: '1', unit: 'K' }, celsius))).toBe('OFFSET_ARITHMETIC');
    expect(reasonOf(() => multiplyQuantity(celsius, 2))).toBe('OFFSET_ARITHMETIC');
    expect(reasonOf(() => divideQuantity(celsius, 2))).toBe('OFFSET_ARITHMETIC');
  });

  it('rounds half up to a scale', () => {
    expect(roundQuantity({ amount: '1.2345', unit: 'kg' }, 3)).toEqual({ amount: '1.235', unit: 'kg' });
  });

  it('parses "<amount> <unit>"', () => {
    expect(parseQuantity(' -1.50  km/h ')).toEqual({ amount: '-1.5', unit: 'km/h' });
    expect(reasonOf(() => parseQuantity('5kg'))).toBe('INVALID_AMOUNT');
    expect(reasonOf(() => parseQuantity('5 parsec'))).toBe('UNKNOWN_UNIT');
  });
});

describe('money', () => {
  const eur = (amount: string) => ({ amount, currency: 'EUR' });

  it('combines and compares one currency', () => {
    expect(addMoney(eur('10.10'), eur('0.90'))).toEqual(eur('11'));
    expect(subtractMoney(eur('10'), eur('0.01'))).toEqual(eur('9.99'));
    expect(compareMoney(eur('1'), eur('1.00'))).toBe(0);
  });

  it('never mixes currencies', () => {
    expect(reasonOf(() => addMoney(eur('1'), { amount: '1', currency: 'USD' }))).toBe('CURRENCY_MISMATCH');
    expect(reasonOf(() => compareMoney(eur('1'), { amount: '1', currency: 'USD' }))).toBe('CURRENCY_MISMATCH');
  });

  it('scales unrounded until rounded explicitly', () => {
    expect(multiplyMoney(eur('19.99'), '0.27')).toEqual(eur('5.3973'));
    expect(roundMoney(divideMoney(eur('100'), 3))).toEqual(eur('33.33'));
    expect(reasonOf(() => divideMoney(eur('1'), '0'))).toBe('DIVISION_BY_ZERO');
  });

  it('parses "<amount> <currency>"', () => {
    expect(parseMoney('100 EUR')).toEqual(eur('100'));
    expect(reasonOf(() => parseMoney('100 XXX'))).toBe('UNKNOWN_CURRENCY');
  });
});

describe('dimensions', () => {
  it('converts every axis', () => {
    expect(convertDimensions({ width: '1.2', height: '0.8', depth: '0.5', unit: 'm' }, 'cm')).toEqual({ width: '120', height: '80', depth: '50', unit: 'cm' });
    expect(convertDimensions({ width: '1', height: '2', unit: 'm' }, 'mm')).toEqual({ width: '1000', height: '2000', unit: 'mm' });
  });

  it('are lengths, and only 3D ones have a volume', () => {
    expect(reasonOf(() => dimensionsArea({ width: '1', height: '1', unit: 'kg' }))).toBe('KIND_MISMATCH');
    expect(reasonOf(() => dimensionsVolume({ width: '1', height: '1', unit: 'm' }))).toBe('KIND_MISMATCH');
  });
});

describe('ISO durations', () => {
  it('only write TIME quantities that are not negative', () => {
    expect(reasonOf(() => quantityToIsoDuration({ amount: '1', unit: 'm' }))).toBe('KIND_MISMATCH');
    expect(reasonOf(() => quantityToIsoDuration({ amount: '-1', unit: 's' }))).toBe('INVALID_DURATION');
  });

  it('never overflows a unit when the division rounds up to it', () => {
    expect(quantityToIsoDuration({ amount: '86399.999999999999999999', unit: 's' })).toBe('PT23H59M59.999999999999999999S');
  });
});
