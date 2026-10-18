import { TestBed } from '@angular/core/testing';
import { EntityDefinition, EntityDefinitionRegistry } from '@processpuzzle/base-entity';
import { beforeEach, describe, expect, it } from 'vitest';
import { DocumentInputPort, PortType } from '../../domain/base-document';
import { DocumentPathCatalog } from './document-path-catalog';

const definitions = new Map(
  [
    new EntityDefinition({
      code: 'order',
      name: 'Order',
      attributes: [
        { code: 'total', name: 'Total', valueKind: 'NUMBER', formControlType: 'TEXT_BOX', displayOrder: 3 },
        { code: 'orderNumber', name: 'Order #', valueKind: 'TEXT', formControlType: 'TEXT_BOX', displayOrder: 1 },
        { code: 'placedAt', valueKind: 'DATE_TIME', formControlType: 'DATE_TIME', displayOrder: 2 },
        { code: 'customer', name: 'Customer', valueKind: 'REFERENCE', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'customer', multiplicity: '0..1' },
        { code: 'lineItems', name: 'Line Items', valueKind: 'REFERENCE', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'order-line', multiplicity: '0..n' },
        { code: 'comments', valueKind: 'REFERENCE', formControlType: 'COMMENTS', multiplicity: '0..n' },
        { code: 'ghost', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'missing' },
      ],
    }),
    new EntityDefinition({
      code: 'customer',
      name: 'Customer',
      attributes: [
        { code: 'name', valueKind: 'TEXT', formControlType: 'TEXT_BOX' },
        { code: 'since', valueKind: 'DATE', formControlType: 'DATE' },
      ],
    }),
    new EntityDefinition({
      code: 'order-line',
      name: 'Order Line',
      attributes: [
        { code: 'productName', name: 'Product', valueKind: 'TEXT', formControlType: 'TEXT_BOX' },
        { code: 'quantity', name: 'Quantity', valueKind: 'NUMBER', formControlType: 'TEXT_BOX' },
        { code: 'customer', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'customer' },
        { code: 'notes', formControlType: 'EMBEDDED_COMPONENTS', linkedEntityType: 'customer', multiplicity: '1..n' },
      ],
    }),
  ].map((definition) => [definition.code, definition] as const),
);

function port(name: string, type: PortType, entityType?: string): DocumentInputPort {
  return Object.assign(new DocumentInputPort(name, type), { entityType });
}

describe('DocumentPathCatalog', () => {
  let catalog: DocumentPathCatalog;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: EntityDefinitionRegistry, useValue: { load: async () => definitions } }] });
    catalog = TestBed.inject(DocumentPathCatalog);
  });

  it('offers the attributes of an ENTITY_REF port in display order, walking single embedded objects', async () => {
    const { fields } = await catalog.describe([port('order', PortType.ENTITY_REF, 'Order')]);

    expect(fields.map((field) => field.path)).toEqual(['order.orderNumber', 'order.placedAt', 'order.total', 'order.customer.name', 'order.customer.since']);
    expect(fields.find((field) => field.path === 'order.total')).toEqual({ path: 'order.total', label: 'order › Total', format: 'number' });
    expect(fields.find((field) => field.path === 'order.placedAt')?.format).toBe('datetime');
    expect(fields.find((field) => field.path === 'order.customer.since')?.format).toBe('date');
  });

  it('offers a multi-valued embedded attribute as a collection, with the fields of its rows', async () => {
    const { collections } = await catalog.describe([port('order', PortType.ENTITY_REF, 'Order')]);

    expect(collections).toEqual([
      {
        path: 'order.lineItems',
        label: 'order › Line Items',
        rowFields: [
          { path: 'productName', label: 'Product', format: 'text' },
          { path: 'quantity', label: 'Quantity', format: 'number' },
          { path: 'customer.name', label: 'customer › name', format: 'text' },
          { path: 'customer.since', label: 'customer › since', format: 'date' },
        ],
      },
    ]);
  });

  it('offers an ENTITY_COLLECTION port as a collection and a plain port as a field', async () => {
    const paths = await catalog.describe([port('lines', PortType.ENTITY_COLLECTION, 'Order Line'), port('count', PortType.NUMBER), port('due', PortType.DATE), port('note', PortType.STRING)]);

    expect(paths.collections.map((collection) => collection.path)).toEqual(['lines']);
    expect(paths.fields).toEqual([
      { path: 'count', label: 'count', format: 'number' },
      { path: 'due', label: 'due', format: 'date' },
      { path: 'note', label: 'note', format: 'text' },
    ]);
  });

  it('skips a port whose entity type has no definition', async () => {
    expect(await catalog.describe([port('x', PortType.ENTITY_REF, 'Unknown')])).toEqual({ fields: [], collections: [] });
  });
});
