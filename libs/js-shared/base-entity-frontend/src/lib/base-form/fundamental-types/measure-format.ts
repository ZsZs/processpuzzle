import { STANDARD_UNIT_CATALOG, UnitCatalog } from '@processpuzzle/util';
import { decimalPlaces } from './amount-text';
import { dimensionsOf, moneyOf, quantityOf } from './measure-values';

/** Longest fraction `Intl.NumberFormat` is asked for; a stored amount has at most the backend's 20 division places. */
const MAX_FRACTION_DIGITS = 20;

/**
 * The decimal text handed to `Intl.NumberFormat.format` as it is. Since ES2023 (Chrome 106, Firefox 116, Safari 15.4)
 * `format` reads a string as an exact decimal, so a stored amount is shown digit for digit instead of through a double —
 * but this workspace compiles against the es2022 typings, which only declare `number | bigint`.
 */
function exactly(amount: string): number {
  return amount as unknown as number;
}

/** Decimal text as a number in the locale's notation, exact — `Intl.NumberFormat` reads the string, not a double. */
function formatDecimal(amount: string, locale: string | undefined): string {
  return new Intl.NumberFormat(locale, { maximumFractionDigits: Math.min(decimalPlaces(amount), MAX_FRACTION_DIGITS) }).format(exactly(amount));
}

/**
 * A measure of any of the three kinds as the text its own formatter gives it, or `undefined` when the value is none — which
 * is how a place that shows whatever an attribute holds (a table cell, a document field, a PDF) tells a measure from
 * another object. A calculated attribute is one: its control type no longer says what it holds.
 */
export function formatMeasure(value: unknown, locale: string | undefined, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): string | undefined {
  if (moneyOf(value)) return formatMoney(value, locale, catalog);
  if (quantityOf(value)) return formatQuantity(value, locale, catalog);
  if (dimensionsOf(value)) return formatDimensions(value, locale, catalog);
  return undefined;
}

/** A `QUANTITY` value as `12.5 kg`; an empty string when the value holds none. */
export function formatQuantity(value: unknown, locale: string | undefined, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): string {
  const quantity = quantityOf(value);
  if (!quantity) return '';
  return `${formatDecimal(quantity.amount, locale)} ${catalog.findUnit(quantity.unit)?.symbol ?? quantity.unit}`;
}

/**
 * A `MONEY` value in the locale's currency notation. The currency's minor units are always shown, and more when
 * the amount has more — an unrounded intermediate result is never displayed as something it is not.
 */
export function formatMoney(value: unknown, locale: string | undefined, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): string {
  const money = moneyOf(value);
  if (!money) return '';
  const minor = catalog.findCurrency(money.currency)?.minorUnits ?? 2;
  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: money.currency,
      minimumFractionDigits: minor,
      maximumFractionDigits: Math.min(Math.max(minor, decimalPlaces(money.amount)), MAX_FRACTION_DIGITS),
    }).format(exactly(money.amount));
  } catch {
    return `${formatDecimal(money.amount, locale)} ${money.currency}`;
  }
}

/** A `DIMENSIONS` value as `1.2 × 0.8 × 0.5 m`; an empty string when the value holds none. */
export function formatDimensions(value: unknown, locale: string | undefined, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): string {
  const dimensions = dimensionsOf(value);
  if (!dimensions) return '';
  const axes = [dimensions.width, dimensions.height, dimensions.depth].filter((axis): axis is string => axis !== undefined);
  return `${axes.map((axis) => formatDecimal(axis, locale)).join(' × ')} ${catalog.findUnit(dimensions.unit)?.symbol ?? dimensions.unit}`;
}
