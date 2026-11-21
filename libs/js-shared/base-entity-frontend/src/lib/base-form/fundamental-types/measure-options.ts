import { convertQuantity, decimalText, roundQuantity, toDecimal, type UnitCatalog, type UnitDefinition } from '@processpuzzle/util';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';

/** The places a unit conversion keeps when the attribute sets no `scale`. */
export const DEFAULT_CONVERSION_SCALE = 6;

const LENGTH = 'LENGTH';

/**
 * The units a `QUANTITY` attribute offers: its `allowedUnits` that the catalog knows, else every unit of its
 * `quantityKind`, else every unit of the catalog. Unknown codes are dropped rather than failing the form: a
 * definition may name a unit this catalog has not caught up to.
 */
export function quantityUnitsOf(config: BaseEntityAttrDescriptor, catalog: UnitCatalog): UnitDefinition[] {
  const allowed = knownUnits(config.allowedUnits, catalog);
  if (allowed.length > 0) return allowed;
  const kind = config.quantityKind ?? undefined;
  if (kind && catalog.kinds().some((candidate) => candidate.code === kind)) return [...catalog.unitsOf(kind)];
  return catalog.kinds().flatMap((candidate) => candidate.units);
}

/** The units a `DIMENSIONS` attribute offers: lengths only, narrowed by `allowedUnits`. */
export function dimensionUnitsOf(config: BaseEntityAttrDescriptor, catalog: UnitCatalog): UnitDefinition[] {
  const lengths = catalog.unitsOf(LENGTH);
  const allowed = knownUnits(config.allowedUnits, catalog).filter((unit) => unit.kind === LENGTH);
  return allowed.length > 0 ? allowed : [...lengths];
}

/** The unit a new value starts in: `defaultUnit` when offered, else the base unit of the offered kind, else the first. */
export function defaultUnitOf(config: BaseEntityAttrDescriptor, offered: readonly UnitDefinition[], catalog: UnitCatalog): string | undefined {
  const wanted = config.defaultUnit;
  if (wanted && offered.some((unit) => unit.code === wanted)) return wanted;
  const base = offered.find((unit) => catalog.kind(unit.kind).baseUnit === unit.code);
  return (base ?? offered[0])?.code;
}

/** The currencies a `MONEY` attribute offers: its `allowedCurrencies` that the catalog knows, else all of them. */
export function currenciesOf(config: BaseEntityAttrDescriptor, catalog: UnitCatalog): string[] {
  const allowed = (config.allowedCurrencies ?? []).filter((code) => catalog.findCurrency(code));
  return allowed.length > 0 ? allowed : catalog.currencies().map((currency) => currency.code);
}

export function defaultCurrencyOf(config: BaseEntityAttrDescriptor, offered: readonly string[]): string | undefined {
  const wanted = config.defaultCurrency;
  return wanted && offered.includes(wanted) ? wanted : offered[0];
}

/** Groups units by the kind they measure, in catalog order. */
export function groupByKind(units: readonly UnitDefinition[], catalog: UnitCatalog): { kind: string; name: string; units: UnitDefinition[] }[] {
  return catalog
    .kinds()
    .map((kind) => ({ kind: kind.code, name: kind.name, units: units.filter((unit) => unit.kind === kind.code) }))
    .filter((group) => group.units.length > 0);
}

/**
 * The same measurement in another unit, rounded half up to `scale` places (six by default) so a conversion reads
 * `3.28084 ft`, not twenty digits. `undefined` when the amount is not a decimal or the units measure different
 * things — the caller then keeps the number as typed.
 */
export function convertAmount(amount: string, fromUnit: string, toUnit: string, scale: number | undefined, catalog: UnitCatalog): string | undefined {
  try {
    toDecimal(amount);
    const converted = convertQuantity({ amount, unit: fromUnit }, toUnit, catalog);
    return decimalText(toDecimal(roundQuantity(converted, scale ?? DEFAULT_CONVERSION_SCALE).amount));
  } catch {
    return undefined;
  }
}

function knownUnits(codes: readonly string[] | undefined, catalog: UnitCatalog): UnitDefinition[] {
  return (codes ?? []).map((code) => catalog.findUnit(code)).filter((unit): unit is UnitDefinition => unit !== undefined);
}
