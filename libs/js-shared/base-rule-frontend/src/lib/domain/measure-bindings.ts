import {
  addMoney,
  addQuantities,
  compareMoney,
  compareQuantities,
  convertDimensions,
  convertMoney,
  convertQuantity,
  decimalText,
  dimensionsArea,
  dimensionsVolume,
  divideMoney,
  divideQuantity,
  ExchangeRates,
  isoDateOf,
  multiplyMoney,
  multiplyQuantity,
  parseMoney,
  parseQuantity,
  quantityBaseAmount,
  roundMoney,
  subtractMoney,
  subtractQuantities,
  toDecimal,
  type DimensionsValue,
  type MoneyValue,
  type QuantityValue,
  STANDARD_UNIT_CATALOG,
  type UnitCatalog,
} from '@processpuzzle/util';

/**
 * The measure helpers a rule expression can call: `qty(...)`, `money(...)` and `dims(...)`, which read a literal
 * (`qty('5 kg')`) or a stored value (`qty(entity.weight)`) as an object that converts and compares itself, so a rule never
 * does the unit arithmetic. The same surface exists in the backend's rule engine (`MeasureBindings` in base-rule-backend);
 * `rule-measure-cases.json` is run against both, so a dry run says what the backend will say — a refusal included.
 *
 * `qty(null)` is `null`, so a rule guards an absent value the way it guards any other. A refusal — different kinds,
 * different currencies, an unknown unit — throws, and the evaluator reports the rule as failed instead of answering wrongly.
 *
 * A measure refuses a bare operator (`qty('10 kg') > qty('9 kg')` would compare the text, `qty('5 kg') > qty('5 m')` would
 * ignore the kinds): `valueOf` throws, and `gt()`, `ge()`, `lt()`, `le()` and `eq()` are the way to ask. `String(q)` and a
 * template literal still print `5 kg`; `q == other` compares identity, so never use it for measures.
 */

/** The units a rule is read in: the organization's, set by {@link withCatalog} for the evaluation in progress. */
let catalog: UnitCatalog = STANDARD_UNIT_CATALOG;

/** The exchange rates `money(...).to(...)` converts at: the organization's, set by {@link withRates} for the evaluation in progress. */
let exchangeRates: ExchangeRates = ExchangeRates.NONE;

/** Runs `evaluation` with `rates` as the exchange rates conversions read; the previous ones come back afterwards. */
export function withRates<T>(rates: ExchangeRates, evaluation: () => T): T {
  const previous = exchangeRates;
  exchangeRates = rates;
  try {
    return evaluation();
  } finally {
    exchangeRates = previous;
  }
}

/**
 * Runs `evaluation` with `unitCatalog` as the catalog `qty()`, `money()` and `dims()` read. An evaluation is synchronous, so
 * one catalog at a time is enough; the previous one comes back afterwards, whatever the evaluation does.
 */
export function withCatalog<T>(unitCatalog: UnitCatalog, evaluation: () => T): T {
  const previous = catalog;
  catalog = unitCatalog;
  try {
    return evaluation();
  } finally {
    catalog = previous;
  }
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** The day a conversion is for: ISO date or date-time text, a JS date, or today. */
function rateDate(raw: unknown): string {
  if (raw === undefined || raw === null) return isoDateOf();
  if (raw instanceof Date && !Number.isNaN(raw.getTime())) return isoDateOf(raw);
  const day = typeof raw === 'string' ? raw.trim().slice(0, 10) : '';
  if (ISO_DATE.test(day)) return day;
  throw new Error("expected an ISO date, e.g. '2026-07-01'");
}

function operatorRefused(what: string, instead: string): Error {
  return new Error(`${what} cannot be used with an operator such as > or ==; use ${instead}`);
}

class QuantityRef {
  constructor(private readonly value: QuantityValue) {
    // An unknown unit is refused when the object is made, not when it is first used.
    quantityBaseAmount(value, catalog);
  }

  get amount(): string {
    return this.value.amount;
  }

  get unit(): string {
    return this.value.unit;
  }

  /** The amount in the kind's base unit, as a number. */
  get base(): number {
    return Number(quantityBaseAmount(this.value, catalog));
  }

  to(unit: unknown): QuantityRef {
    return new QuantityRef(convertQuantity(this.value, text('to', unit, 'a unit code, e.g. \'kg\''), catalog));
  }

  plus(other: unknown): QuantityRef {
    return new QuantityRef(addQuantities(this.value, quantityArg(other), catalog));
  }

  minus(other: unknown): QuantityRef {
    return new QuantityRef(subtractQuantities(this.value, quantityArg(other), catalog));
  }

  times(multiplier: unknown): QuantityRef {
    return new QuantityRef(multiplyQuantity(this.value, decimalArg(multiplier), catalog));
  }

  dividedBy(divisor: unknown): QuantityRef {
    return new QuantityRef(divideQuantity(this.value, decimalArg(divisor), catalog));
  }

  eq(other: unknown): boolean {
    return compareQuantities(this.value, quantityArg(other), catalog) === 0;
  }

  gt(other: unknown): boolean {
    return compareQuantities(this.value, quantityArg(other), catalog) > 0;
  }

  ge(other: unknown): boolean {
    return compareQuantities(this.value, quantityArg(other), catalog) >= 0;
  }

  lt(other: unknown): boolean {
    return compareQuantities(this.value, quantityArg(other), catalog) < 0;
  }

  le(other: unknown): boolean {
    return compareQuantities(this.value, quantityArg(other), catalog) <= 0;
  }

  toString(): string {
    return `${this.value.amount} ${this.value.unit}`;
  }

  valueOf(): never {
    throw operatorRefused('a quantity', "gt(), ge(), lt(), le() or eq(), e.g. qty(entity.weight).gt(qty('5 kg'))");
  }

  get raw(): QuantityValue {
    return this.value;
  }
}

class MoneyRef {
  constructor(private readonly value: MoneyValue) {}

  get amount(): string {
    return this.value.amount;
  }

  get currency(): string {
    return this.value.currency;
  }

  plus(other: unknown): MoneyRef {
    return new MoneyRef(addMoney(this.value, moneyArg(other)));
  }

  minus(other: unknown): MoneyRef {
    return new MoneyRef(subtractMoney(this.value, moneyArg(other)));
  }

  times(multiplier: unknown): MoneyRef {
    return new MoneyRef(multiplyMoney(this.value, decimalArg(multiplier)));
  }

  dividedBy(divisor: unknown): MoneyRef {
    return new MoneyRef(divideMoney(this.value, decimalArg(divisor)));
  }

  /**
   * The money in another currency at the rate valid on a date — an ISO date or date-time text, or a JS date; today's without
   * one — rounded to the target's minor units. A pair with no rate on that date is refused, never guessed.
   */
  to(currency: unknown, date?: unknown): MoneyRef {
    return new MoneyRef(convertMoney(this.value, text('to', currency, "a currency code, e.g. 'USD'"), exchangeRates, rateDate(date), catalog));
  }

  /** Rounded half up to the currency's minor units. */
  round(): MoneyRef {
    return new MoneyRef(roundMoney(this.value, catalog));
  }

  eq(other: unknown): boolean {
    return compareMoney(this.value, moneyArg(other)) === 0;
  }

  gt(other: unknown): boolean {
    return compareMoney(this.value, moneyArg(other)) > 0;
  }

  ge(other: unknown): boolean {
    return compareMoney(this.value, moneyArg(other)) >= 0;
  }

  lt(other: unknown): boolean {
    return compareMoney(this.value, moneyArg(other)) < 0;
  }

  le(other: unknown): boolean {
    return compareMoney(this.value, moneyArg(other)) <= 0;
  }

  toString(): string {
    return `${this.value.amount} ${this.value.currency}`;
  }

  valueOf(): never {
    throw operatorRefused('money', "gt(), ge(), lt(), le() or eq(), e.g. money(entity.price).le(money('100 EUR'))");
  }

  get raw(): MoneyValue {
    return this.value;
  }
}

class DimensionsRef {
  constructor(private readonly value: DimensionsValue) {}

  get width(): string {
    return this.value.width;
  }

  get height(): string {
    return this.value.height;
  }

  /** `null` for two axes. */
  get depth(): string | null {
    return this.value.depth ?? null;
  }

  get unit(): string {
    return this.value.unit;
  }

  to(unit: unknown): DimensionsRef {
    return new DimensionsRef(convertDimensions(this.value, text('to', unit, 'a unit code, e.g. \'kg\''), catalog));
  }

  /** `width × height`, in square metres. */
  area(): QuantityRef {
    return new QuantityRef(dimensionsArea(this.value, catalog));
  }

  /** `width × height × depth`, in cubic metres; a refusal for two axes. */
  volume(): QuantityRef {
    return new QuantityRef(dimensionsVolume(this.value, catalog));
  }

  toString(): string {
    const axes = [this.value.width, this.value.height, this.value.depth].filter((axis) => axis !== undefined);
    return `${axes.join(' x ')} ${this.value.unit}`;
  }

  valueOf(): never {
    throw operatorRefused('dimensions', 'area() or volume(), then gt(), lt() ...');
  }
}

/** Whether `value` is one of the objects `qty`, `money` and `dims` make. */
export function isMeasureRef(value: unknown): boolean {
  return value instanceof QuantityRef || value instanceof MoneyRef || value instanceof DimensionsRef;
}

// region reading what a rule hands in

function isObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function notAMeasure(what: string): Error {
  return new Error(`not a ${what}: pass text such as qty('5 kg'), or a stored value`);
}

/** An amount of a stored value or an object literal: decimal text, or a finite number. */
function amountOf(raw: unknown, field: string): string {
  if (typeof raw === 'string' || typeof raw === 'number') return decimalText(toDecimal(raw));
  throw new Error(`${field} must be a decimal number`);
}

function stringOf(raw: unknown, field: string): string {
  if (typeof raw === 'string' && raw.trim()) return raw;
  throw new Error(`${field} is missing`);
}

function quantityArg(other: unknown): QuantityValue {
  if (other instanceof QuantityRef) return other.raw;
  if (typeof other === 'string') return parseQuantity(other, catalog);
  throw new Error("expected a quantity, e.g. qty('5 kg')");
}

function moneyArg(other: unknown): MoneyValue {
  if (other instanceof MoneyRef) return other.raw;
  if (typeof other === 'string') return parseMoney(other, catalog);
  throw new Error("expected money, e.g. money('100 EUR')");
}

/** A plain number or its decimal text. */
function decimalArg(raw: unknown): string {
  if (typeof raw === 'string' || (typeof raw === 'number' && Number.isFinite(raw))) return decimalText(toDecimal(raw));
  throw new Error('expected a number');
}

function text(what: string, raw: unknown, expected: string): string {
  if (typeof raw === 'string') return raw;
  throw new Error(`${what} expects ${expected}`);
}

// endregion

/** Reads a literal (`'5 kg'`), a stored value or an object literal as a quantity; `null` for an absent value. */
export function qty(value: unknown): QuantityRef | null {
  if (value === null || value === undefined) return null;
  if (value instanceof QuantityRef) return value;
  if (typeof value === 'string') return new QuantityRef(parseQuantity(value, catalog));
  if (!isObject(value)) throw notAMeasure('quantity');
  return new QuantityRef({ amount: amountOf(value['amount'], 'amount'), unit: stringOf(value['unit'], 'unit') });
}

/** Reads a literal (`'100 EUR'`), a stored value or an object literal as money; `null` for an absent value. */
export function money(value: unknown): MoneyRef | null {
  if (value === null || value === undefined) return null;
  if (value instanceof MoneyRef) return value;
  if (typeof value === 'string') return new MoneyRef(parseMoney(value, catalog));
  if (!isObject(value)) throw notAMeasure('money');
  return new MoneyRef({ amount: amountOf(value['amount'], 'amount'), currency: stringOf(value['currency'], 'currency') });
}

/** Reads a stored value or an object literal as dimensions; `null` for an absent value. */
export function dims(value: unknown): DimensionsRef | null {
  if (value === null || value === undefined) return null;
  if (value instanceof DimensionsRef) return value;
  if (!isObject(value)) throw notAMeasure('dimensions');
  const dimensions: DimensionsValue = { width: amountOf(value['width'], 'width'), height: amountOf(value['height'], 'height'), unit: stringOf(value['unit'], 'unit') };
  if (value['depth'] !== null && value['depth'] !== undefined) dimensions.depth = amountOf(value['depth'], 'depth');
  return new DimensionsRef(dimensions);
}
