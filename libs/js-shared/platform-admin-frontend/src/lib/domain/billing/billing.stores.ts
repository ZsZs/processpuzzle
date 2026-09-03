import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { Invoice, Plan, Subscription } from './billing';
import { InvoiceService, PlanService, SubscriptionService } from './billing.service';

export const PlanStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<Plan>(Plan, () => inject(PlanService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('Plan'),
);

export const SubscriptionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<Subscription>(Subscription, () => inject(SubscriptionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('Subscription'),
);

export const InvoiceStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<Invoice>(Invoice, () => inject(InvoiceService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('Invoice'),
);
