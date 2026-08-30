import type { Provider } from '@angular/core';
import type { BaseEntityFacadeRegistry } from '@processpuzzle/base-entity';
import {
  ARTIFACT_DEFINITION_ENTITY_NAME,
  ARTIFACT_INSTANCE_ENTITY_NAME,
  PROCESS_DEFINITION_ENTITY_NAME,
  PROCESS_INSTANCE_ENTITY_NAME,
  PROCESS_TASK_ASSIGNMENT_ENTITY_NAME,
  TASK_DEFINITION_ENTITY_NAME,
  TASK_INPUT_REFERENCE_ENTITY_NAME,
  TASK_INSTANCE_ENTITY_NAME,
  TASK_OUTPUT_REFERENCE_ENTITY_NAME,
  TASK_STEP_DEFINITION_ENTITY_NAME,
  TASK_STEP_RESULT_ENTITY_NAME,
  TOOL_DEFINITION_ENTITY_NAME,
  TOOL_OPERATION_ENTITY_NAME,
  WORKFLOW_ROLE_DEFINITION_ENTITY_NAME,
} from './domain/workflow-entity-names';
import { ArtifactDefinitionFacade } from './feature/definition/artifact-definition.facade';
import { ProcessDefinitionFacade } from './feature/definition/process-definition.facade';
import { WorkflowRoleDefinitionFacade } from './feature/definition/role-definition.facade';
import { TaskDefinitionFacade } from './feature/definition/task-definition.facade';
import { ToolDefinitionFacade } from './feature/definition/tool-definition.facade';
import { ProcessTaskAssignmentFacade, TaskInputReferenceFacade, TaskOutputReferenceFacade, TaskStepDefinitionFacade, ToolOperationFacade } from './feature/definition/workflow-embedded.facades';
import { ProcessInstanceFacade } from './feature/execution/process-instance.facade';
import { ArtifactInstanceFacade, TaskInstanceFacade, TaskStepResultFacade } from './feature/execution/instance-embedded.facades';

/**
 * The facades of the whole workflow graph, to be spread into the application's `providers`.
 *
 * The embedded ones are here for the same reason the five routable ones are: an embedded entity has a
 * facade like any other — that is what gives it a store — and only its repository differs, reading and
 * writing the aggregate's document rather than an endpoint of its own.
 *
 * All fourteen or none: a consuming application cannot register half of a graph whose forms reference
 * each other by entity name.
 */
export const BASE_WORKFLOW_FACADE_PROVIDERS: Provider[] = [
  ProcessDefinitionFacade,
  ProcessTaskAssignmentFacade,
  WorkflowRoleDefinitionFacade,
  ArtifactDefinitionFacade,
  TaskDefinitionFacade,
  TaskInputReferenceFacade,
  TaskOutputReferenceFacade,
  TaskStepDefinitionFacade,
  ToolDefinitionFacade,
  ToolOperationFacade,
  ProcessInstanceFacade,
  TaskInstanceFacade,
  ArtifactInstanceFacade,
  TaskStepResultFacade,
];

/**
 * The same facades keyed by entity name, to be spread into the application's
 * `BASE_ENTITY_FACADE_REGISTRY` value.
 *
 * Every entity a `RELATED_ENTITIES`, `EMBEDDED_COMPONENTS` or `FOREIGN_KEY` attribute of this library
 * names has to appear here, or the control throws on first render rather than showing a list whose
 * rows go nowhere on save — the registry is how it reaches the target's store and descriptor. The
 * reference model makes that load-bearing in a way the embedded one did not: a process's `roles`,
 * `artifacts` and `tools` resolve through this map, and so does an assignment's `taskDefinitionId`.
 *
 * Spread rather than provided separately, because the token holds one value: a second
 * `provide: BASE_ENTITY_FACADE_REGISTRY` would replace the application's own entities instead of
 * adding to them.
 */
export const BASE_WORKFLOW_ENTITY_FACADES: BaseEntityFacadeRegistry = {
  [PROCESS_DEFINITION_ENTITY_NAME]: ProcessDefinitionFacade,
  [PROCESS_TASK_ASSIGNMENT_ENTITY_NAME]: ProcessTaskAssignmentFacade,
  [WORKFLOW_ROLE_DEFINITION_ENTITY_NAME]: WorkflowRoleDefinitionFacade,
  [ARTIFACT_DEFINITION_ENTITY_NAME]: ArtifactDefinitionFacade,
  [TASK_DEFINITION_ENTITY_NAME]: TaskDefinitionFacade,
  [TASK_INPUT_REFERENCE_ENTITY_NAME]: TaskInputReferenceFacade,
  [TASK_OUTPUT_REFERENCE_ENTITY_NAME]: TaskOutputReferenceFacade,
  [TASK_STEP_DEFINITION_ENTITY_NAME]: TaskStepDefinitionFacade,
  [TOOL_DEFINITION_ENTITY_NAME]: ToolDefinitionFacade,
  [TOOL_OPERATION_ENTITY_NAME]: ToolOperationFacade,
  [PROCESS_INSTANCE_ENTITY_NAME]: ProcessInstanceFacade,
  [TASK_INSTANCE_ENTITY_NAME]: TaskInstanceFacade,
  [ARTIFACT_INSTANCE_ENTITY_NAME]: ArtifactInstanceFacade,
  [TASK_STEP_RESULT_ENTITY_NAME]: TaskStepResultFacade,
};
