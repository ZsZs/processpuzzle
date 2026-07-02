import { Injectable } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { OrderLine } from './order-line';

@Injectable({ providedIn: 'root' })
export class OrderLineMapper implements BaseEntityMapper<OrderLine> {
  fromDto(dto: any): OrderLine {
    return new OrderLine(dto.id, dto.productName, dto.quantity, dto.unitPrice, dto.orderId);
  }

  toDto(entity: OrderLine): any {
    return entity;
  }
}
