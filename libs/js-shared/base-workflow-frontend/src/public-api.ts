/*
 * Public API Surface of @processpuzzle/base-workflow
 */

// region models
export { type PropertyMap } from './lib/domain/property-map';
export { toReferenceIds } from './lib/domain/reference-ids';
export { Workflow, WorkflowTaskAssignment } from './lib/domain/definition/workflow';
export { RoleDefinition } from './lib/domain/definition/role-definition';
export { ArtifactDefinition, ArtifactType } from './lib/domain/definition/artifact-definition';
export { ReferenceType, StepDefinition, TaskDefinition, TaskIOReference } from './lib/domain/definition/task-definition';
export { AuthType, HttpMethod, ToolDefinition, ToolOperation, type ToolAuthConfig } from './lib/domain/definition/tool-definition';
export { ArtifactInstance, WorkflowInstance, WorkflowInstanceStatus, StepResult, TaskInstance, TaskInstanceStatus } from './lib/domain/execution/workflow-instance';
// endregion

// region entity names
export {
  ARTIFACT_DEFINITION_ENTITY_NAME,
  ARTIFACT_INSTANCE_ENTITY_NAME,
  WORKFLOW_ENTITY_NAME,
  WORKFLOW_INSTANCE_ENTITY_NAME,
  WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME,
  TASK_DEFINITION_ENTITY_NAME,
  TASK_INPUT_REFERENCE_ENTITY_NAME,
  TASK_INSTANCE_ENTITY_NAME,
  TASK_OUTPUT_REFERENCE_ENTITY_NAME,
  TASK_STEP_DEFINITION_ENTITY_NAME,
  TASK_STEP_RESULT_ENTITY_NAME,
  TOOL_DEFINITION_ENTITY_NAME,
  TOOL_OPERATION_ENTITY_NAME,
  WORKFLOW_ROLE_DEFINITION_ENTITY_NAME,
} from './lib/domain/workflow-entity-names';
// endregion

// region descriptors
export { createWorkflowDescriptor } from './lib/domain/definition/workflow.descriptors';
export { WORKFLOW_TASK_ASSIGNMENT_ID_FIELD, createWorkflowTaskAssignmentDescriptor } from './lib/domain/definition/workflow-task-assignment.descriptors';
export { createRoleDefinitionDescriptor } from './lib/domain/definition/role-definition.descriptors';
export { createArtifactDefinitionDescriptor } from './lib/domain/definition/artifact-definition.descriptors';
export { createTaskDefinitionDescriptor } from './lib/domain/definition/task-definition.descriptors';
export { TASK_IO_REFERENCE_ID_FIELD, createTaskInputReferenceDescriptor, createTaskOutputReferenceDescriptor } from './lib/domain/definition/task-io-reference.descriptors';
export { TASK_STEP_DEFINITION_ID_FIELD, createStepDefinitionDescriptor } from './lib/domain/definition/step-definition.descriptors';
export { createToolDefinitionDescriptor } from './lib/domain/definition/tool-definition.descriptors';
export { TOOL_OPERATION_ID_FIELD, createToolOperationDescriptor } from './lib/domain/definition/tool-operation.descriptors';
export { createWorkflowInstanceDescriptor } from './lib/domain/execution/workflow-instance.descriptors';
export { TASK_INSTANCE_ID_FIELD, createTaskInstanceDescriptor } from './lib/domain/execution/task-instance.descriptors';
export { ARTIFACT_INSTANCE_ID_FIELD, createArtifactInstanceDescriptor } from './lib/domain/execution/artifact-instance.descriptors';
export { TASK_STEP_RESULT_ID_FIELD, createStepResultDescriptor } from './lib/domain/execution/step-result.descriptors';
export { readOnlyAttr } from './lib/domain/execution/read-only-attr';
// endregion

// region data access
export { WorkflowMapper } from './lib/domain/definition/workflow.mapper';
export { WorkflowService } from './lib/domain/definition/workflow.service';
export { WorkflowStore } from './lib/domain/definition/workflow.store';
export { RoleDefinitionMapper } from './lib/domain/definition/role-definition.mapper';
export { RoleDefinitionService } from './lib/domain/definition/role-definition.service';
export { RoleDefinitionStore } from './lib/domain/definition/role-definition.store';
export { ArtifactDefinitionMapper } from './lib/domain/definition/artifact-definition.mapper';
export { ArtifactDefinitionService } from './lib/domain/definition/artifact-definition.service';
export { ArtifactDefinitionStore } from './lib/domain/definition/artifact-definition.store';
export { TaskDefinitionMapper } from './lib/domain/definition/task-definition.mapper';
export { TaskDefinitionService } from './lib/domain/definition/task-definition.service';
export { TaskDefinitionStore } from './lib/domain/definition/task-definition.store';
export { ToolDefinitionMapper } from './lib/domain/definition/tool-definition.mapper';
export { ToolDefinitionService } from './lib/domain/definition/tool-definition.service';
export { ToolDefinitionStore } from './lib/domain/definition/tool-definition.store';
export { WorkflowInstanceMapper } from './lib/domain/execution/workflow-instance.mapper';
export { WorkflowInstanceService } from './lib/domain/execution/workflow-instance.service';
export { WorkflowInstanceStore } from './lib/domain/execution/workflow-instance.store';
// endregion

// region facades
export { WorkflowFacade } from './lib/feature/definition/workflow.facade';
export { WorkflowRoleDefinitionFacade } from './lib/feature/definition/role-definition.facade';
export { ArtifactDefinitionFacade } from './lib/feature/definition/artifact-definition.facade';
export { TaskDefinitionFacade } from './lib/feature/definition/task-definition.facade';
export { ToolDefinitionFacade } from './lib/feature/definition/tool-definition.facade';
export { WorkflowTaskAssignmentFacade, TaskInputReferenceFacade, TaskOutputReferenceFacade, TaskStepDefinitionFacade, ToolOperationFacade } from './lib/feature/definition/workflow-embedded.facades';
export { WorkflowInstanceFacade } from './lib/feature/execution/workflow-instance.facade';
export { ArtifactInstanceFacade, TaskInstanceFacade, TaskStepResultFacade } from './lib/feature/execution/instance-embedded.facades';
// endregion

// region application wiring
export {
  ARTIFACT_DEFINITION_I18N_SCOPE,
  ARTIFACT_INSTANCE_I18N_SCOPE,
  BASE_WORKFLOW_TRANSLATION_SOURCE,
  BASE_WORKFLOW_TRANSLOCO_SCOPE,
  WORKFLOW_I18N_SCOPE,
  WORKFLOW_INSTANCE_I18N_SCOPE,
  WORKFLOW_TASK_ASSIGNMENT_I18N_SCOPE,
  TASK_DEFINITION_I18N_SCOPE,
  TASK_INPUT_REFERENCE_I18N_SCOPE,
  TASK_INSTANCE_I18N_SCOPE,
  TASK_OUTPUT_REFERENCE_I18N_SCOPE,
  TASK_STEP_DEFINITION_I18N_SCOPE,
  TASK_STEP_RESULT_I18N_SCOPE,
  TOOL_DEFINITION_I18N_SCOPE,
  TOOL_OPERATION_I18N_SCOPE,
  WORKFLOW_ROLE_DEFINITION_I18N_SCOPE,
} from './lib/base-workflow.i18n';
export { BASE_WORKFLOW_ENTITY_FACADES, BASE_WORKFLOW_FACADE_PROVIDERS } from './lib/base-workflow.providers';
export { BASE_WORKFLOW_ROUTES } from './lib/base-workflow.routes';
// endregion
