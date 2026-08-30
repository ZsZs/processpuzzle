import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { WORKFLOW_TASK_ASSIGNMENT_I18N_SCOPE } from '../../base-workflow.i18n';
import { WORKFLOW_ENTITY_NAME, WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME, TASK_DEFINITION_ENTITY_NAME, WORKFLOW_ROLE_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';

export { WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME };

/**
 * A `WorkflowTaskAssignment` has no `id` — the task it assigns is what identifies it within the
 * workflow, which is `taskDefinitionId`, and the contract's rule that a task appears at most once per
 * workflow is what makes that unique. The referencing attribute therefore has to set
 * `referenceIdField`; see the `tasks` attribute of the `Workflow` descriptor. Same
 * arrangement as {@link TASK_IO_REFERENCE_ID_FIELD}.
 */
export const WORKFLOW_TASK_ASSIGNMENT_ID_FIELD = 'taskDefinitionId';

function createWorkflowTaskAssignmentAttrDescriptors(): AbstractAttrDescriptor[] {
  // A real reference: the task is a catalog aggregate with a store of its own, so the framework can
  // resolve its display name and navigate to it. `isHeading` and `isLinkToDetails`, because the task
  // is what this row *is* — there is no separate name to head it with.
  const taskDefinitionIdAttr = new BaseEntityAttrDescriptor('taskDefinitionId', FormControlType.FOREIGN_KEY, 'Task', undefined, true);
  taskDefinitionIdAttr.linkedEntityType = TASK_DEFINITION_ENTITY_NAME;
  taskDefinitionIdAttr.required = true;
  taskDefinitionIdAttr.isHeading = true;

  // The one role that performs the task here. The backend refuses a role outside this workflow's
  // `roles` or outside the task's own `performedByRoles` — the task says who is able to perform it,
  // this row says who does — so the picker offering every role of the tenant is wider than the rule.
  const performedByAttr = new BaseEntityAttrDescriptor('performedBy', FormControlType.FOREIGN_KEY, 'Performed By');
  performedByAttr.linkedEntityType = WORKFLOW_ROLE_DEFINITION_ENTITY_NAME;
  performedByAttr.required = true;

  // A chip list, not a picker: `dependsOn` names sibling *rows of the list being edited in the same
  // form*, so any closed option list would be stale the moment an assignment is added. The backend
  // resolves every id on save. Same call as base-state's `initialStateKey`.
  const dependsOnAttr = new BaseEntityAttrDescriptor('dependsOn', FormControlType.TAGS, 'Depends On');
  dependsOnAttr.placeholder = 'Task ids that must complete first; empty means eligible at start';
  dependsOnAttr.hideInTable = true;

  // Both flags are shown in the table: whether a task runs concurrently and whether it replaces an
  // inherited one is the shape of the workflow's control flow, and reading it off the list beats
  // opening every form.
  const parallelAttr = new BaseEntityAttrDescriptor('parallel', FormControlType.CHECKBOX, 'Parallel');
  const overrideAttr = new BaseEntityAttrDescriptor('override', FormControlType.CHECKBOX, 'Override');

  const identityRow = new FlexboxDescriptor([taskDefinitionIdAttr, performedByAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const flowRow = new FlexboxDescriptor([dependsOnAttr, parallelAttr, overrideAttr], FlexDirection.ROW);
  flowRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, flowRow], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/**
 * The one embedded row of a workflow definition, and the only place the reference model puts
 * per-workflow wiring.
 *
 * Embedded rather than a resource of its own because an assignment has no meaning outside the workflow
 * making it: it travels inside the workflow's payload and is addressed through it —
 * `workflow/order-fulfillment-workflow/details/workflow-task-assignment/review-order/details`.
 */
export function createWorkflowTaskAssignmentDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME,
    attrDescriptors: createWorkflowTaskAssignmentAttrDescriptors(),
    i18nScope: WORKFLOW_TASK_ASSIGNMENT_I18N_SCOPE,
    componentParent: WORKFLOW_ENTITY_NAME,
    isEmbedded: true,
  });
}
