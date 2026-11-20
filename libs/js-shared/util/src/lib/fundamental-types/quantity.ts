import type Big from 'big.js';
import { Decimal, DecimalInput, decimalText, toDecimal } from './decimal';
import { FundamentalTypeError } from './fundamental-type.error';
import { STANDARD_UNIT_CATALOG, UnitCatalog, UnitDefinition } from './unit-catalog';

/** A QUANTITY attribute's stored value. `amount` is decimal text, so no JavaScript number ever rounds it. */
export interface QuantityValue {
  amount: string;
  unit: string;
}

/** The value in its kind's base unit: `(amount + offset) × factor / divisor`. */
export function quantityBase(quantity: QuantityValue, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): Big {
  const unit = catalog.unit(quantity.unit);
  return toDecimal(quantity.amount).plus(unit.offset).times(unit.factor).div(unit.divisor);
}

/** {@link quantityBase} as stored text — what the backend keeps as `baseAmount`. */
export function quantityBaseAmount(quantity: QuantityValue, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): string {
  return decimalText(quantityBase(quantity, catalog));
}

/**
 * The same measurement in another unit of its kind. One division, so the result is the one the backend computes:
 * `((amount + offsetFrom) × factorFrom × divisorTo) / (divisorFrom × factorTo) − offsetTo`.
 */
export function convertQuantity(quantity: QuantityValue, toUnit: string, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): QuantityValue {
  const from = catalog.unit(quantity.unit);
  const to = catalog.unit(toUnit);
  requireSameKind(from, to);
  const amount = toDecimal(quantity.amount);
  if (from.code === to.code) return { amount: decimalText(amount), unit: to.code };
  const numerator = amount.plus(from.offset).times(from.factor).times(to.divisor);
  const denominator = new Decimal(from.divisor).times(to.factor);
  return { amount: decimalText(numerator.div(denominator).minus(to.offset)), unit: to.code };
}

/** -1, 0 or 1 as `a` measures less than, as much as or more than `b`; both must be of one kind. */
export function compareQuantities(a: QuantityValue, b: QuantityValue, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): -1 | 0 | 1 {
  requireSameKind(catalog.unit(a.unit), catalog.unit(b.unit));
  return quantityBase(a, catalog).cmp(quantityBase(b, catalog));
}

/** `a + b`, in `a`'s unit. Temperatures on an offset scale (°C, °F) compare and convert but don't add. */
export function addQuantities(a: QuantityValue, b: QuantityValue, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): QuantityValue {
  return combine(a, b, catalog, (x, y) => x.plus(y));
}

/** `a − b`, in `a`'s unit. */
export function subtractQuantities(a: QuantityValue, b: QuantityValue, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): QuantityValue {
  return combine(a, b, catalog, (x, y) => x.minus(y));
}

/** The quantity scaled by a plain number, in its own unit. */
export function multiplyQuantity(quantity: QuantityValue, multiplier: DecimalInput, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): QuantityValue {
  requireNoOffset(catalog.unit(quantity.unit));
  return { amount: decimalText(toDecimal(quantity.amount).times(toDecimal(multiplier))), unit: quantity.unit };
}

/** The quantity divided by a plain number, in its own unit. */
export function divideQuantity(quantity: QuantityValue, divisor: DecimalInput, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): QuantityValue {
  requireNoOffset(catalog.unit(quantity.unit));
  const by = toDecimal(divisor);
  if (by.eq(0)) throw new FundamentalTypeError('DIVISION_BY_ZERO', 'a quantity cannot be divided by zero');
  return { amount: decimalText(toDecimal(quantity.amount).div(by)), unit: quantity.unit };
}

/** The amount rounded half up to `scale` decimal places. */
export function roundQuantity(quantity: QuantityValue, scale: number): QuantityValue {
  return { amount: decimalText(toDecimal(quantity.amount).round(scale, Decimal.roundHalfUp)), unit: quantity.unit };
}

/** `"5 kg"` or `"-1.5 km/h"` — an amount, white space, a unit code of the catalog. */
export function parseQuantity(text: string, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): QuantityValue {
  const [amount, unit] = splitAmountAndCode(text);
  return { amount: decimalText(toDecimal(amount)), unit: catalog.unit(unit).code };
}

/** Splits `"<amount> <code>"`; shared with money's parser. */
export function splitAmountAndCode(text: string): [string, string] {
  const parts = text.trim().split(/\s+/);
  if (parts.length !== 2) throw new FundamentalTypeError('INVALID_AMOUNT', `'${text}' is not '<amount> <unit>'`);
  return [parts[0], parts[1]];
}

function combine(a: QuantityValue, b: QuantityValue, catalog: UnitCatalog, operation: (x: Big, y: Big) => Big): QuantityValue {
  const unitA = catalog.unit(a.unit);
  const unitB = catalog.unit(b.unit);
  requireSameKind(unitA, unitB);
  requireNoOffset(unitA);
  requireNoOffset(unitB);
  const other = toDecimal(convertQuantity(b, a.unit, catalog).amount);
  return { amount: decimalText(operation(toDecimal(a.amount), other)), unit: a.unit };
}

function requireSameKind(a: UnitDefinition, b: UnitDefinition): void {
  if (a.kind !== b.kind) throw new FundamentalTypeError('KIND_MISMATCH', `${a.code} (${a.kind}) and ${b.code} (${b.kind}) measure different things`);
}

function requireNoOffset(unit: UnitDefinition): void {
  if (unit.offset !== '0') throw new FundamentalTypeError('OFFSET_ARITHMETIC', `${unit.code} is an offset scale; convert it to its kind's base unit first`);
}
