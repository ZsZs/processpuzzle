// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { Injectable } from '@angular/core';
import { Order } from './order';

@Injectable({ providedIn: 'root' })
export class OrderMapper implements BaseEntityMapper<Order> {
  fromDto(dto: any): Order {
    return new Order(dto.id, dto.orderNumber, dto.customerName, dto.status, dto.total, dto.shippingAddress, dto.lineItems);
  }

  toDto(entity: Order): any {
    return entity;
  }
}
