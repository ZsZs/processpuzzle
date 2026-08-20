import { EntityDefinition } from './entity-definition';

/**
 * The `order` / `order-line` pair as the testbed seeds it
 * (`base-entity-backend/src/main/resources/default-entities/processpuzzle-testbed-entities.yaml`), trimmed
 * to what a spec needs.
 *
 * A copy of real seed data rather than invented shapes, so that a spec asserting on the synthesized
 * descriptor is asserting about something a running system actually produces: `TEXT_BOX` and `TEXTAREA`
 * alongside a `DROPDOWN` fed by `enumValues`, an `EMBEDDED_COMPONENTS` attribute naming a child by *code*,
 * and a child whose rows carry no `id` and are titled by `productName`.
 */
export const ORDER_LINE_DEFINITION: EntityDefinition = {
  code: 'order-line',
  name: 'Order Line',
  status: 'ACTIVE',
  isEmbedded: true,
  componentParents: ['order'],
  attributes: [
    { code: 'productName', name: 'Product', displayOrder: 1, valueKind: 'TEXT', formControlType: 'TEXT_BOX', required: true, isLinkToDetails: true },
    { code: 'quantity', name: 'Quantity', displayOrder: 2, valueKind: 'NUMBER', formControlType: 'TEXT_BOX', required: true },
    { code: 'unitPrice', name: 'Unit Price', displayOrder: 3, valueKind: 'NUMBER', formControlType: 'TEXT_BOX', required: true },
  ],
};

export const ORDER_DEFINITION: EntityDefinition = {
  code: 'order',
  name: 'Order',
  status: 'ACTIVE',
  isEmbedded: false,
  componentParents: [],
  attributes: [
    { code: 'orderNumber', name: 'Order #', displayOrder: 1, valueKind: 'TEXT', formControlType: 'TEXT_BOX', required: true, isLinkToDetails: true },
    { code: 'customerName', name: 'Customer', displayOrder: 2, valueKind: 'TEXT', formControlType: 'TEXT_BOX', required: true },
    {
      code: 'status',
      name: 'Status',
      displayOrder: 3,
      valueKind: 'ENUM',
      formControlType: 'DROPDOWN',
      enumValues: ['DRAFT', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'],
      defaultValue: 'DRAFT',
    },
    { code: 'total', name: 'Total', displayOrder: 4, valueKind: 'NUMBER', formControlType: 'TEXT_BOX' },
    { code: 'shippingAddress', name: 'Shipping Address', displayOrder: 5, valueKind: 'TEXT', formControlType: 'TEXTAREA' },
    { code: 'lineItems', name: 'Line Items', displayOrder: 6, valueKind: 'REFERENCE', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'order-line', isMultiValued: true },
  ],
};

export const TEST_ENTITY_DEFINITIONS: EntityDefinition[] = [ORDER_LINE_DEFINITION, ORDER_DEFINITION];

/** The `code -> definition` lookup `descriptorOf` takes, over the definitions given. */
export function definitionLookup(definitions: EntityDefinition[] = TEST_ENTITY_DEFINITIONS) {
  return (code: string) => definitions.find((definition) => definition.code === code);
}
