import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { WORKFLOW_ROLE_DEFINITION_I18N_SCOPE } from '../../base-workflow.i18n';
import { ARTIFACT_DEFINITION_ENTITY_NAME, WORKFLOW_ROLE_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';

export { WORKFLOW_ROLE_DEFINITION_ENTITY_NAME };

function createRoleDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  // The business key *and* the record's identity: `/roles/{roleId}` addresses a role by the
  // author-chosen id, which is what the generic screens address a record by too. `isLinkToDetails`, so
  // the list's own column opens the role, and `isHeading`, so the form and the status bar name it.
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique per organization, e.g. developer';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // Association, not containment: an artifact is a catalog aggregate of its own and outlives any role
  // that owns it, so the rows are picked from the artifact's own list and removing one only detaches
  // the reference. Ownership of the outcome, which is a different statement from the read/write access
  // a task's `inputs` and `outputs` grant. `RoleDefinitionMapper` flattens the control's picks to ids.
  const responsibleForAttr = new BaseEntityAttrDescriptor('responsibleFor', FormControlType.RELATED_ENTITIES, 'Responsible For');
  responsibleForAttr.linkedEntityType = ARTIFACT_DEFINITION_ENTITY_NAME;
  responsibleForAttr.hideInTable = true;

  // Plain text rather than a reference: base-entity's role registry is a *tenant's* data, which this
  // library has no store for — base-workflow references it by id only, as the contract's opening note
  // says of every cross-feature link. The backend validates membership when a task is assigned.
  const entityRoleIdAttr = new BaseEntityAttrDescriptor('entityRoleId', FormControlType.TEXT_BOX, 'Entity Role');
  entityRoleIdAttr.placeholder = 'Matching role in base-entity; empty means unchecked';

  // Server-assigned: shown so the author can see which revision is on screen, never edited here.
  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;

  const updatedAtAttr = new BaseEntityAttrDescriptor('updatedAt', FormControlType.TEXT_BOX, 'Updated At');
  updatedAtAttr.disabled = true;

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const revisionRow = new FlexboxDescriptor([entityRoleIdAttr, versionAttr, updatedAtAttr], FlexDirection.ROW);
  revisionRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, revisionRow, descriptionAttr, responsibleForAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/**
 * A catalog aggregate with a list and a details screen of its own — no `componentParent`, no
 * `isEmbedded`. A role is authored once per tenant and referenced by every workflow that involves it,
 * so it outlives any one workflow's edit.
 */
export function createRoleDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WORKFLOW_ROLE_DEFINITION_ENTITY_NAME,
    attrDescriptors: createRoleDefinitionAttrDescriptors(),
    i18nScope: WORKFLOW_ROLE_DEFINITION_I18N_SCOPE,
  });
}
