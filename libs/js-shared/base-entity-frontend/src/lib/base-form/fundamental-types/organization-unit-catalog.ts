import { inject, Injectable, signal } from '@angular/core';
import {
  type CurrencyDefinition,
  type ExtraUnitData,
  type QuantityKindDefinition,
  STANDARD_UNIT_CATALOG,
  UnitCatalog,
  type UnitDefinition,
} from '@processpuzzle/util';
import { NGXLogger } from 'ngx-logging-kit';
import { firstValueFrom } from 'rxjs';
import { useDisplayCatalog } from './measure-format';
import { OrganizationUnit, OrganizationUnitService } from './organization-unit.service';

/**
 * The catalog of the organization the application runs for: the standard units plus the ones it added, retired ones
 * included (a retired unit is no longer offered, but values stored with it must stay readable).
 *
 * It is the standard catalog until the units are loaded. They are read where the organization's metadata is — the entity
 * definition registry calls {@link ensureLoaded} together with the definitions, which is after sign-in (an application
 * initializer would ask before there is a token) — and again after {@link invalidate}, which the registry's `reset` calls.
 *
 * {@link live} is the catalog everything reads (`UNIT_CATALOG`): a view that answers from whatever was loaded last, so a
 * control created before the units arrived, or a pipe that outlives a reload, never holds a stale catalog.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationUnitCatalog {
  private readonly service = inject(OrganizationUnitService);
  private readonly logger = inject(NGXLogger, { optional: true });
  private readonly current = signal<UnitCatalog>(STANDARD_UNIT_CATALOG);
  private loading?: Promise<void>;

  /** The catalog as last loaded. */
  readonly catalog = this.current.asReadonly();

  /** A catalog that always answers as {@link catalog} does now. */
  readonly live: UnitCatalog = new LiveUnitCatalog(() => this.current());

  /** Loads the organization's units once; a second call awaits the first. */
  ensureLoaded(): Promise<void> {
    this.loading ??= this.load();
    return this.loading;
  }

  /** Forgets what was loaded, so the next {@link ensureLoaded} reads the units again. */
  invalidate(): void {
    this.loading = undefined;
  }

  private async load(): Promise<void> {
    const units = await firstValueFrom(this.service.findAll());
    this.current.set(catalogOf(units, this.logger));
    // Places that format a value without an injector — a document field, a PDF — name its unit from the same catalog.
    useDisplayCatalog(this.live);
  }
}

/** The standard catalog with `units` added one at a time, so one the catalog refuses is left out and the rest stay. */
export function catalogOf(units: readonly OrganizationUnit[], logger?: Pick<NGXLogger, 'warn'> | null): UnitCatalog {
  let catalog = STANDARD_UNIT_CATALOG;
  for (const unit of units) {
    try {
      catalog = catalog.withUnits([extraUnitOf(unit)]);
    } catch (error) {
      logger?.warn(`Unit '${unit.code}' is not part of the organization's catalog`, { error });
    }
  }
  return catalog;
}

function extraUnitOf(unit: OrganizationUnit): ExtraUnitData {
  return { kind: unit.kind, code: unit.code, symbol: unit.symbol, name: unit.name, factor: unit.factor, divisor: unit.divisor ?? '1', retired: unit.status === 'RETIRED' };
}

/** A {@link UnitCatalog} that delegates every question to the catalog `source` returns at that moment. */
class LiveUnitCatalog extends UnitCatalog {
  constructor(private readonly source: () => UnitCatalog) {
    super({ kinds: [], currencies: [] });
  }

  override withUnits(extra: readonly ExtraUnitData[]): UnitCatalog {
    return this.source().withUnits(extra);
  }

  override kinds(): QuantityKindDefinition[] {
    return this.source().kinds();
  }

  override kind(code: string): QuantityKindDefinition {
    return this.source().kind(code);
  }

  override findUnit(code: string): UnitDefinition | undefined {
    return this.source().findUnit(code);
  }

  override unit(code: string): UnitDefinition {
    return this.source().unit(code);
  }

  override unitsOf(kind: string): readonly UnitDefinition[] {
    return this.source().unitsOf(kind);
  }

  override activeUnitsOf(kind: string): readonly UnitDefinition[] {
    return this.source().activeUnitsOf(kind);
  }

  override currencies(): CurrencyDefinition[] {
    return this.source().currencies();
  }

  override findCurrency(code: string): CurrencyDefinition | undefined {
    return this.source().findCurrency(code);
  }

  override currency(code: string): CurrencyDefinition {
    return this.source().currency(code);
  }
}
