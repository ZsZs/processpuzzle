import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import { WIDGET_DEFINITION_I18N_SCOPE } from '../base-widget.i18n';
import { createWidgetDefinitionDescriptor, WIDGET_KEY_PATTERN } from './widget-definition.descriptors';
import { WIDGET_DEFINITION_ENTITY_NAME, WIDGET_INPUT_PORT_ENTITY_NAME, WIDGET_OUTPUT_PORT_ENTITY_NAME } from './widget-entity-names';
import { WIDGET_PORT_ID_FIELD } from './widget-port.descriptors';

function flattenAttrs(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flattenAttrs(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createWidgetDefinitionDescriptor', () => {
  const descriptor = createWidgetDefinitionDescriptor();
  const attrs = flattenAttrs(descriptor.attrDescriptors);
  const attrOf = (name: string) => attrs.find((attr) => attr.attrName === name);

  it('describes a routable entity of this library', () => {
    expect(descriptor.entityName).toBe(WIDGET_DEFINITION_ENTITY_NAME);
    expect(descriptor.i18nScope).toBe(WIDGET_DEFINITION_I18N_SCOPE);
    expect(descriptor.isEmbedded).toBeFalsy();
    expect(descriptor.componentParents).toEqual([]);
  });

  /** The contract's `key`, under the name base-entity keys stores and URLs on, with the contract's pattern. */
  it('edits the registry key as the entity id', () => {
    const idAttr = attrOf('id');

    expect(idAttr?.label).toBe('Key');
    expect(idAttr?.required).toBe(true);
    expect(idAttr?.pattern).toBe(WIDGET_KEY_PATTERN);
    expect(new RegExp(WIDGET_KEY_PATTERN).test('cards-grid')).toBe(true);
    expect(new RegExp(WIDGET_KEY_PATTERN).test('Cards Grid')).toBe(false);
  });

  it('shows the server-assigned fields without letting them be edited', () => {
    expect(['status', 'version', 'publishedVersion', 'updatedAt'].map((name) => attrOf(name)?.disabled)).toEqual([true, true, true, true]);
  });

  it('contains both port lists as embedded rows keyed by name', () => {
    const inputPortsAttr = attrOf('inputPorts');
    const outputPortsAttr = attrOf('outputPorts');

    expect(inputPortsAttr?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(inputPortsAttr?.linkedEntityType).toBe(WIDGET_INPUT_PORT_ENTITY_NAME);
    expect(inputPortsAttr?.referenceIdField).toBe(WIDGET_PORT_ID_FIELD);
    expect(outputPortsAttr?.linkedEntityType).toBe(WIDGET_OUTPUT_PORT_ENTITY_NAME);
    expect(outputPortsAttr?.referenceIdField).toBe(WIDGET_PORT_ID_FIELD);
  });

  /**
   * Asserted as an absence, because the alternative is worse than a gap: `ADDITIONAL_PROPERTIES` would
   * flatten a nested JSON Schema into something the widget no longer describes. `WidgetDefinitionMapper`
   * carries the field, so leaving it off the form does not lose it — see that spec.
   */
  it('leaves the props schema off the form', () => {
    expect(attrOf('propsSchema')).toBeUndefined();
  });
});
