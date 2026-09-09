import { describe, expect, it } from 'vitest';
import { InputPort, OutputPort, PORT_TYPES, WIDGET_DEFINITION_STATUSES, WidgetDefinition } from './widget-definition';

describe('WidgetDefinition', () => {
  it('starts as an empty draft nobody has described yet', () => {
    const definition = new WidgetDefinition();

    expect(definition.id).toBe('');
    expect(definition.name).toBe('');
    // Undefined rather than `{}`: no schema means "props unconstrained", an empty schema would assert the
    // widget takes no props at all. The contract keeps the field nullable for exactly that distinction.
    expect(definition.propsSchema).toBeUndefined();
    expect(definition.inputPorts).toBeUndefined();
    expect(definition.outputPorts).toBeUndefined();
    expect(definition.status).toBeUndefined();
  });

  it('keeps every field it is initialized with', () => {
    const definition = new WidgetDefinition({
      id: 'cards-grid',
      name: 'Cards grid',
      translocoId: 'base_widget.cards_grid.name',
      description: 'A grid.',
      category: 'Content',
      icon: 'grid_view',
      propsSchema: { type: 'object', properties: { title: { type: 'string' } } },
      inputPorts: [new InputPort('items', 'ENTITY_COLLECTION', true)],
      outputPorts: [new OutputPort('selected', 'ENTITY_REF')],
      orgKey: 'processpuzzle-testbed',
      status: 'PUBLISHED',
      version: 3,
      publishedVersion: 2,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-02-01T00:00:00Z',
    });

    expect(definition.inputPorts?.[0].required).toBe(true);
    expect(definition.outputPorts?.[0].type).toBe('ENTITY_REF');
    expect(definition.publishedVersion).toBe(2);
    expect(definition.propsSchema?.properties?.['title'].type).toBe('string');
  });

  it('declares the enumerations the contract does', () => {
    expect(PORT_TYPES).toContain('ENTITY_COLLECTION');
    expect(WIDGET_DEFINITION_STATUSES).toEqual(['DRAFT', 'PUBLISHED']);
  });
});

describe('ports', () => {
  it('default to a plain string port the container need not supply', () => {
    const port = new InputPort();

    expect(port.name).toBe('');
    expect(port.type).toBe('STRING');
    expect(port.required).toBe(false);
    expect(port.attributeVisibility).toBeUndefined();
  });

  /** An output port has no `required` / `defaultValue`: nothing is asked of the container. */
  it('carry only what their direction needs', () => {
    const output = new OutputPort('selected', 'ENTITY_REF', 'The row picked.', 'Order', { mode: 'INCLUDE', attributes: ['id'] });

    expect(output.description).toBe('The row picked.');
    expect(output.attributeVisibility?.attributes).toEqual(['id']);
    expect('required' in output).toBe(false);
  });
});
