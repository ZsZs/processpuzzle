import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createTaskArtifactStateDescriptor, WORKFLOW_TASK_ARTIFACT_STATE_ID_FIELD } from './task-artifact-state.descriptors';
import { ARTIFACT_DEFINITION_ENTITY_NAME, WORKFLOW_TASK_ARTIFACT_STATE_ENTITY_NAME, WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createTaskArtifactStateDescriptor', () => {
  const descriptor = createTaskArtifactStateDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  // Embedded in the task assignment, because the state is true of the task only in this workflow.
  it('is an embedded component of the task assignment', () => {
    expect(descriptor.entityName).toBe(WORKFLOW_TASK_ARTIFACT_STATE_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.workflow_task_artifact_state');
  });

  it('describes the artifact, the state it is read in and the state it is left in', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['artifactDefinitionId', 'inputState', 'outputState']);
  });

  it('references the artifact catalog as a navigable foreign key', () => {
    expect(byName('artifactDefinitionId')?.formControlType).toBe(FormControlType.FOREIGN_KEY);
    expect(byName('artifactDefinitionId')?.linkedEntityType).toBe(ARTIFACT_DEFINITION_ENTITY_NAME);
    expect(byName('artifactDefinitionId')?.required).toBe(true);
  });

  it('is identified by the artifact it names', () => {
    expect(descriptor.componentIdentification()).toBe(WORKFLOW_TASK_ARTIFACT_STATE_ID_FIELD);
    expect(WORKFLOW_TASK_ARTIFACT_STATE_ID_FIELD).toBe('artifactDefinitionId');
    expect(byName('artifactDefinitionId')?.isHeading).toBe(true);
    expect(byName('artifactDefinitionId')?.isLinkToDetails).toBe(true);
  });

  it('leaves both states as optional plain text, base-state owning the names', () => {
    ['inputState', 'outputState'].forEach((attrName) => {
      expect(byName(attrName)?.formControlType).toBe(FormControlType.TEXT_BOX);
      expect(byName(attrName)?.linkedEntityType).toBeUndefined();
      expect(byName(attrName)?.required).toBeFalsy();
    });
  });
});
