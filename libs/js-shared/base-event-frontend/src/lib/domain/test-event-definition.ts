/**
 * The wire shape of two of the event definitions base-event-backend seeds for `processpuzzle-testbed`:
 * a plain `CREATED` event and a `STATE_CHANGED` one carrying the state it waits for.
 */
export const ORDER_CREATED_EVENT_DTO = {
  id: 'OrderCreatedEvent',
  name: 'Order Created',
  description: 'An order entity object was created.',
  kind: 'SYSTEM',
  subjectType: 'order',
  action: 'CREATED',
  parameters: [
    { name: 'orderNumber', type: 'STRING', required: true, description: 'The number the customer quotes.' },
    { name: 'customerName', type: 'STRING', required: false },
  ],
  version: 0,
};

/** A MESSAGE event carrying two record references and a value, as `InvoiceIssued` is seeded. */
export const INVOICE_ISSUED_EVENT_DTO = {
  id: 'InvoiceIssued',
  name: 'Invoice Issued',
  kind: 'MESSAGE',
  parameters: [
    { name: 'order', type: 'ENTITY_REF', entityType: 'order', required: true },
    { name: 'invoice', type: 'ENTITY_REF', entityType: 'document', required: true },
    { name: 'invoiceNumber', type: 'STRING', required: true },
  ],
  version: 1,
};

export const ORDER_CONFIRMED_EVENT_DTO = {
  id: 'OrderConfirmedEvent',
  name: 'Order Confirmed',
  kind: 'SYSTEM',
  subjectType: 'order',
  action: 'STATE_CHANGED',
  state: 'CONFIRMED',
  version: 2,
};

export function pageOfEventDefinitions(...content: unknown[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}
