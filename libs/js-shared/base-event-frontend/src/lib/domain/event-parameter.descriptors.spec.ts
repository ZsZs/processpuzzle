import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType, snakeCaseName } from '@processpuzzle/base-entity';
import { EVENT_DEFINITION_ENTITY_NAME } from './event-definition.descriptors';
import { createEventParameterDescriptor, EVENT_PARAMETER_ENTITY_NAME, EVENT_PARAMETER_ID_FIELD } from './event-parameter.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createEventParameterDescriptor', () => {
  const descriptor = createEventParameterDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the embedded entity, and the route segment follows from it', () => {
    expect(EVENT_PARAMETER_ENTITY_NAME).toBe('Event Parameter');
    expect(descriptor.entityName).toBe(EVENT_PARAMETER_ENTITY_NAME);
    expect(snakeCaseName(descriptor.entityName)).toBe('event-parameter');
  });

  it('is embedded in the event definition', () => {
    expect(descriptor.isEmbedded).toBe(true);
    expect(descriptor.componentParents).toEqual([EVENT_DEFINITION_ENTITY_NAME]);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_event.event_parameter');
    expect(descriptor.i18nKey()).toBe('base_event.event_parameter._self');
    expect(byName('entityType')?.i18nKey()).toBe('base_event.event_parameter.entityType');
  });

  it('describes the fields of the contract', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['name', 'type', 'entityType', 'required', 'description']);
  });

  // No id of its own: the name is unique within the definition, and what the definition's list keys the row by.
  it('identifies a parameter by its name, an identifier', () => {
    expect(EVENT_PARAMETER_ID_FIELD).toBe('name');
    expect(byName('name')?.required).toBe(true);
    expect(byName('name')?.isLinkToDetails).toBe(true);
    expect(byName('name')?.isHeading).toBe(true);
    expect(new RegExp(byName('name')?.pattern ?? '').test('invoiceNumber')).toBe(true);
    expect(new RegExp(byName('name')?.pattern ?? '').test('invoice-number')).toBe(false);
  });

  it('offers the types the contract defines', () => {
    expect(byName('type')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('type')?.required).toBe(true);
    expect(
      byName('type')
        ?.getSelectables()
        ?.map((selectable) => selectable.key),
    ).toEqual(['STRING', 'NUMBER', 'BOOLEAN', 'DATE', 'ENTITY_REF']);
  });

  // Meaningful for ENTITY_REF only, which the form cannot express; the mapper drops it otherwise.
  it('leaves the entity type optional and edits required as a checkbox', () => {
    expect(byName('entityType')?.required).toBeFalsy();
    expect(byName('required')?.formControlType).toBe(FormControlType.CHECKBOX);
  });

  it('keeps the description out of the list', () => {
    expect(attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName)).toEqual(['name', 'type', 'entityType', 'required']);
  });
});
