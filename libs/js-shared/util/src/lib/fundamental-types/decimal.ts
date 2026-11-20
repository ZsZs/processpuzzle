import Big from 'big.js';
import { FundamentalTypeError } from './fundamental-type.error';

/**
 * The decimal every fundamental type computes with: its own big.js constructor, so the settings below don't touch
 * anyone else's. A division keeps {@link DIVISION_PLACES} decimal places, rounding half up — exactly what
 * `BigDecimal.divide(d, 20, HALF_UP)` does on the backend, which is what keeps the two sides' results identical.
 */
export const Decimal = Big();
export const DIVISION_PLACES = 20;
Decimal.DP = DIVISION_PLACES;
Decimal.RM = Big.roundHalfUp;

export type DecimalInput = string | number;

const PLAIN_DECIMAL = /^-?\d+(\.\d+)?$/;

/** A decimal from its stored text (`"12.50"`) or a number; anything else is an INVALID_AMOUNT. */
export function toDecimal(value: DecimalInput): Big {
  const text = typeof value === 'number' ? numberText(value) : value.trim();
  if (!PLAIN_DECIMAL.test(text)) throw new FundamentalTypeError('INVALID_AMOUNT', `'${value}' is not a decimal number`);
  return new Decimal(text);
}

/** The stored text of a decimal: plain notation, no trailing zeros, never `-0`. */
export function decimalText(value: Big): string {
  return value.eq(0) ? '0' : value.toFixed();
}

function numberText(value: number): string {
  if (!Number.isFinite(value)) throw new FundamentalTypeError('INVALID_AMOUNT', `${value} is not a decimal number`);
  return new Decimal(value).toFixed();
}
