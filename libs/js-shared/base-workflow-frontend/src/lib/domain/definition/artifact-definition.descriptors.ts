import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { ARTIFACT_DEFINITION_I18N_SCOPE } from '../../base-workflow.i18n';
import { ArtifactType } from './artifact-definition';
import { ARTIFACT_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';

export { ARTIFACT_DEFINITION_ENTITY_NAME };

const artifactTypeSelectables = toSelectables(Object.keys(ArtifactType));

function createArtifactDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique per organization, e.g. order-entity';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  const artifactTypeAttr = new BaseEntityAttrDescriptor('artifactType', FormControlType.DROPDOWN, 'Artifact Type', artifactTypeSelectables);
  artifactTypeAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // Both name resources owned by *other* features and are referenced by id only — base-entity's
  // entity types, base-document's documents or the widget registry, and base-state's machines. Plain
  // text rather than a picker for the same reason `entityRoleId` is: this library holds no store for
  // any of them, and the contract is explicit that base-workflow never duplicates another feature's
  // model. Which of the three `artifactTypeId` names is decided by the artifact type chosen above,
  // which is a second reason no single picker would do.
  const artifactTypeIdAttr = new BaseEntityAttrDescriptor('artifactTypeId', FormControlType.TEXT_BOX, 'Type Id');
  artifactTypeIdAttr.placeholder = 'The entity, document or widget this artifact is, e.g. order';

  const stateMachineIdAttr = new BaseEntityAttrDescriptor('stateMachineId', FormControlType.TEXT_BOX, 'State Machine');
  stateMachineIdAttr.placeholder = 'base-state machine governing its lifecycle';

  // Server-assigned: shown so the author can see which revision is on screen, never edited here.
  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;

  const updatedAtAttr = new BaseEntityAttrDescriptor('updatedAt', FormControlType.TEXT_BOX, 'Updated At');
  updatedAtAttr.disabled = true;

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr, artifactTypeAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const bindingRow = new FlexboxDescriptor([artifactTypeIdAttr, stateMachineIdAttr], FlexDirection.ROW);
  bindingRow.style = { 'column-gap': '10px' };
  const revisionRow = new FlexboxDescriptor([versionAttr, updatedAtAttr], FlexDirection.ROW);
  revisionRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, bindingRow, revisionRow, descriptionAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/**
 * A catalog aggregate with a list and a details screen of its own. An artifact is authored once per
 * tenant: the same `Fulfillment Invoice` is produced by one workflow and consumed by another, and a
 * task's `inputs` and `outputs` name it by id.
 */
export function createArtifactDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: ARTIFACT_DEFINITION_ENTITY_NAME,
    attrDescriptors: createArtifactDefinitionAttrDescriptors(),
    i18nScope: ARTIFACT_DEFINITION_I18N_SCOPE,
  });
}
