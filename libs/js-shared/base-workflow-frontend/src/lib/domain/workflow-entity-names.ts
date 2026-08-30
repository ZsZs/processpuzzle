/**
 * Entity names of the whole workflow graph, kept in one dependency-free module.
 *
 * They live here rather than next to their descriptors because the graph is cyclic: the
 * `Workflow` descriptor aggregates `Workflow Task Assignment`, and that one names the
 * definition back as its `componentParent`. Each descriptor module re-exports the names it owns, so
 * importers are unaffected. Same arrangement as base-state's `state-entity-names.ts` and base-app's
 * `app-entity-names.ts`.
 *
 * Every name is prefixed where a bare one would be ambiguous, because `BASE_ENTITY_FACADE_REGISTRY`
 * is one flat map for the whole application: `Role Definition` is a name another feature — or a
 * tenant's own metadata — could plausibly claim.
 */

// region definition layer — design time
/**
 * The four catalog aggregates a tenant authors independently, and the workflow that composes them.
 *
 * A role, an artifact, a task and a tool are each addressable on their own — `/roles`, `/artifacts`,
 * `/tasks`, `/tools` — and shared across workflows, so each has its own list screen. The workflow
 * *references* them; only {@link WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME} is private to it.
 */
export const WORKFLOW_ENTITY_NAME = 'Workflow';
export const WORKFLOW_ROLE_DEFINITION_ENTITY_NAME = 'Workflow Role Definition';
export const ARTIFACT_DEFINITION_ENTITY_NAME = 'Artifact Definition';
export const TASK_DEFINITION_ENTITY_NAME = 'Task Definition';

/**
 * A task's place in one workflow: which of the task's `performedByRoles` performs it here, what has to
 * finish first, and whether it may run beside its siblings.
 *
 * An entity of its own rather than fields on the task, because the task is shared and none of those
 * three answers is: `dependsOn` names siblings of *one* workflow, `parallel` orders it against them,
 * and `override` belongs to that workflow's `extends` chain. The row therefore stays embedded in the
 * workflow, and it is the only thing that does.
 */
export const WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME = 'Workflow Task Assignment';

/**
 * The three `*Use` rows of a workflow: a role, an artifact or a tool *taking part in this workflow*,
 * as opposed to being defined by the tenant.
 *
 * Separate entities rather than plain id lists on the workflow, because that is what the contract
 * says they are — `Workflow.roles` is `RoleUse[]`, and a `RoleUse` is an object wrapping
 * `roleDefinitionId`. Each wraps only that id today, and the schema is explicit that this is
 * deliberate: the object is where per-workflow configuration of a shared definition will go. Modelling
 * them as ids would have to be undone the first time one of them grows a second field, and until then
 * it silently drops every row — `toReferenceIds` looks for `.id` and a `*Use` has none.
 *
 * Three names rather than one shared `Use` entity, for the reason
 * {@link WORKFLOW_TASK_ASSIGNMENT_ENTITY_NAME} and the step rows share: an `EMBEDDED_COMPONENTS`
 * control resolves its child by name, and `BaseEntityDescriptor.embeddedAttrFor()` refuses a child
 * type carried by two attributes, since the route segment names the entity.
 */
export const WORKFLOW_ROLE_USE_ENTITY_NAME = 'Workflow Role Use';
export const WORKFLOW_ARTIFACT_USE_ENTITY_NAME = 'Workflow Artifact Use';
export const WORKFLOW_TOOL_USE_ENTITY_NAME = 'Workflow Tool Use';

/**
 * One artifact — and optionally the state it has to be in — that a workflow's `INPUT_ARTIFACT` start
 * condition waits for.
 *
 * Embedded in the workflow because it is part of that workflow's start condition and nothing else, and
 * an entity of its own because `requiredArtifacts` is a list the author edits row by row. The `state`
 * is base-state's to interpret; base-workflow records it and never resolves it.
 */
export const WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME = 'Workflow Required Start Artifact';

export const TASK_STEP_DEFINITION_ENTITY_NAME = 'Task Step Definition';
export const TOOL_DEFINITION_ENTITY_NAME = 'Tool Definition';
export const TOOL_OPERATION_ENTITY_NAME = 'Tool Operation';
// endregion

// region execution layer — run time, read-only
export const WORKFLOW_INSTANCE_ENTITY_NAME = 'Workflow Instance';
export const TASK_INSTANCE_ENTITY_NAME = 'Task Instance';
export const ARTIFACT_INSTANCE_ENTITY_NAME = 'Artifact Instance';
export const TASK_STEP_RESULT_ENTITY_NAME = 'Task Step Result';
// endregion
