import { describe, expect, it } from 'vitest';
import { WIDGET_DEFINITION_DTO } from './test-widget-definition';
import { WidgetDefinitionMapper } from './widget-definition.mapper';

describe('WidgetDefinitionMapper', () => {
  const mapper = new WidgetDefinitionMapper();

  it('renames the contract key onto id', () => {
    const definition = mapper.fromDto(WIDGET_DEFINITION_DTO);

    expect(definition.id).toBe('cards-grid');
    expect('key' in definition).toBe(false);
    expect(mapper.toDto(definition).key).toBe('cards-grid');
    expect('id' in mapper.toDto(definition)).toBe(false);
  });

  it('reads the whole definition, ports and schema included', () => {
    const definition = mapper.fromDto(WIDGET_DEFINITION_DTO);

    expect(definition.name).toBe('Cards grid');
    expect(definition.category).toBe('Content');
    expect(definition.status).toBe('PUBLISHED');
    expect(definition.publishedVersion).toBe(2);
    expect(definition.inputPorts?.[0].attributeVisibility?.attributes).toEqual(['id', 'name']);
    expect(definition.outputPorts?.[0].name).toBe('selected');
    expect(Object.keys(definition.propsSchema?.properties ?? {})).toEqual(['title', 'columns', 'cards']);
  });

  /**
   * The invariant the class doc states: every contract field travels both ways, whether or not the form
   * renders it. `propsSchema` is the one that matters — it has no control, and a mapper that dropped it
   * would let a Save of an unrelated field destroy it, since `update` PUTs the whole input schema.
   */
  it('round-trips a definition without losing a field', () => {
    expect(mapper.toDto(mapper.fromDto(WIDGET_DEFINITION_DTO))).toEqual(WIDGET_DEFINITION_DTO);
  });

  it('leaves an absent optional absent rather than inventing a value', () => {
    const definition = mapper.fromDto({ key: 'bare', name: 'Bare' });

    expect(definition.propsSchema).toBeUndefined();
    expect(definition.inputPorts).toBeUndefined();
    expect(definition.status).toBeUndefined();
    expect(mapper.toDto(definition)).toEqual({
      key: 'bare',
      name: 'Bare',
      translocoId: undefined,
      description: undefined,
      category: undefined,
      icon: undefined,
      propsSchema: undefined,
      inputPorts: undefined,
      outputPorts: undefined,
      orgKey: undefined,
      status: undefined,
      version: undefined,
      publishedVersion: undefined,
      createdAt: undefined,
      updatedAt: undefined,
    });
  });
});
