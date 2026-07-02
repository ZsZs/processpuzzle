// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { Injectable } from '@angular/core';
import { OrderMapper } from './order.mapper';
import { Order } from './order';

@Injectable({ providedIn: 'root' })
export class OrderService extends BaseEntityRestService<Order> {
  constructor(protected override entityMapper: OrderMapper) {
    super(entityMapper, 'BACKEND_SERVICE_ROOT', 'order');
  }
}
