import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { describe, expect, it } from 'vitest';
import { WIDGET_INPUT_PORT_I18N_SCOPE, WIDGET_OUTPUT_PORT_I18N_SCOPE } from '../base-widget.i18n';
import { PORT_TYPES } from './widget-definition';
import { WIDGET_DEFINITION_ENTITY_NAME, WIDGET_INPUT_PORT_ENTITY_NAME, WIDGET_OUTPUT_PORT_ENTITY_NAME } from './widget-entity-names';
import { createWidgetInputPortDescriptor, createWidgetOutputPortDescriptor, WIDGET_PORT_ID_FIELD } from './widget-port.descriptors';

function flattenAttrs(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flattenAttrs(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('widget port descriptors', () => {
  const inputDescriptor = createWidgetInputPortDescriptor();
  const outputDescriptor = createWidgetOutputPortDescriptor();
  const inputAttrNames = flattenAttrs(inputDescriptor.attrDescriptors).map((attr) => attr.attrName);
  const outputAttrNames = flattenAttrs(outputDescriptor.attrDescriptors).map((attr) => attr.attrName);

  it('are embedded levels of the definition', () => {
    expect([inputDescriptor.entityName, outputDescriptor.entityName]).toEqual([WIDGET_INPUT_PORT_ENTITY_NAME, WIDGET_OUTPUT_PORT_ENTITY_NAME]);
    expect([inputDescriptor.isEmbedded, outputDescriptor.isEmbedded]).toEqual([true, true]);
    expect([inputDescriptor.componentParents, outputDescriptor.componentParents]).toEqual([[WIDGET_DEFINITION_ENTITY_NAME], [WIDGET_DEFINITION_ENTITY_NAME]]);
    expect([inputDescriptor.i18nScope, outputDescriptor.i18nScope]).toEqual([WIDGET_INPUT_PORT_I18N_SCOPE, WIDGET_OUTPUT_PORT_I18N_SCOPE]);
  });

  /** `name` identifies a port row: the contract gives it no id, and the URL segment is built from it. */
  it('identify a row by its name', () => {
    expect(WIDGET_PORT_ID_FIELD).toBe('name');
    const nameAttr = flattenAttrs(inputDescriptor.attrDescriptors).find((attr) => attr.attrName === 'name');
    expect(nameAttr?.required).toBe(true);
    expect(nameAttr?.isHeading).toBe(true);
    expect(nameAttr?.isLinkToDetails).toBe(true);
  });

  it('offer every port type the contract defines', () => {
    const typeAttr = flattenAttrs(inputDescriptor.attrDescriptors).find((attr) => attr.attrName === 'type');

    expect(typeAttr?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(typeAttr?.getSelectables()?.map((selectable) => selectable.value)).toEqual([...PORT_TYPES]);
  });

  /** Only an input port is something the container has to supply, so only it carries these three. */
  it('give the input side the fields an output port has no use for', () => {
    expect(inputAttrNames).toEqual(expect.arrayContaining(['required', 'defaultValue', 'defaultRsqlFilter']));
    expect(outputAttrNames).not.toContain('required');
    expect(outputAttrNames).not.toContain('defaultValue');
    expect(outputAttrNames).not.toContain('defaultRsqlFilter');
  });
});
