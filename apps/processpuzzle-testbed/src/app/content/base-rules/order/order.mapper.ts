// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { Injectable } from '@angular/core';
import { Order } from './order';

type OrderDto = Partial<Order>;

@Injectable({ providedIn: 'root' })
export class OrderMapper implements BaseEntityMapper<Order> {
  fromDto(dto: unknown): Order {
    const source = dto as OrderDto;
    return new Order(source.id, source.orderNumber, source.customerName, source.status, source.total, source.shippingAddress, source.lineItems);
  }

  toDto(entity: Order): unknown {
    return entity;
  }
}
