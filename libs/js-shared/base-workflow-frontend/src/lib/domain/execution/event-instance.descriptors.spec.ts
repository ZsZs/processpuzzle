import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createEventInstanceDescriptor, EVENT_INSTANCE_ID_FIELD } from './event-instance.descriptors';
import { EVENT_DEFINITION_ENTITY_NAME, EVENT_INSTANCE_ENTITY_NAME, WORKFLOW_INSTANCE_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createEventInstanceDescriptor', () => {
  const descriptor = createEventInstanceDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is a read-only embedded component of the instance', () => {
    expect(descriptor.entityName).toBe(EVENT_INSTANCE_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([WORKFLOW_INSTANCE_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
    expect(descriptor.isAbstract).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.i18nKey()).toBe('base_workflow.event_instance._self');
  });

  it('disables every attribute', () => {
    expect(attrs.every((attr) => attr.disabled)).toBe(true);
  });

  it('is identified by its server-minted id and headed by the event it runs', () => {
    expect(EVENT_INSTANCE_ID_FIELD).toBe('id');
    expect(byName('eventUseId')?.isHeading).toBe(true);
  });

  it('offers the five statuses and links the catalog event', () => {
    expect(
      byName('status')
        ?.getSelectables()
        ?.map((selectable) => selectable.key),
    ).toEqual(['PENDING', 'WAITING', 'OCCURRED', 'THROWN', 'CANCELLED']);
    expect(byName('eventDefinitionId')?.formControlType).toBe(FormControlType.FOREIGN_KEY);
    expect(byName('eventDefinitionId')?.linkedEntityType).toBe(EVENT_DEFINITION_ENTITY_NAME);
  });

  it('keeps the payload and the contribution out of the table', () => {
    expect(byName('payload')?.hideInTable).toBe(true);
    expect(byName('contextContribution')?.hideInTable).toBe(true);
  });
});
