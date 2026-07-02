import { inject, Injectable, Type } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { OrderLine } from './order-line';
import { OrderLineMapper } from './order-line.mapper';
import { OrderLineService } from './order-line.service';
import { OrderLineStore } from './order-line.store';
import { createOrderLineDescriptor } from './order-line.descriptors';

@Injectable()
export class OrderLineFacade extends BaseEntityFacade<OrderLine> {
  readonly entityType = OrderLine;

  private readonly mapperRef = inject(OrderLineMapper);
  private readonly serviceRef = inject(OrderLineService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return OrderLineStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createOrderLineDescriptor();
  }
}
