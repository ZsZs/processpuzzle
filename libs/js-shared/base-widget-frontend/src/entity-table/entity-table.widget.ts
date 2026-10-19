import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { EntityTableComponent } from './entity-table.component';

export const ENTITY_TABLE_WIDGET = 'entity-table';

// `rows` is the one bound input: a document binds it to a port path (`order.lineItems`). Columns and totals
// are the author's layout, which is why they are props rather than the port's attributeVisibility — they need
// an order, a header and a format each.
export const ENTITY_TABLE_WIDGET_REGISTRATION: WidgetRegistration = {
  type: ENTITY_TABLE_WIDGET,
  component: EntityTableComponent,
  definition: {
    name: 'Entity table',
    translocoId: 'base_widget.entity_table.name',
    description: 'A printable table of objects: the rows bound to a collection, one column per chosen attribute, optionally with a totals row.',
    category: 'Data',
    icon: 'table_rows',
    propsSchema: {
      type: 'object',
      properties: {
        rows: {
          type: 'array',
          title: 'Rows',
          description: 'The objects to list. Usually bound to a port path rather than typed in.',
          items: {
            type: 'object',
          },
        },
        columns: {
          type: 'array',
          title: 'Columns',
          items: {
            type: 'object',
            properties: {
              path: {
                type: 'string',
                title: 'Attribute path',
              },
              header: {
                type: 'string',
                title: 'Header',
              },
              format: {
                type: 'string',
                title: 'Format',
              },
              align: {
                type: 'string',
                enum: ['start', 'center', 'end'],
              },
            },
            required: ['path'],
          },
        },
        totals: {
          type: 'array',
          title: 'Totals',
          items: {
            type: 'object',
            properties: {
              path: {
                type: 'string',
                title: 'Attribute path',
              },
              aggregate: {
                type: 'string',
                enum: ['sum', 'count'],
              },
            },
            required: ['path', 'aggregate'],
          },
        },
        caption: {
          type: 'string',
          title: 'Caption',
        },
      },
      additionalProperties: false,
    },
    inputPorts: [
      {
        name: 'rows',
        type: 'ENTITY_COLLECTION',
        required: false,
        description: 'The objects to list, one row each.',
      },
    ],
  },
};
