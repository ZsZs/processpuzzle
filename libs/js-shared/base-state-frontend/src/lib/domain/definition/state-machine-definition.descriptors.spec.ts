import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import {
  STATE_MACHINE_DEFINITION_ENTITY_NAME,
  STATE_MACHINE_STATE_ENTITY_NAME,
  STATE_MACHINE_TRANSITION_ENTITY_NAME,
} from './state-entity-names';
import { createStateMachineDefinitionDescriptor } from './state-machine-definition.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createStateMachineDefinitionDescriptor', () => {
  const descriptor = createStateMachineDefinitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity so that the route segment follows from it', () => {
    expect(descriptor.entityName).toBe(STATE_MACHINE_DEFINITION_ENTITY_NAME);
  });

  it('is a routable aggregate, not a component of anything', () => {
    expect(descriptor.componentParents).toEqual([]);
    expect(descriptor.isEmbedded).toBe(false);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_state.state_machine_definition');
    expect(descriptor.i18nKey()).toBe('base_state.state_machine_definition._self');
    expect(byName('states')?.i18nKey()).toBe('base_state.state_machine_definition.states');
  });

  it('describes the machine, what it binds to and the graph it contains', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['entityName', 'name', 'stateAttributeKey', 'initialStateKey', 'version', 'updatedAt', 'description', 'states', 'transitions']);
  });

  // The machine is addressed by the entity it governs, so that column is both the business key and the
  // link into the form; `id` is its mirror and deliberately has no control of its own.
  it('identifies a machine by the entity it governs', () => {
    expect(byName('entityName')?.required).toBe(true);
    expect(byName('entityName')?.isLinkToDetails).toBe(true);
    expect(byName('entityName')?.isHeading).toBe(true);
    expect(byName('id')).toBeUndefined();
  });

  it('requires the binding the engine needs to work at all', () => {
    expect(byName('stateAttributeKey')?.required).toBe(true);
    expect(byName('initialStateKey')?.required).toBe(true);
  });

  it('shows the server-assigned fields read-only', () => {
    expect(byName('version')?.disabled).toBe(true);
    expect(byName('updatedAt')?.disabled).toBe(true);
  });

  it('contains the states and the transitions, which have no endpoint of their own', () => {
    expect(byName('states')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('states')?.linkedEntityType).toBe(STATE_MACHINE_STATE_ENTITY_NAME);
    expect(byName('transitions')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('transitions')?.linkedEntityType).toBe(STATE_MACHINE_TRANSITION_ENTITY_NAME);
  });

  // Neither row has an `id` in the contract, so the control has to be told which field names it — left
  // at the default `id`, every row would be keyed `undefined` and the details form would open the wrong one.
  it('names the field that identifies a row of each nested list', () => {
    expect(byName('states')?.referenceIdField).toBe('key');
    expect(byName('transitions')?.referenceIdField).toBe('key');
  });

  // The URL segment of an embedded level names the entity, so a child type carried by two attributes
  // could not be addressed; `embeddedAttrFor` throws on that, and this is where it would surface.
  it('carries each embedded child type on exactly one attribute', () => {
    expect(descriptor.embeddedAttrFor(STATE_MACHINE_STATE_ENTITY_NAME)?.attrName).toBe('states');
    expect(descriptor.embeddedAttrFor(STATE_MACHINE_TRANSITION_ENTITY_NAME)?.attrName).toBe('transitions');
  });

  it('keeps the list to what tells two machines apart', () => {
    const tableColumns = attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName);

    expect(tableColumns).toEqual(['entityName', 'name', 'stateAttributeKey', 'initialStateKey', 'version', 'updatedAt']);
  });
});
