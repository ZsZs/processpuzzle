import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { createArtifactDefinitionDescriptor } from './artifact-definition.descriptors';
import { ARTIFACT_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';

function flatten(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flatten(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createArtifactDefinitionDescriptor', () => {
  const descriptor = createArtifactDefinitionDescriptor();
  const attrs = flatten(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  // A catalog aggregate, and the rename the reference model brought with it: what used to be a
  // `Work Product Definition` embedded in one workflow is an `Artifact Definition` of the tenant.
  it('is a standalone aggregate under its new name', () => {
    expect(descriptor.entityName).toBe(ARTIFACT_DEFINITION_ENTITY_NAME);
    expect(descriptor.entityName).toBe('Artifact Definition');
    expect(descriptor.componentParents).toEqual([]);
    expect(descriptor.isEmbedded).toBeFalsy();
  });

  it('roots the labels under the library scope', () => {
    expect(descriptor.scopeRoot()).toBe('base_workflow.artifact_definition');
  });

  it('describes the artifact, its cross-feature bindings and its revision', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual(['id', 'name', 'artifactType', 'artifactTypeId', 'stateMachineId', 'version', 'updatedAt', 'description']);
  });

  it('is identified by its own id, which a task’s inputs and outputs name', () => {
    expect(descriptor.componentIdentification()).toBe('id');
    expect(byName('id')?.required).toBe(true);
    expect(byName('id')?.isHeading).toBe(true);
    expect(byName('id')?.isLinkToDetails).toBe(true);
  });

  // Required and a closed list by contract: the type is what says which SPEM kind of artifact this is.
  // Asserted under the contract's own field name: the descriptor called it `type` while the schema and
  // the backend column both say `artifactType`, so the value never reached the server.
  it('offers the contract types as a required closed list', () => {
    expect(byName('artifactType')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('artifactType')?.required).toBe(true);
    expect(byName('artifactType')?.getSelectables()?.map((selectable) => selectable.key)).toEqual(['DOCUMENT', 'ENTITY', 'WIDGET']);
  });

  // Both name resources owned by other features, and this library has a store for neither — the
  // contract is explicit that base-workflow never duplicates another feature's model.
  it('leaves both cross-feature bindings as plain text rather than pickers', () => {
    expect(byName('artifactTypeId')?.formControlType).toBe(FormControlType.TEXT_BOX);
    expect(byName('artifactTypeId')?.linkedEntityType).toBeUndefined();
    expect(byName('stateMachineId')?.formControlType).toBe(FormControlType.TEXT_BOX);
    expect(byName('stateMachineId')?.linkedEntityType).toBeUndefined();
  });

  it('shows the server-assigned fields without letting them be edited', () => {
    expect(byName('version')?.disabled).toBe(true);
    expect(byName('updatedAt')?.disabled).toBe(true);
  });
});
