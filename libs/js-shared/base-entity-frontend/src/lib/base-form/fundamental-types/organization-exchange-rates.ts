import { inject, Injectable, Injector, signal } from '@angular/core';
import { ExchangeRates } from '@processpuzzle/util';
import { NGXLogger } from 'ngx-logging-kit';
import { firstValueFrom } from 'rxjs';
import { ExchangeRateRecord, ExchangeRateService } from './exchange-rate.service';

/**
 * The exchange rates of the organization the application runs for. No rates until they are loaded, so money converts to
 * nothing but itself.
 *
 * Read where the organization's metadata is, as {@link OrganizationUnitCatalog} is: the entity definition registry calls
 * {@link ensureLoaded} with the definitions, which is after sign-in, and `invalidate` on its `reset`. {@link rates} follows every
 * load, so a rule evaluated or a hint shown after one reads the new table.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationExchangeRates {
  private readonly service = inject(ExchangeRateService);
  private readonly injector = inject(Injector);
  private readonly current = signal<ExchangeRates>(ExchangeRates.NONE);
  private loading?: Promise<void>;

  /** The table as last loaded. */
  readonly rates = this.current.asReadonly();

  /** Loads the organization's rates once; a second call awaits the first. */
  ensureLoaded(): Promise<void> {
    this.loading ??= this.load();
    return this.loading;
  }

  /** Forgets what was loaded, so the next {@link ensureLoaded} reads the rates again. */
  invalidate(): void {
    this.loading = undefined;
  }

  private async load(): Promise<void> {
    const records = await firstValueFrom(this.service.findAll());
    this.current.set(tableOf(records, this.loggerOrNull()));
  }

  /**
   * The logger, looked up now and not injected at construction: this store can be created while the logger itself is being
   * built. When it is still being built, the warnings for unusable rates are left out, which is better than failing the load.
   */
  private loggerOrNull(): NGXLogger | null {
    try {
      return this.injector.get(NGXLogger, null);
    } catch {
      return null;
    }
  }
}

/** The table of `records`, built one rate at a time so one the table refuses is left out and the others stay. */
export function tableOf(records: readonly ExchangeRateRecord[], logger?: Pick<NGXLogger, 'warn'> | null): ExchangeRates {
  const accepted: ExchangeRateRecord[] = [];
  for (const record of records) {
    try {
      new ExchangeRates([...accepted, record]);
      accepted.push(record);
    } catch (error) {
      logger?.warn(`Exchange rate ${record.from} to ${record.to} is not usable`, { error });
    }
  }
  return accepted.length === 0 ? ExchangeRates.NONE : new ExchangeRates(accepted);
}
