import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { ACTIVE_ENTITY_FACADE, baseEntityRoutes, BaseEntityContainerComponent, type EmbeddedChildRoute } from '@processpuzzle/base-entity';
import { BASE_ENTITY_TRANSLOCO_SCOPE, BASE_WORKFLOW_TRANSLOCO_SCOPE } from './base-workflow.i18n';
import {
  ARTIFACT_DEFINITION_ENTITY_NAME,
  ARTIFACT_INSTANCE_ENTITY_NAME,
  WORKFLOW_ENTITY_NAME,
  WORKFLOW_INSTANCE_ENTITY_NAME,
  WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME,
  TASK_DEFINITION_ENTITY_NAME,
  TASK_INSTANCE_ENTITY_NAME,
  TASK_STEP_DEFINITION_ENTITY_NAME,
  TASK_STEP_RESULT_ENTITY_NAME,
  TOOL_DEFINITION_ENTITY_NAME,
  TOOL_OPERATION_ENTITY_NAME,
  WORKFLOW_ROLE_DEFINITION_ENTITY_NAME,
  WORKFLOW_ROLE_USE_ENTITY_NAME,
  WORKFLOW_ARTIFACT_USE_ENTITY_NAME,
  WORKFLOW_TOOL_USE_ENTITY_NAME,
  WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME,
} from './domain/workflow-entity-names';
import { ArtifactDefinitionFacade } from './feature/definition/artifact-definition.facade';
import { WorkflowFacade } from './feature/definition/workflow.facade';
import { WorkflowRoleDefinitionFacade } from './feature/definition/role-definition.facade';
import { ROLE_MODELER_TAB } from './feature/definition/role-modeler-tab';
import { WORKFLOW_MODELER_TAB } from './feature/definition/workflow-modeler-tab';
import { TaskDefinitionFacade } from './feature/definition/task-definition.facade';
import { ToolDefinitionFacade } from './feature/definition/tool-definition.facade';
import {
  WorkflowArtifactUseFacade,
  WorkflowRequiredStartArtifactFacade,
  WorkflowRoleUseFacade,
  WorkflowTaskAssignmentFacade,
  WorkflowToolUseFacade,
  TaskStepDefinitionFacade,
  ToolOperationFacade,
} from './feature/definition/workflow-embedded.facades';
import { WorkflowInstanceFacade } from './feature/execution/workflow-instance.facade';
import { ArtifactInstanceFacade, TaskInstanceFacade, TaskStepResultFacade } from './feature/execution/instance-embedded.facades';

/**
 * The six routable aggregates of base-workflow, as six sibling branches: the four catalog entities a
 * tenant authors — roles, artifacts, tasks and tools — the workflow that composes them, and the runs it
 * produces.
 *
 * Siblings rather than a nesting, and that is the whole point of the reference model. A role, an
 * artifact and a task were children of one workflow until this contract; each is now shared across
 * workflows, so each is an aggregate with a list screen of its own, addressable at `/roles`,
 * `/artifacts` and `/tasks`. What ties the six together is data — a workflow naming a task id, a step
 * naming a `toolId`, an instance naming a `workflowId` — not the authoring URL. Same call
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
 * a Preview tab, none of these six needs a screen or an action beyond what `extraTabs` contributes. The
 * modeler contributes two such entries — {@link WORKFLOW_MODELER_TAB} on the workflow branch and
 * {@link ROLE_MODELER_TAB} on the roles one — with the Tasks perspective to follow on its own. Both use the
 * `modeler` segment and neither collides: a tab's segment is only ever appended to its own entity's
 * `<entity>/<id>/`.
 */
export const BASE_WORKFLOW_ROUTES: Routes = [
  {
    path: 'workflow',
    title: 'ProcessPuzzle - Workflows',
    data: { icon: 'schema', menuTitle: 'workflow.workflows', entityName: WORKFLOW_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: WorkflowFacade }, authoringScopes()],
    // The Workflows perspective of the modeler, at `:entityId/modeler`, beside the generic List and Details
    // and beside the workflow's own embedded levels. The same constant is on the descriptor, which is what
    // renders the tab's link.
    children: baseEntityRoutes(embeddedWorkflowRoutes(), [WORKFLOW_MODELER_TAB]),
  },
  {
    path: 'workflow-role-definition',
    title: 'ProcessPuzzle - Roles',
    data: { icon: 'badge', menuTitle: 'workflow.roles', entityName: WORKFLOW_ROLE_DEFINITION_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: WorkflowRoleDefinitionFacade }, authoringScopes()],
    // The Roles perspective of the modeler, mounted at `:entityId/modeler` beside the generic List and
    // Details. The same constant is on the descriptor, which is what renders the tab's link.
    children: baseEntityRoutes([], [ROLE_MODELER_TAB]),
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
    children: baseEntityRoutes([stepDefinitionRoute()]),
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
    path: 'workflow-instance',
    title: 'ProcessPuzzle - Workflow Instances',
    data: { icon: 'play_circle', menuTitle: 'workflow.instances', entityName: WORKFLOW_INSTANCE_ENTITY_NAME },
    component: BaseEntityContainerComponent,
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: WorkflowInstanceFacade }, authoringScopes()],
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
 * The embedded levels of a workflow, all below its details route: its task assignments, the three
 * `*Use` rows through which it involves a role, an artifact or a tool, and the required artifacts of
 * its start condition.
 *
 * The `*Use` rows are URLs of their own and not merely pickers, because that is what the contract makes
 * them: `Workflow.roles` is an array of `RoleUse` objects, each wrapping a `roleDefinitionId` and
 * standing ready to carry whatever turns out to be true of a shared role only in this workflow. The
 * *definition* the row names is still edited on its own branch — the row is the participation, not the
 * role.
 *
 * None of the five has an id of its own, so the URL is what addresses each, resolved against the rows
 * of the workflow above it:
 * `workflow/order-fulfillment-workflow/details/workflow-task-assignment/review-order/details`, and
 * `.../workflow-role-use/clerk/details` beside it.
 */
function embeddedWorkflowRoutes(): EmbeddedChildRoute[] {
  return [
    { entityName: WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME, facade: WorkflowTaskAssignmentFacade },
    { entityName: WORKFLOW_ROLE_USE_ENTITY_NAME, facade: WorkflowRoleUseFacade },
    { entityName: WORKFLOW_ARTIFACT_USE_ENTITY_NAME, facade: WorkflowArtifactUseFacade },
    { entityName: WORKFLOW_TOOL_USE_ENTITY_NAME, facade: WorkflowToolUseFacade },
    { entityName: WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME, facade: WorkflowRequiredStartArtifactFacade },
  ];
}

/**
 * The one embedded level of a task: its steps, below the task's details route.
 *
 * The nesting mirrors the containment of `base-workflow-api.yaml` exactly, and it has to: a step has no
 * endpoint of its own, so it is addressed through the task that carries it —
 * `task-definition/review-order/details/task-step-definition/check-items/details`. What a task reads
 * and writes has no branch here at all: `inputs` and `outputs` are artifact definition ids, picked on
 * the task's own form and edited under `artifact-definition`.
 */
function stepDefinitionRoute(): EmbeddedChildRoute {
  return { entityName: TASK_STEP_DEFINITION_ENTITY_NAME, facade: TaskStepDefinitionFacade };
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
