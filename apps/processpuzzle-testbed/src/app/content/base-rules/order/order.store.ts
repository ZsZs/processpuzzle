import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { inject } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { OrderService } from './order.service';
import { Order } from './order';

export const OrderStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<Order>(Order, () => inject(OrderService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('Order'),
);
