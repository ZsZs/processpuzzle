import { Injectable } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { OrderLine } from './order-line';
import { OrderLineMapper } from './order-line.mapper';

@Injectable({ providedIn: 'root' })
export class OrderLineService extends BaseEntityRestService<OrderLine> {
  constructor(protected override entityMapper: OrderLineMapper) {
    super(entityMapper, 'BACKEND_SERVICE_ROOT', 'order-line');
  }
}
