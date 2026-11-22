import { inject, Injectable } from '@angular/core';
import type { Observable } from 'rxjs';
import { tap } from 'rxjs';
import type { PersistedEntity } from '../base-entity/base-entity';
import { ENTITY_SERVICE_ROOT_KEY } from '../base-entity-definition/entity-definition.service';
import { BaseEntityRestService } from '../base-entity-service/base-entity-rest.service';
import { OrganizationExchangeRates } from '../base-form/fundamental-types/organization-exchange-rates';
import type { ExchangeRateEntry } from './exchange-rate-entry';
import { ExchangeRateEntryMapper } from './exchange-rate-entry.mapper';

/**
 * CRUD access to `/organizations/{orgKey}/exchange-rates` — the rates an organization enters, as the designer writes them. A
 * second service over the resource {@link ExchangeRateService} reads, for the reason `EntityDefinitionAuthoringService` is one:
 * this is the store's repository, with paged reads and errors that reach the screen.
 *
 * A rate changes what every rule, calculation and money hint converts at, so a successful create, update or delete reloads the
 * organization's table at once: the next evaluation reads it. Unlike a unit, a rate can be deleted — nothing stored carries it.
 */
@Injectable({ providedIn: 'root' })
export class ExchangeRateAuthoringService extends BaseEntityRestService<ExchangeRateEntry> {
  private readonly rates = inject(OrganizationExchangeRates);

  constructor(protected override entityMapper: ExchangeRateEntryMapper) {
    super(entityMapper, ENTITY_SERVICE_ROOT_KEY, 'exchange-rates');
  }

  override add(entity: ExchangeRateEntry, id?: number): Observable<PersistedEntity<ExchangeRateEntry>> {
    return super.add(entity, id).pipe(tap(() => this.reloadRates()));
  }

  override update(entity: PersistedEntity<ExchangeRateEntry>): Observable<PersistedEntity<ExchangeRateEntry>> {
    return super.update(entity).pipe(tap(() => this.reloadRates()));
  }

  override delete(id: string): Observable<unknown> {
    return super.delete(id).pipe(tap(() => this.reloadRates()));
  }

  private reloadRates(): void {
    this.rates.invalidate();
    void this.rates.ensureLoaded();
  }
}
