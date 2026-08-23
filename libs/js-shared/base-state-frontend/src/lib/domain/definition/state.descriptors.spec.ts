import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { STATE_MACHINE_DEFINITION_ENTITY_NAME, STATE_MACHINE_STATE_ENTITY_NAME } from './state-entity-names';
import { createStateDescriptor, STATE_MACHINE_STATE_ID_FIELD } from './state.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createStateDescriptor', () => {
  const descriptor = createStateDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(STATE_MACHINE_STATE_ENTITY_NAME);
  });

  it('is an embedded component of the state machine definition', () => {
    expect(descriptor.componentParents).toEqual([STATE_MACHINE_DEFINITION_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
    expect(descriptor.parentReferenceAttrName()).toBeUndefined();
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_state.state_machine_state');
    expect(descriptor.i18nKey()).toBe('base_state.state_machine_state._self');
    expect(byName('isFinal')?.i18nKey()).toBe('base_state.state_machine_state.isFinal');
  });

  it('describes the state, its two flags and its opaque UI hints', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['key', 'name', 'isFinal', 'isLocked', 'description', 'metadata']);
  });

  it('identifies a state by the value it writes, the schema giving it no id', () => {
    expect(byName('key')?.required).toBe(true);
    expect(byName('key')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe(STATE_MACHINE_STATE_ID_FIELD);
  });

  it('offers both flags as checkboxes', () => {
    expect(byName('isFinal')?.formControlType).toBe(FormControlType.CHECKBOX);
    expect(byName('isLocked')?.formControlType).toBe(FormControlType.CHECKBOX);
  });

  // `additionalProperties: true` by contract and opaque to the backend, so a closed set of controls
  // could only ever be wrong.
  it('edits the metadata as an open key/value map', () => {
    expect(byName('metadata')?.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES);
  });

  it('has no embedded children, states not nesting in this version of the contract', () => {
    expect(attrs.filter((attr) => attr.formControlType === FormControlType.EMBEDDED_COMPONENTS)).toEqual([]);
  });

  // Which states are final and which are locked is the shape of the machine's boundary; reading it
  // off the list beats opening every state's form.
  it('shows the identity and both flags in the list', () => {
    const tableColumns = attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName);

    expect(tableColumns).toEqual(['key', 'name', 'isFinal', 'isLocked']);
  });
});
