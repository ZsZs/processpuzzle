import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { TASK_DEFINITION_I18N_SCOPE } from '../../base-workflow.i18n';
import { ARTIFACT_DEFINITION_ENTITY_NAME, TASK_DEFINITION_ENTITY_NAME, TASK_STEP_DEFINITION_ENTITY_NAME, WORKFLOW_ROLE_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';
import { TASK_STEP_DEFINITION_ID_FIELD } from './step-definition.descriptors';

export { TASK_DEFINITION_ENTITY_NAME };

function createTaskDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique per organization, e.g. write-spec';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // Association, not containment: a role exists independently of this task and outlives its
  // reference, so the rows are picked from the role's own list and removing one only detaches it.
  // The list is who is *able* to perform the task; a workflow pins exactly one of them, on its
  // `Workflow Task Assignment`. `TaskDefinitionMapper` flattens the control's picks back to ids.
  const performedByRolesAttr = new BaseEntityAttrDescriptor('performedByRoles', FormControlType.RELATED_ENTITIES, 'Performed By Roles');
  performedByRolesAttr.linkedEntityType = WORKFLOW_ROLE_DEFINITION_ENTITY_NAME;
  performedByRolesAttr.required = true;
  performedByRolesAttr.hideInTable = true;

  // Ids of base-rule rules, referenced by id only — this library has no rule store. Plain text for
  // the same reason `entityRoleId` is on a role.
  const preconditionRuleIdAttr = new BaseEntityAttrDescriptor('preconditionRuleId', FormControlType.TEXT_BOX, 'Precondition Rule');
  preconditionRuleIdAttr.placeholder = 'base-rule rule guarding activation';
  preconditionRuleIdAttr.hideInTable = true;

  const postconditionRuleIdAttr = new BaseEntityAttrDescriptor('postconditionRuleId', FormControlType.TEXT_BOX, 'Postcondition Rule');
  postconditionRuleIdAttr.placeholder = 'base-rule rule guarding completion';
  postconditionRuleIdAttr.hideInTable = true;

  // Server-assigned: shown so the author can see which revision is on screen, never edited here.
  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;

  const updatedAtAttr = new BaseEntityAttrDescriptor('updatedAt', FormControlType.TEXT_BOX, 'Updated At');
  updatedAtAttr.disabled = true;

  // region what the task reads and writes — association, not containment
  // Artifact definition ids, so a picker over the artifact catalog. Ids rather than typed references,
  // by contract: an artifact's own `artifactType` already says whether it is an entity, a document or
  // a widget, so there is nothing left for a per-reference `type` to add. An artifact outlives any
  // task that names it, and removing a row only detaches the reference.
  //
  // The backend additionally refuses an artifact the referencing workflow has not declared in its own
  // `artifacts` — a rule about a workflow, which a task authored on its own cannot check, so the
  // picker offers the whole catalog.
  const inputsAttr = new BaseEntityAttrDescriptor('inputs', FormControlType.RELATED_ENTITIES, 'Inputs');
  inputsAttr.linkedEntityType = ARTIFACT_DEFINITION_ENTITY_NAME;
  inputsAttr.hideInTable = true;

  const outputsAttr = new BaseEntityAttrDescriptor('outputs', FormControlType.RELATED_ENTITIES, 'Outputs');
  outputsAttr.linkedEntityType = ARTIFACT_DEFINITION_ENTITY_NAME;
  outputsAttr.hideInTable = true;
  // endregion

  // region the steps — kept on the entity, so the full-replacement PUT preserves them
  // Containment: the contract nests a step inside the task that owns it and gives it no endpoint of
  // its own, so these rows travel inside this entity's payload and are addressed through it —
  // `task-definition/review-order/details/task-step-definition/check-items/details`.
  const stepsAttr = new BaseEntityAttrDescriptor('steps', FormControlType.EMBEDDED_COMPONENTS, 'Steps');
  stepsAttr.linkedEntityType = TASK_STEP_DEFINITION_ENTITY_NAME;
  stepsAttr.referenceIdField = TASK_STEP_DEFINITION_ID_FIELD;
  stepsAttr.hideInTable = true;
  // endregion

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const guardRow = new FlexboxDescriptor([preconditionRuleIdAttr, postconditionRuleIdAttr], FlexDirection.ROW);
  guardRow.style = { 'column-gap': '10px' };
  const revisionRow = new FlexboxDescriptor([versionAttr, updatedAtAttr], FlexDirection.ROW);
  revisionRow.style = { 'column-gap': '10px' };
  const referenceRow = new FlexboxDescriptor([performedByRolesAttr, inputsAttr, outputsAttr], FlexDirection.ROW);
  referenceRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, guardRow, revisionRow, descriptionAttr, referenceRow, stepsAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/**
 * A catalog aggregate with a list and a details screen of its own.
 *
 * What a task deliberately does *not* describe is where it sits in a workflow: `dependsOn`, `parallel`
 * and `override` all name siblings of one workflow, so they live on that workflow's
 * `Workflow Task Assignment` instead. Authoring a task here therefore puts it in no workflow — the
 * workflow picks it up by referencing its id.
 */
export function createTaskDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: TASK_DEFINITION_ENTITY_NAME,
    attrDescriptors: createTaskDefinitionAttrDescriptors(),
    i18nScope: TASK_DEFINITION_I18N_SCOPE,
  });
}
