import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType, snakeCaseName } from '@processpuzzle/base-entity';
import { createEventDefinitionDescriptor, EVENT_DEFINITION_ENTITY_NAME } from './event-definition.descriptors';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createEventDefinitionDescriptor', () => {
  const descriptor = createEventDefinitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);
  const keysOf = (attrName: string) => byName(attrName)?.getSelectables()?.map((selectable) => selectable.key);

  // Other features point a FOREIGN_KEY at this name without importing the library.
  it('names the entity other features refer to, and the route segment follows from it', () => {
    expect(EVENT_DEFINITION_ENTITY_NAME).toBe('Event Definition');
    expect(descriptor.entityName).toBe(EVENT_DEFINITION_ENTITY_NAME);
    expect(snakeCaseName(descriptor.entityName)).toBe('event-definition');
  });

  it('is a routable aggregate, not a component of anything', () => {
    expect(descriptor.componentParents).toEqual([]);
    expect(descriptor.isEmbedded).toBe(false);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_event.event_definition');
    expect(descriptor.i18nKey()).toBe('base_event.event_definition._self');
    expect(byName('kind')?.i18nKey()).toBe('base_event.event_definition.kind');
  });

  it('describes the identity, the kind and the subject', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'name', 'kind', 'subjectType', 'action', 'state', 'description']);
  });

  it('identifies a definition by its author-chosen id', () => {
    expect(byName('id')?.required).toBe(true);
    expect(byName('id')?.isLinkToDetails).toBe(true);
    expect(byName('id')?.isHeading).toBe(true);
    expect(byName('name')?.required).toBe(true);
  });

  it('offers the kinds and actions the contract defines', () => {
    expect(byName('kind')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('kind')?.required).toBe(true);
    expect(keysOf('kind')).toEqual(['SYSTEM', 'MESSAGE', 'SIGNAL']);
    expect(byName('action')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(keysOf('action')).toEqual(['CREATED', 'UPDATED', 'DELETED', 'STATE_CHANGED']);
  });

  // Required only for a SYSTEM event, which the form cannot express; the backend validates it.
  it('leaves the subject fields optional', () => {
    ['subjectType', 'action', 'state'].forEach((attrName) => expect(byName(attrName)?.required).toBeFalsy());
  });

  it('keeps the description out of the list', () => {
    expect(attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName)).toEqual(['id', 'name', 'kind', 'subjectType', 'action', 'state']);
  });
});
