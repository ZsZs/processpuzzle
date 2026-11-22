import { DimensionsValue, MoneyValue, QuantityValue, toDecimal } from '@processpuzzle/util';

/** An amount as stored decimal text; a finite number is accepted too, since a hand-written payload may hold one. */
function amountOf(value: unknown): string | undefined {
  if (typeof value !== 'string' && typeof value !== 'number') return undefined;
  try {
    return toDecimal(value).toFixed();
  } catch {
    return undefined;
  }
}

function recordOf(value: unknown): Record<string, unknown> | undefined {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

/** The value of a `QUANTITY` attribute — anything without a decimal amount and a unit holds none. */
export function quantityOf(value: unknown): QuantityValue | undefined {
  const candidate = recordOf(value);
  const amount = amountOf(candidate?.['amount']);
  const unit = candidate?.['unit'];
  return amount !== undefined && typeof unit === 'string' && unit ? { amount, unit } : undefined;
}

/** The value of a `MONEY` attribute — anything without a decimal amount and a currency holds none. */
export function moneyOf(value: unknown): MoneyValue | undefined {
  const candidate = recordOf(value);
  const amount = amountOf(candidate?.['amount']);
  const currency = candidate?.['currency'];
  return amount !== undefined && typeof currency === 'string' && currency ? { amount, currency } : undefined;
}

/** The value of a `DIMENSIONS` attribute — width, height and a unit are required, the depth is optional. */
export function dimensionsOf(value: unknown): DimensionsValue | undefined {
  const candidate = recordOf(value);
  const width = amountOf(candidate?.['width']);
  const height = amountOf(candidate?.['height']);
  const unit = candidate?.['unit'];
  if (width === undefined || height === undefined || typeof unit !== 'string' || !unit) return undefined;
  const depth = amountOf(candidate?.['depth']);
  return depth === undefined ? { width, height, unit } : { width, height, depth, unit };
}
