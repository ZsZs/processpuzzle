import type Big from 'big.js';
import { DIVISION_PLACES, Decimal, decimalText } from './decimal';
import { FundamentalTypeError } from './fundamental-type.error';
import { convertQuantity, quantityBase, QuantityValue } from './quantity';
import { STANDARD_UNIT_CATALOG, UnitCatalog } from './unit-catalog';

/** A DIMENSIONS attribute's stored value: two or three lengths in one unit. */
export interface DimensionsValue {
  width: string;
  height: string;
  depth?: string;
  unit: string;
}

const LENGTH = 'LENGTH';
const AREA = 'AREA';
const VOLUME = 'VOLUME';

/** `width × height`, in the AREA base unit (m²) — what the backend keeps as `baseArea`. */
export function dimensionsArea(dimensions: DimensionsValue, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): QuantityValue {
  const [width, height] = baseAxes(dimensions, catalog);
  return { amount: decimalText(product(width, height)), unit: catalog.kind(AREA).baseUnit };
}

/** `width × height × depth`, in the VOLUME base unit (m³) — what the backend keeps as `baseVolume`. A 2D value has no volume. */
export function dimensionsVolume(dimensions: DimensionsValue, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): QuantityValue {
  const [width, height, depth] = baseAxes(dimensions, catalog);
  if (!depth) throw new FundamentalTypeError('KIND_MISMATCH', 'two-dimensional dimensions have no volume');
  return { amount: decimalText(product(width, height, depth)), unit: catalog.kind(VOLUME).baseUnit };
}

/** Every axis in another length unit. */
export function convertDimensions(dimensions: DimensionsValue, toUnit: string, catalog: UnitCatalog = STANDARD_UNIT_CATALOG): DimensionsValue {
  requireLength(dimensions.unit, catalog);
  const axis = (amount: string) => convertQuantity({ amount, unit: dimensions.unit }, toUnit, catalog).amount;
  const converted: DimensionsValue = { width: axis(dimensions.width), height: axis(dimensions.height), unit: toUnit };
  if (dimensions.depth !== undefined) converted.depth = axis(dimensions.depth);
  return converted;
}

function baseAxes(dimensions: DimensionsValue, catalog: UnitCatalog): Big[] {
  requireLength(dimensions.unit, catalog);
  const axes = [dimensions.width, dimensions.height, dimensions.depth].filter((axis): axis is string => axis !== undefined);
  return axes.map((amount) => quantityBase({ amount, unit: dimensions.unit }, catalog));
}

/** Rounded like a division, so the backend's product of the same base lengths has the same digits. */
function product(...factors: Big[]): Big {
  return factors.reduce((result, factor) => result.times(factor), new Decimal(1)).round(DIVISION_PLACES, Decimal.roundHalfUp);
}

function requireLength(unit: string, catalog: UnitCatalog): void {
  const kind = catalog.unit(unit).kind;
  if (kind !== LENGTH) throw new FundamentalTypeError('KIND_MISMATCH', `dimensions are lengths, ${unit} measures ${kind}`);
}
