import { AbstractAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { WORKFLOW_INSTANCE_I18N_SCOPE } from '../../base-workflow.i18n';
import { ARTIFACT_INSTANCE_ENTITY_NAME, WORKFLOW_INSTANCE_ENTITY_NAME, TASK_INSTANCE_ENTITY_NAME } from '../workflow-entity-names';
import { WorkflowInstanceStatus } from './workflow-instance';
import { readOnlyAttr } from './read-only-attr';
import { TASK_INSTANCE_ID_FIELD } from './task-instance.descriptors';
import { ARTIFACT_INSTANCE_ID_FIELD } from './artifact-instance.descriptors';

export { WORKFLOW_INSTANCE_ENTITY_NAME };

const workflowInstanceStatusSelectables = toSelectables(Object.keys(WorkflowInstanceStatus));

function createWorkflowInstanceAttrDescriptors(): AbstractAttrDescriptor[] {
  // `id` opens the details and is the record's identity — a server-minted UUID — but the *heading* is
  // the definition's name, which is what a monitor recognises a run by. `titleKey` on the descriptor
  // says so for the status bar too, since it would otherwise take the `isLinkToDetails` attribute.
  const idAttr = readOnlyAttr('id', FormControlType.TEXT_BOX, 'Id', undefined, true);

  const workflowNameAttr = readOnlyAttr('workflowName', FormControlType.TEXT_BOX, 'Workflow Name');
  workflowNameAttr.isHeading = true;

  const statusAttr = readOnlyAttr('status', FormControlType.DROPDOWN, 'Status', workflowInstanceStatusSelectables);

  const workflowIdAttr = readOnlyAttr('workflowId', FormControlType.TEXT_BOX, 'Workflow');
  workflowIdAttr.hideInTable = true;

  // The base-entity instance this run was started for, when it was started for one — an order, a
  // claim. By id only, as every cross-feature link in this contract is.
  const entityIdAttr = readOnlyAttr('entityId', FormControlType.TEXT_BOX, 'Entity');

  const startedAtAttr = readOnlyAttr('startedAt', FormControlType.TEXT_BOX, 'Started At');
  const completedAtAttr = readOnlyAttr('completedAt', FormControlType.TEXT_BOX, 'Completed At');

  // Whatever the tool steps have written so far. Open by contract, so an open key/value view is the
  // only shape that can show it.
  const contextAttr = readOnlyAttr('context', FormControlType.ADDITIONAL_PROPERTIES, 'Context');
  contextAttr.hideInTable = true;

  // Containment: the contract nests both lists inside the instance document — `/instances/{id}/tasks`
  // and `/artifacts` exist as read-only sub-resources, but the single GET already carries them, so
  // the rows travel inside this entity's payload and are addressed through it.
  const tasksAttr = readOnlyAttr('tasks', FormControlType.EMBEDDED_COMPONENTS, 'Tasks');
  tasksAttr.linkedEntityType = TASK_INSTANCE_ENTITY_NAME;
  tasksAttr.referenceIdField = TASK_INSTANCE_ID_FIELD;
  tasksAttr.hideInTable = true;

  const artifactsAttr = readOnlyAttr('artifacts', FormControlType.EMBEDDED_COMPONENTS, 'Artifacts');
  artifactsAttr.linkedEntityType = ARTIFACT_INSTANCE_ENTITY_NAME;
  artifactsAttr.referenceIdField = ARTIFACT_INSTANCE_ID_FIELD;
  artifactsAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([workflowNameAttr, statusAttr, entityIdAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const referenceRow = new FlexboxDescriptor([idAttr, workflowIdAttr], FlexDirection.ROW);
  referenceRow.style = { 'column-gap': '10px' };
  const timestampRow = new FlexboxDescriptor([startedAtAttr, completedAtAttr], FlexDirection.ROW);
  timestampRow.style = { 'column-gap': '10px' };
  const contentRow = new FlexboxDescriptor([tasksAttr, artifactsAttr], FlexDirection.ROW);
  contentRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, referenceRow, timestampRow, contextAttr, contentRow], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createWorkflowInstanceDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WORKFLOW_INSTANCE_ENTITY_NAME,
    attrDescriptors: createWorkflowInstanceAttrDescriptors(),
    i18nScope: WORKFLOW_INSTANCE_I18N_SCOPE,
    // Names the run in the status bar. Without it the bar would take the `isLinkToDetails` attribute,
    // which here is the UUID.
    titleKey: 'workflowName',
    // Read-only by contract: an instance is started by POST, cancelled by DELETE, and never PUT.
    isAbstract: true,
  });
}
