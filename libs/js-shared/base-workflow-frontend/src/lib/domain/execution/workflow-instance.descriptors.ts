import { AbstractAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { WORKFLOW_INSTANCE_I18N_SCOPE } from '../../base-workflow.i18n';
import { ARTIFACT_INSTANCE_ENTITY_NAME, WORKFLOW_ENTITY_NAME, WORKFLOW_INSTANCE_ENTITY_NAME, TASK_INSTANCE_ENTITY_NAME } from '../workflow-entity-names';
import { WorkflowInstanceStatus } from './workflow-instance';
import { readOnlyAttr } from './read-only-attr';
import { TASK_INSTANCE_ID_FIELD } from './task-instance.descriptors';
import { ARTIFACT_INSTANCE_ID_FIELD } from './artifact-instance.descriptors';
import { timestampAttr } from '../timestamp-attr';

export { WORKFLOW_INSTANCE_ENTITY_NAME };

const workflowInstanceStatusSelectables = toSelectables(Object.keys(WorkflowInstanceStatus));

function createWorkflowInstanceAttrDescriptors(): AbstractAttrDescriptor[] {
  // The run's own identity opens its details: the number the server assigns on creation, 1, 2, 3… per
  // organization. Not the workflow's name — that belongs to the related definition, and every run of it
  // shares it — and not the UUID, which is a database key rather than something a person reads, and so
  // appears neither in the list nor on the form: it is only in the URL.
  const instanceNumberAttr = readOnlyAttr('instanceNumber', FormControlType.TEXT_BOX, 'No.', undefined, true);

  const workflowNameAttr = readOnlyAttr('workflowName', FormControlType.TEXT_BOX, 'Workflow Name');

  const statusAttr = readOnlyAttr('status', FormControlType.DROPDOWN, 'Status', workflowInstanceStatusSelectables);

  // A disabled `FOREIGN_KEY` rather than a text box: the definition this run came from is a routable
  // aggregate with a store of its own, so the control resolves its name and renders a link icon that
  // navigates to it. Disabled suppresses only the select button — `ForeignKeyComponent` early-returns
  // from `navigateToRelatedList()` — so the row stays unmodifiable while becoming navigable, which is
  // what a read-only execution screen wants.
  const workflowIdAttr = readOnlyAttr('workflowId', FormControlType.FOREIGN_KEY, 'Workflow');
  workflowIdAttr.linkedEntityType = WORKFLOW_ENTITY_NAME;
  workflowIdAttr.hideInTable = true;

  // Which of the definition's start events fired this run — by id, the way the contract names it. Empty
  // for a run started explicitly through `/instances` without naming one.
  const startEventIdAttr = readOnlyAttr('startEventId', FormControlType.TEXT_BOX, 'Start Event');
  startEventIdAttr.hideInTable = true;

  // The base-entity instance this run was started for, when it was started for one — an order, a
  // claim. Shown by name: `entityLabel` is resolved by the backend per response (the order number), and
  // falls back to the id when there is no name to give.
  const entityLabelAttr = readOnlyAttr('entityLabel', FormControlType.TEXT_BOX, 'Entity');

  const startedAtAttr = timestampAttr('startedAt', 'Started At');
  const completedAtAttr = timestampAttr('completedAt', 'Completed At');

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

  const identityRow = new FlexboxDescriptor([instanceNumberAttr, workflowNameAttr, statusAttr, entityLabelAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const referenceRow = new FlexboxDescriptor([workflowIdAttr, startEventIdAttr], FlexDirection.ROW);
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
    // Names the run in the form heading and the status bar: `Order Fulfillment Workflow #3`, derived by the
    // model from the definition's name and the run's number.
    titleKey: 'title',
    // Read-only by contract: an instance is started by POST, cancelled by DELETE, and never PUT.
    isAbstract: true,
  });
}
