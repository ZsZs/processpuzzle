import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { BEAN_REF_ID_FIELD, createActionRefDescriptor, createGuardRefDescriptor } from './bean-ref.descriptors';
import { STATE_MACHINE_TRANSITION_ENTITY_NAME, STATE_TRANSITION_ACTION_ENTITY_NAME, STATE_TRANSITION_GUARD_ENTITY_NAME } from './state-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe.each([
  { kind: 'guard', create: createGuardRefDescriptor, entityName: STATE_TRANSITION_GUARD_ENTITY_NAME, scopeRoot: 'base_state.state_transition_guard', beanKind: 'Guard' },
  { kind: 'action', create: createActionRefDescriptor, entityName: STATE_TRANSITION_ACTION_ENTITY_NAME, scopeRoot: 'base_state.state_transition_action', beanKind: 'Action' },
])('$kind bean reference descriptor', ({ create, entityName, scopeRoot, beanKind }) => {
  const descriptor = create();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(entityName);
  });

  // The transition, not the definition: a guard belongs to the edge that evaluates it, and the URL
  // that addresses it runs through that edge.
  it('is an embedded component of the transition', () => {
    expect(descriptor.componentParents).toEqual([STATE_MACHINE_TRANSITION_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under a scope of its own, so the two forms can be labelled apart', () => {
    expect(descriptor.scopeRoot()).toBe(scopeRoot);
    expect(descriptor.i18nKey()).toBe(`${scopeRoot}._self`);
  });

  it('describes the bean and its static params', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['beanName', 'params']);
  });

  it('identifies a row by the bean it names, the schema giving it no id', () => {
    expect(byName('beanName')?.required).toBe(true);
    expect(byName('beanName')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe(BEAN_REF_ID_FIELD);
  });

  it('names the SPI the bean has to implement in its placeholder', () => {
    expect(byName('beanName')?.placeholder).toBe(`Spring bean implementing Transition${beanKind}`);
  });

  it('edits the params as an open key/value map', () => {
    expect(byName('params')?.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES);
  });
});

describe('the two bean reference descriptors', () => {
  // Same layout from one factory, distinct names so that `guards` and `actions` stay two addressable
  // lists rather than one. Were the names equal, a transition could not carry both.
  it('share their shape but not their identity', () => {
    const guard = createGuardRefDescriptor();
    const action = createActionRefDescriptor();

    expect(flatten(guard.attrDescriptors).map((attr) => attr.attrName)).toEqual(flatten(action.attrDescriptors).map((attr) => attr.attrName));
    expect(guard.entityName).not.toBe(action.entityName);
  });
});
