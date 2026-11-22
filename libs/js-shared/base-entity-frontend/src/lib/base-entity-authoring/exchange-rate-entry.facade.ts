import { inject, Injectable, type Type } from '@angular/core';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseEntityFacade } from '../base-entity-facade/base-entity-facade';
import { UNIT_CATALOG } from '../base-form/fundamental-types/unit-catalog.token';
import { ExchangeRateEntry } from './exchange-rate-entry';
import { ExchangeRateAuthoringService } from './exchange-rate-authoring.service';
import { createExchangeRateDescriptor } from './exchange-rate-entry.descriptors';
import { ExchangeRateEntryMapper } from './exchange-rate-entry.mapper';
import { ExchangeRateEntryStore } from './exchange-rate-entry.store';

/**
 * The routable `Exchange Rate`: the list and form of the rates an organization enters. The mapper and the service are injected
 * rather than constructed, so the store's repository and this facade's are the same object.
 */
@Injectable()
export class ExchangeRateEntryFacade extends BaseEntityFacade<ExchangeRateEntry> {
  readonly entityType = ExchangeRateEntry;

  private readonly mapperRef = inject(ExchangeRateEntryMapper);
  private readonly serviceRef = inject(ExchangeRateAuthoringService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return ExchangeRateEntryStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    const catalog = this.injector.get(UNIT_CATALOG);
    return createExchangeRateDescriptor(() => catalog.currencies().map((currency) => currency.code));
  }
}
