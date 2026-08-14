/**
 * The DTO shape `GET /organizations/{orgKey}/widget-definitions` returns, with every field of the contract
 * populated — including a nested `propsSchema` and both port lists, because the mapper's job is to carry
 * all of them and a fixture that omits one cannot prove it.
 *
 * Named `test-*` for the same reason as `test-app-definition.ts` in base-app: `tsconfig.lib.json` keeps it
 * out of the published package.
 */
export const WIDGET_DEFINITION_DTO = {
  key: 'cards-grid',
  name: 'Cards grid',
  translocoId: 'base_widget.cards_grid.name',
  description: 'A responsive grid of Material cards.',
  category: 'Content',
  icon: 'grid_view',
  propsSchema: {
    type: 'object',
    required: ['cards'],
    properties: {
      title: { type: 'string', title: 'Title', maxLength: 80 },
      columns: { type: 'integer', title: 'Columns', default: 3 },
      cards: { type: 'array', title: 'Cards', items: { type: 'object', properties: { caption: { type: 'string' } } } },
    },
  },
  inputPorts: [{ name: 'items', type: 'ENTITY_COLLECTION', required: true, entityType: 'Order', attributeVisibility: { mode: 'INCLUDE', attributes: ['id', 'name'] }, defaultRsqlFilter: 'status==OPEN' }],
  outputPorts: [{ name: 'selected', type: 'ENTITY_REF', description: 'The card the user picked.', entityType: 'Order' }],
  orgKey: 'processpuzzle-testbed',
  status: 'PUBLISHED',
  version: 3,
  publishedVersion: 2,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-02-01T00:00:00Z',
};

/** A second definition, so list specs can tell entries apart, and a DRAFT to contrast the status with. */
export const OTHER_WIDGET_DEFINITION_DTO = { ...WIDGET_DEFINITION_DTO, key: 'entity-grid', name: 'Entity grid', status: 'DRAFT', version: 1, publishedVersion: undefined };

/** Wraps entries in the `PageOf_WidgetDefinition` envelope the Spring backend answers with. */
export function pageOfWidgetDefinitions(...content: unknown[]) {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}
