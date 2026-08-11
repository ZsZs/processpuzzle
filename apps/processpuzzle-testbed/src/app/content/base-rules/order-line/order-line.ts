import { v4 as uuidv4 } from 'uuid';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntity } from '@processpuzzle/base-entity';

export class OrderLine implements BaseEntity {
  readonly id: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  orderId: string;

  constructor(id?: string, productName?: string, quantity?: number, unitPrice?: number, orderId?: string) {
    this.id = id ?? uuidv4();
    this.productName = productName ?? 'Product';
    this.quantity = quantity ?? 1;
    this.unitPrice = unitPrice ?? 0;
    this.orderId = orderId ?? '';
  }
}
