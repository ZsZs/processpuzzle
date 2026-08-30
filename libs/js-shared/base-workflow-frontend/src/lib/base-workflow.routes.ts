import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ACTIVE_ENTITY_FACADE, baseEntityRoutes, BaseEntityContainerComponent, type EmbeddedChildRoute } from '@processpuzzle/base-entity';
import { BASE_ENTITY_TRANSLOCO_SCOPE, BASE_WORKFLOW_TRANSLOCO_SCOPE } from './base-workflow.i18n';
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
 * The six routable aggregates of base-workflow, as six sibling branches: the four catalog entities a
 * tenant authors — roles, artifacts, tasks and tools — the process that composes them, and the runs it
 * produces.
 *
 * Siblings rather than a nesting, and that is the whole point of the reference model. A role, an
 * artifact and a task were children of one process until this contract; each is now shared across
 * processes, so each is an aggregate with a list screen of its own, addressable at `/roles`,
 * `/artifacts` and `/tasks`. What ties the six together is data — a process naming a task id, a step
 * naming a `toolId`, an instance naming a `processDefinitionId` — not the authoring URL. Same call
 * base-app makes for `app-definition` and `module-definition`.
 *
 * Each `path` has to be `snakeCaseName(entityName)`, because `BaseFormNavigatorSingletonStore` builds
 * the details URL from the entity name — the same constraint `BASE_APP_ROUTES`, `BASE_RULE_ROUTES` and
 * `BASE_STATE_ROUTES` carry.
 *
 * `entityName` in `data` is not decoration: `readEmbeddedBreadcrumb` pushes a level when it meets the
 * route that *declares* the name, and takes that level's base URL from the URL accumulated so far. It
 * has to sit on the route contributing the aggregate's own segment — here — or every URL built on that
 * level doubles the segment, silently.
 *
 * The generic container is mounted directly rather than through a component of this library's own:
 * unlike base-app's `AppDefinitionContainerComponent`, which exists to contribute a Publish action and
 * a Preview tab, none of these six has a screen or an action beyond List and Details yet. The process
 * modeler this library will eventually want is the natural first `extraTabs` entry on the
 * `process-definition` branch, and it would go in exactly where base-state passes `STATE_MODELER_TAB`.
 */
export const BASE_WORKFLOW_ROUTES: Routes = [
  {
    path: 'process-definition',
    title: 'ProcessPuzzle - Processes',
    data: { icon: 'schema', menuTitle: 'workflow.processes', entityName: PROCESS_DEFINITION_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: ProcessDefinitionFacade }, authoringScopes()],
    children: baseEntityRoutes([assignmentRoute()]),
  },
  {
    path: 'workflow-role-definition',
    title: 'ProcessPuzzle - Roles',
    data: { icon: 'badge', menuTitle: 'workflow.roles', entityName: WORKFLOW_ROLE_DEFINITION_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: WorkflowRoleDefinitionFacade }, authoringScopes()],
    children: baseEntityRoutes(),
  },
  {
    path: 'artifact-definition',
    title: 'ProcessPuzzle - Artifacts',
    data: { icon: 'inventory_2', menuTitle: 'workflow.artifacts', entityName: ARTIFACT_DEFINITION_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: ArtifactDefinitionFacade }, authoringScopes()],
    children: baseEntityRoutes(),
  },
  {
    path: 'task-definition',
    title: 'ProcessPuzzle - Tasks',
    data: { icon: 'assignment', menuTitle: 'workflow.tasks', entityName: TASK_DEFINITION_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: TaskDefinitionFacade }, authoringScopes()],
    children: baseEntityRoutes(embeddedTaskRoutes()),
  },
  {
    path: 'tool-definition',
    title: 'ProcessPuzzle - Tools',
    data: { icon: 'build', menuTitle: 'workflow.tools', entityName: TOOL_DEFINITION_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: ToolDefinitionFacade }, authoringScopes()],
    children: baseEntityRoutes([operationRoute()]),
  },
  {
    path: 'process-instance',
    title: 'ProcessPuzzle - Process Instances',
    data: { icon: 'play_circle', menuTitle: 'workflow.instances', entityName: PROCESS_INSTANCE_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: ProcessInstanceFacade }, authoringScopes()],
    children: baseEntityRoutes(embeddedInstanceRoutes()),
  },
];

/**
 * The transloco scopes this branch needs. Both are required: a route that declares TRANSLOCO_SCOPE
 * replaces the collection it inherits rather than adding to it, and the generic tabs translate the
 * framework's own `base_entity.*` keys.
 *
 * Registered on each top-level route rather than once above them, because there is no shared parent —
 * the six branches are spread into whatever route an application mounts them under. The embedded
 * branches below need none of their own: `base_workflow.task_definition.*` and its siblings are keys
 * of the scope already registered here.
 *
 * Both aliases are spelled out, as everywhere in this workspace: transloco camel-cases the default
 * alias, so `base_workflow` would silently become `baseWorkflow` and miss every key below it.
 */
function authoringScopes() {
  return provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE }, { scope: BASE_WORKFLOW_TRANSLOCO_SCOPE, alias: BASE_WORKFLOW_TRANSLOCO_SCOPE });
}

/**
 * The one embedded level of a process: its task assignments, below the process's details route.
 *
 * A leaf, and the only child this branch has left — the roles, artifacts and tools a process involves
 * are references now, edited on their own branches and merely picked here, so no URL under a process
 * addresses them. An assignment has no id of its own, so the URL is what addresses it:
 * `process-definition/order-fulfillment-workflow/details/process-task-assignment/review-order/details`,
 * resolved against the rows of the process above it.
 */
function assignmentRoute(): EmbeddedChildRoute {
  return { entityName: PROCESS_TASK_ASSIGNMENT_ENTITY_NAME, facade: ProcessTaskAssignmentFacade };
}

/**
 * The task as route branches: its inputs, outputs and steps hang below the task's details route.
 *
 * The nesting mirrors the containment of `base-workflow-api.yaml` exactly, and it has to: none of the
 * three has an endpoint of its own, so each is addressed through the task that carries it —
 * `task-definition/review-order/details/task-step-definition/check-items/details`.
 */
function embeddedTaskRoutes(): EmbeddedChildRoute[] {
  return [
    { entityName: TASK_INPUT_REFERENCE_ENTITY_NAME, facade: TaskInputReferenceFacade },
    { entityName: TASK_OUTPUT_REFERENCE_ENTITY_NAME, facade: TaskOutputReferenceFacade },
    { entityName: TASK_STEP_DEFINITION_ENTITY_NAME, facade: TaskStepDefinitionFacade },
  ];
}

function operationRoute(): EmbeddedChildRoute {
  return { entityName: TOOL_OPERATION_ENTITY_NAME, facade: ToolOperationFacade };
}

/** The run as route branches: tasks and artifacts below the instance, step results below a task. */
function embeddedInstanceRoutes(): EmbeddedChildRoute[] {
  return [
    { entityName: TASK_INSTANCE_ENTITY_NAME, facade: TaskInstanceFacade, children: () => [stepResultRoute()] },
    { entityName: ARTIFACT_INSTANCE_ENTITY_NAME, facade: ArtifactInstanceFacade },
  ];
}

function stepResultRoute(): EmbeddedChildRoute {
  return { entityName: TASK_STEP_RESULT_ENTITY_NAME, facade: TaskStepResultFacade };
}
