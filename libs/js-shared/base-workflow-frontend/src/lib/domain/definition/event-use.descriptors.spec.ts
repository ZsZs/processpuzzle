import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createEventUseDescriptor } from './event-use.descriptors';
import { EVENT_DEFINITION_ENTITY_NAME, WORKFLOW_ENTITY_NAME, WORKFLOW_EVENT_USE_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createEventUseDescriptor', () => {
  const descriptor = createEventUseDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is an embedded component of the workflow', () => {
    expect(descriptor.entityName).toBe(WORKFLOW_EVENT_USE_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([WORKFLOW_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.workflow_event_use');
    expect(descriptor.i18nKey()).toBe('base_workflow.workflow_event_use._self');
  });

  it('describes the identity, the flow wiring and the mapping', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'name', 'direction', 'eventDefinitionId', 'dependsOn', 'joinType', 'correlationKey', 'timerType', 'timerExpression', 'attachedTo', 'interrupting', 'payloadMapping']);
  });

  // Like a start event, and unlike the workflow's other rows, an event has a key of its own.
  it('is identified by its author-chosen id', () => {
    expect(byName('id')?.required).toBe(true);
    expect(byName('id')?.isHeading).toBe(true);
    expect(byName('id')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe('id');
  });

  it('requires a direction, offering throw and catch', () => {
    expect(byName('direction')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('direction')?.required).toBe(true);
    expect(
      byName('direction')
        ?.getSelectables()
        ?.map((selectable) => selectable.key),
    ).toEqual(['THROW', 'CATCH']);
  });

  // Cross-feature, by name only: the catalog belongs to base-event. Optional, since a timer catch names none.
  it('picks the event from the base-event catalog', () => {
    expect(byName('eventDefinitionId')?.formControlType).toBe(FormControlType.FOREIGN_KEY);
    expect(byName('eventDefinitionId')?.linkedEntityType).toBe(EVENT_DEFINITION_ENTITY_NAME);
    expect(byName('eventDefinitionId')?.required).toBeFalsy();
  });

  it('flattens the timer into a type and an expression', () => {
    expect(byName('timerType')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(
      byName('timerType')
        ?.getSelectables()
        ?.map((selectable) => selectable.key),
    ).toEqual(['DURATION', 'DATE', 'CYCLE']);
    expect(byName('timerExpression')?.formControlType).toBe(FormControlType.TEXT_BOX);
  });

  it('attaches the event to a task by id, interrupting it or not', () => {
    expect(byName('attachedTo')?.formControlType).toBe(FormControlType.TEXT_BOX);
    expect(byName('interrupting')?.formControlType).toBe(FormControlType.CHECKBOX);
  });

  it('wires the event into the flow like a task assignment', () => {
    expect(byName('dependsOn')?.formControlType).toBe(FormControlType.TAGS);
    expect(byName('joinType')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('payloadMapping')?.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES);
  });

  it('shows the identity, the correlation key and the task it is attached to in the table', () => {
    expect(attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName)).toEqual(['id', 'name', 'direction', 'eventDefinitionId', 'correlationKey', 'attachedTo']);
  });
});
