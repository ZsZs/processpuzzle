import type Big from 'big.js';
import { Decimal, decimalText } from './decimal';
import { FundamentalTypeError } from './fundamental-type.error';
import { convertQuantity, QuantityValue } from './quantity';
import { STANDARD_UNIT_CATALOG, UnitCatalog } from './unit-catalog';

const SECONDS_OF: Readonly<Record<string, number>> = { W: 604_800, D: 86_400, H: 3_600, M: 60, S: 1 };
const DATE_DESIGNATORS = ['Y', 'M', 'W', 'D'];
const TIME_DESIGNATORS = ['H', 'M', 'S'];

/**
 * A TIME quantity as the ISO 8601 duration workflow timers use: `1.5 h` is `PT1H30M`. Days are written as days,
 * never weeks, and the result is always one a timer accepts.
 */
export function quantityToIsoDuration(quantity: QuantityValue, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): string {
  const seconds = new Decimal(convertQuantity(quantity, 's', catalog).amount);
  if (seconds.lt(0)) throw new FundamentalTypeError('INVALID_DURATION', 'a duration cannot be negative');
  let rest = seconds;
  const [days, hours, minutes] = ['D', 'H', 'M'].map((designator) => {
    let whole = rest.div(SECONDS_OF[designator]).round(0, Decimal.roundDown);
    // The division keeps 20 places, so a hair below a whole unit can round up to it.
    if (whole.times(SECONDS_OF[designator]).gt(rest)) whole = whole.minus(1);
    rest = rest.minus(whole.times(SECONDS_OF[designator]));
    return whole;
  });
  const date = part(days, 'D');
  const time = [part(hours, 'H'), part(minutes, 'M'), part(rest, 'S')].join('');
  if (!date && !time) return 'PT0S';
  return `P${date}${time ? 'T' + time : ''}`;
}

/**
 * An ISO 8601 duration as a TIME quantity in `unit`. Years and months are refused: they have no fixed length, so
 * no quantity measures them — such a duration stays a timer expression.
 */
export function isoDurationToQuantity(duration: string, unit = 's', catalog: UnitCatalog = STANDARD_UNIT_CATALOG): QuantityValue {
  const text = duration.trim().toUpperCase();
  if (!text.startsWith('P')) throw invalid(duration);
  const [datePart, timePart, ...more] = text.slice(1).split('T');
  if (more.length > 0 || timePart === '') throw invalid(duration);
  const parts = [...designators(datePart, DATE_DESIGNATORS, duration), ...designators(timePart ?? '', TIME_DESIGNATORS, duration)];
  if (parts.length === 0) throw invalid(duration);
  if (parts.some(([designator, , inDate]) => inDate && (designator === 'Y' || designator === 'M'))) {
    throw new FundamentalTypeError('CALENDAR_DURATION', `${duration} names years or months, which have no fixed length`);
  }
  const seconds = parts.reduce((total, [designator, amount]) => total.plus(amount.times(SECONDS_OF[designator])), new Decimal(0));
  return convertQuantity({ amount: decimalText(seconds), unit: 's' }, unit, catalog);
}

type Designator = [designator: string, amount: Big, inDate: boolean];

/** The `<number><designator>` pairs of one part, each designator at most once and in ISO order. */
function designators(part: string, order: string[], duration: string): Designator[] {
  const inDate = order === DATE_DESIGNATORS;
  const token = /(\d+(?:\.\d+)?)([A-Z])/y;
  const found: Designator[] = [];
  let position = 0;
  let last = -1;
  while (position < part.length) {
    token.lastIndex = position;
    const match = token.exec(part);
    const rank = match ? order.indexOf(match[2]) : -1;
    if (!match || rank <= last) throw invalid(duration);
    found.push([match[2], new Decimal(match[1]), inDate]);
    last = rank;
    position = token.lastIndex;
  }
  return found;
}

function part(amount: Big, designator: string): string {
  return amount.gt(0) ? `${decimalText(amount)}${designator}` : '';
}

function invalid(duration: string): FundamentalTypeError {
  return new FundamentalTypeError('INVALID_DURATION', `'${duration}' is not an ISO 8601 duration`);
}
