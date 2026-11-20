import type Big from 'big.js';
import { Decimal, DecimalInput, decimalText, toDecimal } from './decimal';
import { FundamentalTypeError } from './fundamental-type.error';
import { splitAmountAndCode } from './quantity';
import { STANDARD_UNIT_CATALOG, UnitCatalog } from './unit-catalog';

/** A MONEY attribute's stored value. Money of different currencies is never combined or compared — there are no exchange rates yet. */
export interface MoneyValue {
  amount: string;
  /** ISO 4217 code. */
  currency: string;
}

export function compareMoney(a: MoneyValue, b: MoneyValue): -1 | 0 | 1 {
  requireSameCurrency(a, b);
  return toDecimal(a.amount).cmp(toDecimal(b.amount));
}

export function addMoney(a: MoneyValue, b: MoneyValue): MoneyValue {
  return combine(a, b, (x, y) => x.plus(y));
}

export function subtractMoney(a: MoneyValue, b: MoneyValue): MoneyValue {
  return combine(a, b, (x, y) => x.minus(y));
}

/** The amount scaled by a plain number, unrounded — round with {@link roundMoney} when the result is final. */
export function multiplyMoney(money: MoneyValue, multiplier: DecimalInput): MoneyValue {
  return { amount: decimalText(toDecimal(money.amount).times(toDecimal(multiplier))), currency: money.currency };
}

export function divideMoney(money: MoneyValue, divisor: DecimalInput): MoneyValue {
  const by = toDecimal(divisor);
  if (by.eq(0)) throw new FundamentalTypeError('DIVISION_BY_ZERO', 'money cannot be divided by zero');
  return { amount: decimalText(toDecimal(money.amount).div(by)), currency: money.currency };
}

/** The amount rounded half up to the currency's minor units (EUR 2, JPY 0, KWD 3). */
export function roundMoney(money: MoneyValue, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): MoneyValue {
  const places = catalog.currency(money.currency).minorUnits;
  return { amount: decimalText(toDecimal(money.amount).round(places, Decimal.roundHalfUp)), currency: money.currency };
}

/** `"100 EUR"` — an amount, white space, an ISO 4217 code of the catalog. */
export function parseMoney(text: string, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): MoneyValue {
  const [amount, currency] = splitAmountAndCode(text);
  return { amount: decimalText(toDecimal(amount)), currency: catalog.currency(currency).code };
}

function combine(a: MoneyValue, b: MoneyValue, operation: (x: Big, y: Big) => Big): MoneyValue {
  requireSameCurrency(a, b);
  return { amount: decimalText(operation(toDecimal(a.amount), toDecimal(b.amount))), currency: a.currency };
}

function requireSameCurrency(a: MoneyValue, b: MoneyValue): void {
  if (a.currency !== b.currency) throw new FundamentalTypeError('CURRENCY_MISMATCH', `${a.currency} and ${b.currency} cannot be combined without an exchange rate`);
}
