import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { TASK_DEFINITION_I18N_SCOPE } from '../../base-workflow.i18n';
import {
  TASK_DEFINITION_ENTITY_NAME,
  TASK_INPUT_REFERENCE_ENTITY_NAME,
  TASK_OUTPUT_REFERENCE_ENTITY_NAME,
  TASK_STEP_DEFINITION_ENTITY_NAME,
  WORKFLOW_ROLE_DEFINITION_ENTITY_NAME,
} from '../workflow-entity-names';
import { TASK_IO_REFERENCE_ID_FIELD } from './task-io-reference.descriptors';
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

  // region nested references and steps — kept on the entity, so the full-replacement PUT preserves them
  // Containment: the contract nests a reference and a step inside the task that owns it and gives
  // neither an endpoint of its own, so these rows travel inside this entity's payload and are
  // addressed through it — `task-definition/review-order/details/task-input-reference/order/details`.
  const inputsAttr = new BaseEntityAttrDescriptor('inputs', FormControlType.EMBEDDED_COMPONENTS, 'Inputs');
  inputsAttr.linkedEntityType = TASK_INPUT_REFERENCE_ENTITY_NAME;
  inputsAttr.referenceIdField = TASK_IO_REFERENCE_ID_FIELD;
  inputsAttr.hideInTable = true;

  const outputsAttr = new BaseEntityAttrDescriptor('outputs', FormControlType.EMBEDDED_COMPONENTS, 'Outputs');
  outputsAttr.linkedEntityType = TASK_OUTPUT_REFERENCE_ENTITY_NAME;
  outputsAttr.referenceIdField = TASK_IO_REFERENCE_ID_FIELD;
  outputsAttr.hideInTable = true;

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
  const contentRow = new FlexboxDescriptor([inputsAttr, outputsAttr, stepsAttr], FlexDirection.ROW);
  contentRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, guardRow, revisionRow, descriptionAttr, performedByRolesAttr, contentRow], FlexDirection.COLUMN);
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
