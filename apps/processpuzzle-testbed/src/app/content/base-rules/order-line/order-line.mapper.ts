import { Injectable } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { OrderLine } from './order-line';

type OrderLineDto = Partial<OrderLine>;

@Injectable({ providedIn: 'root' })
export class OrderLineMapper implements BaseEntityMapper<OrderLine> {
  fromDto(dto: unknown): OrderLine {
    const source = dto as OrderLineDto;
    return new OrderLine(source.id, source.productName, source.quantity, source.unitPrice, source.orderId);
  }

  toDto(entity: OrderLine): unknown {
    return entity;
  }
}
