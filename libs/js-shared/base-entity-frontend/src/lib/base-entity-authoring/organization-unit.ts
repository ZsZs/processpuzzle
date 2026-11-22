import type { BaseEntity } from '../base-entity/base-entity';

export type OrganizationUnitStatus = 'ACTIVE' | 'RETIRED';
export const ORGANIZATION_UNIT_STATUSES: readonly OrganizationUnitStatus[] = ['ACTIVE', 'RETIRED'];

/**
 * A unit an organization adds to a quantity kind of the standard catalog — the contract's `OrganizationUnit`. A pallet of
 * 500 kg, a bag of 25 kg.
 *
 * Its `kind`, `factor` and `divisor` (and its code) are fixed once it exists, because stored amounts and their normalized
 * `baseAmount` are derived from them; the form disables them after the first save. It is retired, never deleted.
 */
export class OrganizationUnitRecord implements BaseEntity {
  /** Mirror of {@link code}; maintained by the mapper and never edited — the unit is addressed by its code. */
  id?: string;
  code: string;
  symbol: string;
  name: string;
  /** The quantity kind it measures, e.g. `MASS`. */
  kind: string;
  /** Decimal text; `factor / divisor` is the unit's size in the kind's base unit. */
  factor: string;
  divisor: string;
  status: OrganizationUnitStatus;
  /** Optimistic lock. */
  version?: number;

  constructor(init: Partial<OrganizationUnitRecord> = {}) {
    this.id = init.id;
    this.code = init.code ?? '';
    this.symbol = init.symbol ?? '';
    this.name = init.name ?? '';
    this.kind = init.kind ?? '';
    this.factor = init.factor ?? '';
    this.divisor = init.divisor ?? '1';
    this.status = init.status ?? 'ACTIVE';
    this.version = init.version;
  }
}
