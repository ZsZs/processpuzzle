import { AbstractAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { ARTIFACT_INSTANCE_I18N_SCOPE } from '../../base-workflow.i18n';
import { ArtifactType } from '../definition/artifact-definition';
import { ARTIFACT_INSTANCE_ENTITY_NAME, PROCESS_INSTANCE_ENTITY_NAME } from '../workflow-entity-names';
import { readOnlyAttr } from './read-only-attr';

export { ARTIFACT_INSTANCE_ENTITY_NAME };

/** An `ArtifactInstance` is identified by its own server-minted `id`. */
export const ARTIFACT_INSTANCE_ID_FIELD = 'id';

const artifactTypeSelectables = toSelectables(Object.keys(ArtifactType));

function createArtifactInstanceAttrDescriptors(): AbstractAttrDescriptor[] {
  const nameAttr = readOnlyAttr('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.isHeading = true;

  const typeAttr = readOnlyAttr('type', FormControlType.DROPDOWN, 'Type', artifactTypeSelectables);

  // Cached from base-state, refreshed when base-workflow sees `EntityStateChangedEvent`. Shown in the
  // table because where each artifact stands *is* the progress of the process; base-state stays the
  // authority, and `stateMachineInstanceId` below is how a caller asks it.
  const currentStateAttr = readOnlyAttr('currentState', FormControlType.TEXT_BOX, 'Current State');

  const idAttr = readOnlyAttr('id', FormControlType.TEXT_BOX, 'Id');
  idAttr.hideInTable = true;

  const artifactDefinitionIdAttr = readOnlyAttr('artifactDefinitionId', FormControlType.TEXT_BOX, 'Artifact');
  artifactDefinitionIdAttr.hideInTable = true;

  // Both are references into other features, by id only — base-entity's data and base-state's machine.
  const entityIdAttr = readOnlyAttr('entityId', FormControlType.TEXT_BOX, 'Entity');
  const stateMachineInstanceIdAttr = readOnlyAttr('stateMachineInstanceId', FormControlType.TEXT_BOX, 'State Machine Instance');
  stateMachineInstanceIdAttr.hideInTable = true;

  const updatedAtAttr = readOnlyAttr('updatedAt', FormControlType.TEXT_BOX, 'Updated At');
  updatedAtAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([nameAttr, typeAttr, currentStateAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const referenceRow = new FlexboxDescriptor([idAttr, artifactDefinitionIdAttr], FlexDirection.ROW);
  referenceRow.style = { 'column-gap': '10px' };
  const linkRow = new FlexboxDescriptor([entityIdAttr, stateMachineInstanceIdAttr, updatedAtAttr], FlexDirection.ROW);
  linkRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, referenceRow, linkRow], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createArtifactInstanceDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: ARTIFACT_INSTANCE_ENTITY_NAME,
    attrDescriptors: createArtifactInstanceAttrDescriptors(),
    i18nScope: ARTIFACT_INSTANCE_I18N_SCOPE,
    componentParent: PROCESS_INSTANCE_ENTITY_NAME,
    isEmbedded: true,
    // Read-only by contract: base-workflow only stores the references, it never writes an artifact.
    isAbstract: true,
  });
}
