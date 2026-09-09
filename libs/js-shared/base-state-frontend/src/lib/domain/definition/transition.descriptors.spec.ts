import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { BEAN_REF_ID_FIELD } from './bean-ref.descriptors';
import { STATE_MACHINE_DEFINITION_ENTITY_NAME, STATE_MACHINE_TRANSITION_ENTITY_NAME, STATE_TRANSITION_ACTION_ENTITY_NAME, STATE_TRANSITION_GUARD_ENTITY_NAME } from './state-entity-names';
import { createTransitionDescriptor, STATE_MACHINE_TRANSITION_ID_FIELD } from './transition.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createTransitionDescriptor', () => {
  const descriptor = createTransitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(STATE_MACHINE_TRANSITION_ENTITY_NAME);
  });

  it('is an embedded component of the state machine definition', () => {
    expect(descriptor.componentParents).toEqual([STATE_MACHINE_DEFINITION_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_state.state_machine_transition');
    expect(byName('triggerKey')?.i18nKey()).toBe('base_state.state_machine_transition.triggerKey');
  });

  it('describes the edge, its trigger and the behaviour hanging off it', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['key', 'name', 'triggerKey', 'sourceStateKey', 'targetStateKey', 'guards', 'actions']);
  });

  it('identifies a transition by its key, the schema giving it no id', () => {
    expect(byName('key')?.required).toBe(true);
    expect(byName('key')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe(STATE_MACHINE_TRANSITION_ID_FIELD);
  });

  it('requires everything an edge needs to be resolvable', () => {
    expect(byName('sourceStateKey')?.required).toBe(true);
    expect(byName('targetStateKey')?.required).toBe(true);
    expect(byName('triggerKey')?.required).toBe(true);
  });

  it('contains the guards and the actions as embedded lists of their own', () => {
    expect(byName('guards')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('guards')?.linkedEntityType).toBe(STATE_TRANSITION_GUARD_ENTITY_NAME);
    expect(byName('actions')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('actions')?.linkedEntityType).toBe(STATE_TRANSITION_ACTION_ENTITY_NAME);
  });

  // A bean reference has no `id` either, so both lists have to name the field that identifies a row.
  it('names the field that identifies a guard and an action row', () => {
    expect(byName('guards')?.referenceIdField).toBe(BEAN_REF_ID_FIELD);
    expect(byName('actions')?.referenceIdField).toBe(BEAN_REF_ID_FIELD);
  });

  // Guards and actions share a shape but not an entity name, precisely so that each list is addressable
  // by a URL segment of its own. Were they one name, `embeddedAttrFor` could not tell the two apart.
  it('carries each embedded child type on exactly one attribute', () => {
    expect(descriptor.embeddedAttrFor(STATE_TRANSITION_GUARD_ENTITY_NAME)?.attrName).toBe('guards');
    expect(descriptor.embeddedAttrFor(STATE_TRANSITION_ACTION_ENTITY_NAME)?.attrName).toBe('actions');
  });

  it('keeps the list to the edge itself', () => {
    const tableColumns = attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName);

    expect(tableColumns).toEqual(['key', 'name', 'triggerKey', 'sourceStateKey', 'targetStateKey']);
  });
});
