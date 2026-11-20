import { FundamentalTypeError } from './fundamental-type.error';
import standardUnits from './standard-units.json';

/** A unit as the catalog file declares it: `base = (amount + offset) × factor / divisor`. */
export interface UnitData {
  code: string;
  symbol: string;
  name: string;
  factor: string;
  divisor?: string;
  offset?: string;
}

export interface QuantityKindData {
  code: string;
  name: string;
  baseUnit: string;
  units: UnitData[];
}

export interface CurrencyDefinition {
  /** ISO 4217 alphabetic code. */
  code: string;
  /** Decimal places of the currency's minor unit, per ISO 4217. */
  minorUnits: number;
}

export interface UnitCatalogData {
  kinds: QuantityKindData[];
  currencies: CurrencyDefinition[];
}

/** A unit with the kind it measures; `divisor` and `offset` are filled in. */
export interface UnitDefinition extends Required<UnitData> {
  kind: string;
}

export interface QuantityKindDefinition {
  code: string;
  name: string;
  baseUnit: string;
  units: readonly UnitDefinition[];
}

/**
 * The units and currencies a value may name. Unit codes are unique across kinds, so a code alone identifies the unit
 * and therefore what it measures. The standard catalog is {@link STANDARD_UNIT_CATALOG}; the backend reads the same file.
 */
export class UnitCatalog {
  private readonly kindsByCode = new Map<string, QuantityKindDefinition>();
  private readonly unitsByCode = new Map<string, UnitDefinition>();
  private readonly currenciesByCode = new Map<string, CurrencyDefinition>();

  constructor(data: UnitCatalogData) {
    data.kinds.forEach((kind) => this.addKind(kind));
    data.currencies.forEach((currency) => {
      if (this.currenciesByCode.has(currency.code)) throw invalidCatalog(`currency ${currency.code} is declared twice`);
      this.currenciesByCode.set(currency.code, { code: currency.code, minorUnits: currency.minorUnits });
    });
  }

  kinds(): QuantityKindDefinition[] {
    return [...this.kindsByCode.values()];
  }

  kind(code: string): QuantityKindDefinition {
    const kind = this.kindsByCode.get(code);
    if (!kind) throw new FundamentalTypeError('UNKNOWN_KIND', `unknown quantity kind '${code}'`);
    return kind;
  }

  findUnit(code: string): UnitDefinition | undefined {
    return this.unitsByCode.get(code);
  }

  unit(code: string): UnitDefinition {
    const unit = this.unitsByCode.get(code);
    if (!unit) throw new FundamentalTypeError('UNKNOWN_UNIT', `unknown unit '${code}'`);
    return unit;
  }

  unitsOf(kind: string): readonly UnitDefinition[] {
    return this.kind(kind).units;
  }

  currencies(): CurrencyDefinition[] {
    return [...this.currenciesByCode.values()];
  }

  findCurrency(code: string): CurrencyDefinition | undefined {
    return this.currenciesByCode.get(code);
  }

  currency(code: string): CurrencyDefinition {
    const currency = this.currenciesByCode.get(code);
    if (!currency) throw new FundamentalTypeError('UNKNOWN_CURRENCY', `unknown currency '${code}'`);
    return currency;
  }

  private addKind(data: QuantityKindData): void {
    if (this.kindsByCode.has(data.code)) throw invalidCatalog(`kind ${data.code} is declared twice`);
    const units = data.units.map((unit) => this.addUnit(data.code, unit));
    const base = units.find((unit) => unit.code === data.baseUnit);
    if (!base || base.factor !== '1' || base.divisor !== '1' || base.offset !== '0') {
      throw invalidCatalog(`kind ${data.code} must declare its base unit ${data.baseUnit} with factor 1`);
    }
    this.kindsByCode.set(data.code, { code: data.code, name: data.name, baseUnit: data.baseUnit, units });
  }

  private addUnit(kind: string, data: UnitData): UnitDefinition {
    if (this.unitsByCode.has(data.code)) throw invalidCatalog(`unit ${data.code} is declared twice`);
    const unit: UnitDefinition = { ...data, divisor: data.divisor ?? '1', offset: data.offset ?? '0', kind };
    this.unitsByCode.set(unit.code, unit);
    return unit;
  }
}

function invalidCatalog(message: string): FundamentalTypeError {
  return new FundamentalTypeError('INVALID_CATALOG', message);
}

export const STANDARD_UNIT_CATALOG = new UnitCatalog(standardUnits);
