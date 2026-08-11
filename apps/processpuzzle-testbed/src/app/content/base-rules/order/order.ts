import { v4 as uuidv4 } from 'uuid';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntity } from '@processpuzzle/base-entity';
import { OrderLine } from '../order-line/order-line';

export enum OrderStatus {
  DRAFT = 'DRAFT',
  CONFIRMED = 'CONFIRMED',
  SHIPPED = 'SHIPPED',
  DELIVERED = 'DELIVERED',
  CANCELLED = 'CANCELLED',
}

export class Order implements BaseEntity {
  readonly id: string;
  orderNumber: string;
  customerName: string;
  status: OrderStatus;
  total: number;
  shippingAddress: string | undefined;
  lineItems: Array<OrderLine> | undefined;

  constructor(
    id?: string,
    orderNumber?: string,
    customerName?: string,
    status?: OrderStatus,
    total?: number,
    shippingAddress?: string,
    lineItems?: Array<OrderLine>,
  ) {
    this.id = id ?? uuidv4();
    this.orderNumber = orderNumber ?? 'ORD-000';
    this.customerName = customerName ?? '';
    this.status = status ?? OrderStatus.DRAFT;
    this.total = total ?? 0;
    this.shippingAddress = shippingAddress;
    this.lineItems = lineItems;
  }
}
