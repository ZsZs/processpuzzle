import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore } from '../base-entity-container.store';
import { BaseEntityStore } from '../base-entity-store/base-entity.store';
import { BaseEntityTabsStore } from '../base-tabs/base-entity-tabs.store';
import { ExchangeRateEntry } from './exchange-rate-entry';
import { ExchangeRateAuthoringService } from './exchange-rate-authoring.service';

/** The stock CRUD store, `providedIn: 'root'` because it outlives the route, like the stores beside it. */
export const ExchangeRateEntryStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<ExchangeRateEntry>(ExchangeRateEntry, () => inject(ExchangeRateAuthoringService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('ExchangeRateEntry'),
);
