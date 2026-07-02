import { inject, Injectable, Type } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { Order } from './order';
import { OrderMapper } from './order.mapper';
import { OrderService } from './order.service';
import { OrderStore } from './order.store';
import { createOrderDescriptor } from './order.descriptors';

@Injectable()
export class OrderFacade extends BaseEntityFacade<Order> {
  readonly entityType = Order;

  private readonly mapperRef = inject(OrderMapper);
  private readonly serviceRef = inject(OrderService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return OrderStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createOrderDescriptor();
  }
}
