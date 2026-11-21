import { decimalText, toDecimal } from './decimal';

/**
 * The sum of money or quantities that share one currency or unit, in that currency or unit; `undefined` when there is
 * nothing to add or the values differ — a total across units or currencies would be quietly wrong.
 */
export function sumMeasures(values: readonly unknown[]): { amount: string; currency: string } | { amount: string; unit: string } | undefined {
  const measures = values.map(asRecord).filter((measure) => measure !== undefined);
  if (measures.length === 0) return undefined;
  const first = measures[0];
  const firstCode = codeOf(first);
  if (firstCode === undefined || measures.some((measure) => !isText(measure['amount']) || codeOf(measure) !== firstCode)) return undefined;
  const amount = decimalText(measures.reduce((sum, measure) => sum.plus(toDecimal(measure['amount'] as string)), toDecimal('0')));
  return isText(first['currency']) ? { amount, currency: firstCode } : { amount, unit: firstCode };
}

/** The currency of money, or the unit of a quantity. */
function codeOf(measure: Record<string, unknown>): string | undefined {
  if (isText(measure['currency'])) return measure['currency'];
  return isText(measure['unit']) ? measure['unit'] : undefined;
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

function isText(value: unknown): value is string {
  return typeof value === 'string' && value !== '';
}
