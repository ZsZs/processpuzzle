import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { ARTIFACT_INSTANCE_ID_FIELD, createArtifactInstanceDescriptor } from './artifact-instance.descriptors';
import { ARTIFACT_INSTANCE_ENTITY_NAME, PROCESS_INSTANCE_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createArtifactInstanceDescriptor', () => {
  const descriptor = createArtifactInstanceDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('is an embedded, read-only component of the process instance', () => {
    expect(descriptor.entityName).toBe(ARTIFACT_INSTANCE_ENTITY_NAME);
    expect(descriptor.componentParents).toEqual([PROCESS_INSTANCE_ENTITY_NAME]);
    expect(descriptor.isEmbedded).toBe(true);
    expect(descriptor.isAbstract).toBe(true);
    expect(attrs.every((attr) => attr.disabled)).toBe(true);
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.artifact_instance');
  });

  it('describes the artifact, where it stands and the two features it points into', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['name', 'type', 'currentState', 'id', 'artifactDefinitionId', 'entityId', 'stateMachineInstanceId', 'updatedAt']);
  });

  it('opens the details by name and is addressed by its id from the owning instance', () => {
    expect(byName('name')?.isLinkToDetails).toBe(true);
    expect(descriptor.componentIdentification()).toBe('name');
    expect(ARTIFACT_INSTANCE_ID_FIELD).toBe('id');
  });

  // Cached from base-state, refreshed when base-workflow sees EntityStateChangedEvent. Where each artifact
  // stands *is* the progress of the run, so it earns a column.
  it('shows the cached current state in the table', () => {
    expect(byName('currentState')?.formControlType).toBe(FormControlType.TEXT_BOX);
    expect(byName('currentState')?.hideInTable).toBe(false);
  });

  it('offers the artifact-type list as a dropdown, sharing the definition’s enum', () => {
    expect(byName('type')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('type')?.getSelectables()?.map((selectable) => selectable.value)).toEqual(['ARTIFACT', 'DELIVERABLE', 'OUTCOME', 'ENTITY']);
  });

  it('declares no embedded children — an artifact instance is a leaf', () => {
    expect(descriptor.embeddedAttrDescriptors()).toEqual([]);
  });
});
