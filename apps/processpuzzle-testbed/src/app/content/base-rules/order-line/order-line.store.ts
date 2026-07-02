import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { inject } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { OrderLineService } from './order-line.service';
import { OrderLine } from './order-line';

export const OrderLineStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<OrderLine>(OrderLine, () => inject(OrderLineService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('OrderLine'),
);
