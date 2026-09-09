import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createRequiredStartArtifactDescriptor, WORKFLOW_REQUIRED_START_ARTIFACT_ID_FIELD } from './required-start-artifact.descriptors';
import { ARTIFACT_DEFINITION_ENTITY_NAME, WORKFLOW_ENTITY_NAME, WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createRequiredStartArtifactDescriptor', () => {
  const descriptor = createRequiredStartArtifactDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  // Embedded in the workflow because the start condition is part of the workflow and nothing else. It is
  // an entity of its own only because `requiredArtifacts` is a list; the condition's other six fields are
  // scalars, flattened onto the workflow's own form.
  it('is an embedded component of the workflow', () => {
    expect(descriptor.entityName).toBe(WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([WORKFLOW_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.workflow_required_start_artifact');
  });

  it('describes the artifact and the state it has to be in', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['artifactDefinitionId', 'state']);
  });

  it('references the artifact catalog as a navigable foreign key', () => {
    expect(byName('artifactDefinitionId')?.formControlType).toBe(FormControlType.FOREIGN_KEY);
    expect(byName('artifactDefinitionId')?.linkedEntityType).toBe(ARTIFACT_DEFINITION_ENTITY_NAME);
    expect(byName('artifactDefinitionId')?.required).toBe(true);
  });

  // The contract gives the row no key of its own, so the artifact it names is what addresses it.
  it('is identified by the artifact it names', () => {
    expect(descriptor.componentIdentification()).toBe(WORKFLOW_REQUIRED_START_ARTIFACT_ID_FIELD);
    expect(WORKFLOW_REQUIRED_START_ARTIFACT_ID_FIELD).toBe('artifactDefinitionId');
    expect(byName('artifactDefinitionId')?.isHeading).toBe(true);
    expect(byName('artifactDefinitionId')?.isLinkToDetails).toBe(true);
  });

  // Plain text, not a picker: the state is named by the artifact's base-state machine, and base-workflow
  // records the name without resolving it — there is no store here to offer the states of a machine chosen
  // in another control. Optional, because absent means any state will do.
  it('leaves the state as optional plain text, base-state owning the name', () => {
    expect(byName('state')?.formControlType).toBe(FormControlType.TEXT_BOX);
    expect(byName('state')?.linkedEntityType).toBeUndefined();
    expect(byName('state')?.required).toBeFalsy();
  });
});
