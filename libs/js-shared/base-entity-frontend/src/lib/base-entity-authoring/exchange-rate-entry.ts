import type { BaseEntity } from '../base-entity/base-entity';

/**
 * An exchange rate an organization enters — the contract's `ExchangeRate`: what one unit of `from` is worth in `to`, from
 * `validFrom` until the pair's next rate starts. A pair needs entering once; the inverse and cross rates are derived.
 */
export class ExchangeRateEntry implements BaseEntity {
  /** The rate's own id, assigned by the backend — a rate has no business key, a pair can have many dates. */
  id?: string;
  /** ISO 4217 code of the currency converted from. */
  from: string;
  /** ISO 4217 code of the currency converted to; another than {@link from}. */
  to: string;
  /** Decimal text, positive. */
  rate: string;
  /** An ISO date, `2026-07-01`: the first day the rate holds. */
  validFrom: string;
  /** Optimistic lock. */
  version?: number;

  constructor(init: Partial<ExchangeRateEntry> = {}) {
    this.id = init.id;
    this.from = init.from ?? '';
    this.to = init.to ?? '';
    this.rate = init.rate ?? '';
    this.validFrom = init.validFrom ?? '';
    this.version = init.version;
  }
}
