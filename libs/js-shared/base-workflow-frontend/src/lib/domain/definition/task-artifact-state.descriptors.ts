import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { WORKFLOW_TASK_ARTIFACT_STATE_I18N_SCOPE } from '../../base-workflow.i18n';
import { ARTIFACT_DEFINITION_ENTITY_NAME, WORKFLOW_TASK_ARTIFACT_STATE_ENTITY_NAME, WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME } from '../workflow-entity-names';

export { WORKFLOW_TASK_ARTIFACT_STATE_ENTITY_NAME };

/**
 * A `TaskArtifactState` has no `id` — the artifact it names is what identifies it within the task
 * assignment. The referencing attribute therefore has to set `referenceIdField`; see the
 * `artifactStates` attribute of the `Workflow Task Assignment` descriptor.
 */
export const WORKFLOW_TASK_ARTIFACT_STATE_ID_FIELD = 'artifactDefinitionId';

function createTaskArtifactStateAttrDescriptors(): AbstractAttrDescriptor[] {
  // A real reference, like a required start artifact's. The picker offers every artifact of the tenant;
  // the backend refuses one that is none of the task definition's inputs or outputs.
  const artifactDefinitionIdAttr = new BaseEntityAttrDescriptor('artifactDefinitionId', FormControlType.FOREIGN_KEY, 'Artifact', undefined, true);
  artifactDefinitionIdAttr.linkedEntityType = ARTIFACT_DEFINITION_ENTITY_NAME;
  artifactDefinitionIdAttr.required = true;
  artifactDefinitionIdAttr.isHeading = true;

  // Plain text for the reason a required start artifact's `state` is: base-state names the states and
  // base-workflow records the names without resolving them. Each is optional, and the backend refuses
  // one on the wrong side — an input state for an artifact the task does not read, and vice versa.
  const inputStateAttr = new BaseEntityAttrDescriptor('inputState', FormControlType.TEXT_BOX, 'Input State');
  inputStateAttr.placeholder = 'State the task expects the input in, e.g. DRAFT';

  const outputStateAttr = new BaseEntityAttrDescriptor('outputState', FormControlType.TEXT_BOX, 'Output State');
  outputStateAttr.placeholder = 'State the task leaves the output in, e.g. CONFIRMED';

  const flexBoxContainer = new FlexboxDescriptor([artifactDefinitionIdAttr, inputStateAttr, outputStateAttr], FlexDirection.ROW);
  flexBoxContainer.style = { 'column-gap': '10px', width: 'fit-content' };
  return [flexBoxContainer];
}

/**
 * The state one task of a workflow expects an input artifact in, or leaves an output artifact in.
 *
 * Embedded in the task assignment — two levels below the workflow — because the state is true of the task
 * only in this workflow, and an entity of its own because `artifactStates` is a list edited row by row:
 * `workflow/<id>/details/workflow-task-assignment/<taskId>/details/workflow-task-artifact-state/<artifactId>/details`.
 */
export function createTaskArtifactStateDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WORKFLOW_TASK_ARTIFACT_STATE_ENTITY_NAME,
    attrDescriptors: createTaskArtifactStateAttrDescriptors(),
    i18nScope: WORKFLOW_TASK_ARTIFACT_STATE_I18N_SCOPE,
    componentParent: WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME,
    isEmbedded: true,
  });
}
