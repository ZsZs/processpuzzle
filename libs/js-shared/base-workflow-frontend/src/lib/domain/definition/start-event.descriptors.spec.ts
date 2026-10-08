import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createStartEventDescriptor } from './start-event.descriptors';
import { EVENT_DEFINITION_ENTITY_NAME, WORKFLOW_ENTITY_NAME, WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME, WORKFLOW_ROLE_DEFINITION_ENTITY_NAME, WORKFLOW_START_EVENT_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createStartEventDescriptor', () => {
  const descriptor = createStartEventDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is an embedded component of the workflow', () => {
    expect(descriptor.entityName).toBe(WORKFLOW_START_EVENT_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([WORKFLOW_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.workflow_start_event');
    expect(descriptor.i18nKey()).toBe('base_workflow.workflow_start_event._self');
  });

  it('describes the identity, the trigger fields and the nested artifacts', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'name', 'startType', 'eventType', 'timerType', 'timerExpression', 'milestoneRef', 'preconditionExpression', 'authorizedRoles', 'payloadMapping', 'requiredArtifacts']);
  });

  // Unlike the workflow's other rows, a start event has a key of its own.
  it('is identified by its author-chosen id', () => {
    expect(byName('id')?.required).toBe(true);
    expect(byName('id')?.isHeading).toBe(true);
    expect(byName('id')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe('id');
  });

  it('requires a start type, offering the four the contract defines', () => {
    expect(byName('startType')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('startType')?.required).toBe(true);
    expect(
      byName('startType')
        ?.getSelectables()
        ?.map((selectable) => selectable.key),
    ).toEqual(['INPUT_ARTIFACT', 'TRIGGERING_EVENT', 'ROLE_DEFINITION', 'TIME_BASED_PRECONDITION']);
  });

  // Cross-feature, by name only: the catalog belongs to base-event, which this library does not import.
  it('picks the event type from the base-event catalog, keeping the payload mapping beside it', () => {
    expect(byName('eventType')?.formControlType).toBe(FormControlType.FOREIGN_KEY);
    expect(byName('eventType')?.linkedEntityType).toBe(EVENT_DEFINITION_ENTITY_NAME);
    expect(EVENT_DEFINITION_ENTITY_NAME).toBe('Event Definition');
    expect(byName('payloadMapping')?.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES);
  });

  it('picks the authorized roles from the role catalog', () => {
    expect(byName('authorizedRoles')?.formControlType).toBe(FormControlType.RELATED_ENTITIES);
    expect(byName('authorizedRoles')?.linkedEntityType).toBe(WORKFLOW_ROLE_DEFINITION_ENTITY_NAME);
    expect(byName('payloadMapping')?.formControlType).toBe(FormControlType.ADDITIONAL_PROPERTIES);
  });

  // Nested one level further down; a required artifact has no id, so the artifact it names addresses it.
  it('nests its required artifacts as an embedded list', () => {
    expect(byName('requiredArtifacts')?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName('requiredArtifacts')?.linkedEntityType).toBe(WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME);
    expect(byName('requiredArtifacts')?.referenceIdField).toBe('artifactDefinitionId');
    expect(descriptor.embeddedAttrFor(WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME)?.attrName).toBe('requiredArtifacts');
  });

  // A scheduled start needs a moment; a DURATION would have nothing to be relative to.
  it('offers a date or a cycle as the timer of a scheduled start', () => {
    expect(
      byName('timerType')
        ?.getSelectables()
        ?.map((selectable) => selectable.key),
    ).toEqual(['DATE', 'CYCLE']);
    expect(byName('timerExpression')?.formControlType).toBe(FormControlType.TEXT_BOX);
  });

  it('shows only the identity and the type in the table', () => {
    expect(attrs.filter((attr) => !attr.hideInTable).map((attr) => attr.attrName)).toEqual(['id', 'name', 'startType']);
  });
});
