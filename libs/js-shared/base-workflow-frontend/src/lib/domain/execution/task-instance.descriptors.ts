import { AbstractAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { TASK_INSTANCE_I18N_SCOPE } from '../../base-workflow.i18n';
import { PROCESS_INSTANCE_ENTITY_NAME, TASK_INSTANCE_ENTITY_NAME, TASK_STEP_RESULT_ENTITY_NAME } from '../workflow-entity-names';
import { TaskInstanceStatus } from './process-instance';
import { readOnlyAttr } from './read-only-attr';
import { TASK_STEP_RESULT_ID_FIELD } from './step-result.descriptors';

export { TASK_INSTANCE_ENTITY_NAME };

/** A `TaskInstance` is identified by its own server-minted `id`. */
export const TASK_INSTANCE_ID_FIELD = 'id';

const taskInstanceStatusSelectables = toSelectables(Object.keys(TaskInstanceStatus));

function createTaskInstanceAttrDescriptors(): AbstractAttrDescriptor[] {
  // `name` is the heading rather than `id`: an instance id is a UUID, and the name — copied from the
  // definition when the instance was created — is what a monitor recognises a row by.
  const nameAttr = readOnlyAttr('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.isHeading = true;

  const statusAttr = readOnlyAttr('status', FormControlType.DROPDOWN, 'Status', taskInstanceStatusSelectables);

  const assignedToAttr = readOnlyAttr('assignedTo', FormControlType.TEXT_BOX, 'Assigned To');

  const idAttr = readOnlyAttr('id', FormControlType.TEXT_BOX, 'Id');
  idAttr.hideInTable = true;

  const taskDefinitionIdAttr = readOnlyAttr('taskDefinitionId', FormControlType.TEXT_BOX, 'Task');
  taskDefinitionIdAttr.hideInTable = true;

  // Only set while BLOCKED, and then it is the whole story: the detail of the precondition rule that
  // refused activation. Shown in the table because it is the one field that explains a stuck process.
  const blockedReasonAttr = readOnlyAttr('blockedReason', FormControlType.TEXTAREA, 'Blocked Reason');
  blockedReasonAttr.styleClass = 'full-width';

  const activatedAtAttr = readOnlyAttr('activatedAt', FormControlType.TEXT_BOX, 'Activated At');
  activatedAtAttr.hideInTable = true;
  const completedAtAttr = readOnlyAttr('completedAt', FormControlType.TEXT_BOX, 'Completed At');
  completedAtAttr.hideInTable = true;
  const skippedAtAttr = readOnlyAttr('skippedAt', FormControlType.TEXT_BOX, 'Skipped At');
  skippedAtAttr.hideInTable = true;

  // Containment: the contract nests the step results inside the task instance, and the task instance
  // inside the process instance, so these rows travel inside the *instance's* payload.
  const stepResultsAttr = readOnlyAttr('stepResults', FormControlType.EMBEDDED_COMPONENTS, 'Step Results');
  stepResultsAttr.linkedEntityType = TASK_STEP_RESULT_ENTITY_NAME;
  stepResultsAttr.referenceIdField = TASK_STEP_RESULT_ID_FIELD;
  stepResultsAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([nameAttr, statusAttr, assignedToAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const referenceRow = new FlexboxDescriptor([idAttr, taskDefinitionIdAttr], FlexDirection.ROW);
  referenceRow.style = { 'column-gap': '10px' };
  const timestampRow = new FlexboxDescriptor([activatedAtAttr, completedAtAttr, skippedAtAttr], FlexDirection.ROW);
  timestampRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, referenceRow, timestampRow, blockedReasonAttr, stepResultsAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createTaskInstanceDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: TASK_INSTANCE_ENTITY_NAME,
    attrDescriptors: createTaskInstanceAttrDescriptors(),
    i18nScope: TASK_INSTANCE_I18N_SCOPE,
    componentParent: PROCESS_INSTANCE_ENTITY_NAME,
    isEmbedded: true,
    // Read-only by contract: a task changes through /assign, /complete and /skip, never through a PUT.
    isAbstract: true,
  });
}
