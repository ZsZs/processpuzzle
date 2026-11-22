import type Big from 'big.js';
import { Decimal, decimalText, toDecimal } from './decimal';
import { FundamentalTypeError } from './fundamental-type.error';
import type { MoneyValue } from './money';
import { STANDARD_UNIT_CATALOG, UnitCatalog } from './unit-catalog';

/**
 * How many units of `to` one unit of `from` is worth, from `validFrom` (an ISO date, `2026-07-01`) on — until a later rate of
 * the same pair starts. `rate` is decimal text, as on the wire.
 */
export interface ExchangeRateData {
  from: string;
  to: string;
  rate: string;
  validFrom: string;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function invalidRate(message: string): FundamentalTypeError {
  return new FundamentalTypeError('INVALID_RATE', message);
}

/**
 * The exchange rates of one organization, immutable; the same rules as the backend's `ExchangeRates`, which
 * `parity-cases.json` runs against both. {@link rate} answers what one currency is worth in another on a date:
 *
 * 1. the **direct** rate of the pair — the one with the latest `validFrom` on or before the date, so a rate holds until the
 *    next one starts;
 * 2. else the **inverse** of the opposite pair's, to 20 places;
 * 3. else a **cross** rate through one other currency, each leg direct or inverse — the alphabetically first intermediate
 *    currency that has both, so the answer never depends on the order rates were entered in.
 *
 * Nothing else: a pair none of these reaches is a `NO_EXCHANGE_RATE`, never a guess.
 */
export class ExchangeRates {
  static readonly NONE = new ExchangeRates([]);

  /** Per `from>to` pair, the rates by `validFrom`, oldest first. */
  private readonly history = new Map<string, { validFrom: string; rate: Big }[]>();

  constructor(rates: readonly ExchangeRateData[]) {
    for (const entry of rates) {
      if (!entry.from || !entry.to || entry.from === entry.to) throw invalidRate('an exchange rate is between two different currencies');
      const rate = toDecimal(entry.rate);
      if (rate.lte(0)) throw invalidRate(`the rate of ${entry.from} to ${entry.to} must be a positive decimal`);
      if (!ISO_DATE.test(entry.validFrom ?? '')) throw invalidRate(`the rate of ${entry.from} to ${entry.to} needs the date it is valid from`);
      const key = pairKey(entry.from, entry.to);
      const list = this.history.get(key) ?? [];
      if (list.some((existing) => existing.validFrom === entry.validFrom)) {
        throw invalidRate(`${entry.from} to ${entry.to} has two rates valid from ${entry.validFrom}`);
      }
      list.push({ validFrom: entry.validFrom, rate });
      list.sort((a, b) => a.validFrom.localeCompare(b.validFrom));
      this.history.set(key, list);
    }
  }

  /** Every currency some rate mentions, alphabetically — the ones a conversion can start or end in. */
  currencies(): string[] {
    const codes = new Set<string>();
    for (const key of this.history.keys()) key.split('>').forEach((code) => codes.add(code));
    return [...codes].sort((a, b) => a.localeCompare(b));
  }

  /** What one `from` is worth in `to` on `on` (an ISO date); one for the same currency. */
  rate(from: string, to: string, on: string): Big {
    if (from === to) return toDecimal('1');
    const found = this.leg(from, to, on) ?? this.cross(from, to, on);
    if (!found) throw new FundamentalTypeError('NO_EXCHANGE_RATE', `there is no exchange rate from ${from} to ${to} on ${on}`);
    return found;
  }

  /** The money in another currency at the rate of `on`, rounded half up to the currency's minor units. */
  convert(money: MoneyValue, to: string, on: string, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): MoneyValue {
    catalog.currency(money.currency);
    const minorUnits = catalog.currency(to).minorUnits;
    if (money.currency === to) return money;
    const converted = toDecimal(money.amount).times(this.rate(money.currency, to, on)).round(minorUnits, Decimal.roundHalfUp);
    return { amount: decimalText(converted), currency: to };
  }

  private leg(from: string, to: string, on: string): Big | undefined {
    const direct = this.valid(from, to, on);
    if (direct) return direct;
    const opposite = this.valid(to, from, on);
    return opposite ? toDecimal('1').div(opposite) : undefined;
  }

  private valid(base: string, quote: string, on: string): Big | undefined {
    const list = this.history.get(pairKey(base, quote)) ?? [];
    let found: Big | undefined;
    for (const entry of list) {
      if (entry.validFrom <= on) found = entry.rate;
    }
    return found;
  }

  private cross(from: string, to: string, on: string): Big | undefined {
    const through = new Set<string>();
    for (const key of this.history.keys()) key.split('>').forEach((code) => through.add(code));
    through.delete(from);
    through.delete(to);
    for (const via of [...through].sort((a, b) => a.localeCompare(b))) {
      const first = this.leg(from, via, on);
      const second = first ? this.leg(via, to, on) : undefined;
      if (first && second) return first.times(second);
    }
    return undefined;
  }
}

/** A date as the ISO text a rate is looked up by (`2026-07-01`), in the local calendar; today without an argument. */
export function isoDateOf(date: Date = new Date()): string {
  const two = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}`;
}

function pairKey(from: string, to: string): string {
  return `${from}>${to}`;
}

/** Money in another currency at the rate valid on `on`; see {@link ExchangeRates}. */
export function convertMoney(money: MoneyValue, to: string, rates: ExchangeRates, on: string, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): MoneyValue {
  return rates.convert(money, to, on, catalog);
}
