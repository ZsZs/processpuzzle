import {
  ARTIFACT_DEFINITION_I18N_SCOPE,
  TASK_DEFINITION_I18N_SCOPE,
  TOOL_DEFINITION_I18N_SCOPE,
  WORKFLOW_I18N_SCOPE,
  WORKFLOW_ROLE_DEFINITION_I18N_SCOPE,
} from '../../base-workflow.i18n';
import { WorkflowElementKind } from './workflow-graph';

/**
 * What each element kind is *called*, as a transloco key.
 *
 * The entity's own `_self` key rather than a label of the modeler's own: a node kind and a routable
 * aggregate are the same thing seen twice, so `role` is whatever the Roles list and the Role form already
 * call it — in all five languages, already translated. A `base_workflow.modeler.*` block beside them would
 * be the same five words maintained twice, free to drift.
 */
const ELEMENT_I18N_SCOPES: Record<WorkflowElementKind, string> = {
  role: WORKFLOW_ROLE_DEFINITION_I18N_SCOPE,
  artifact: ARTIFACT_DEFINITION_I18N_SCOPE,
  task: TASK_DEFINITION_I18N_SCOPE,
  tool: TOOL_DEFINITION_I18N_SCOPE,
  workflow: WORKFLOW_I18N_SCOPE,
};

/** The key naming one kind — `base_workflow.workflow_role_definition._self` and its four siblings. */
export function modelerElementNameKey(kind: WorkflowElementKind): string {
  return `${ELEMENT_I18N_SCOPES[kind]}._self`;
}
