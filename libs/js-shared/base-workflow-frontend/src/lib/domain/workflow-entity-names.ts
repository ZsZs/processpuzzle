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
 * One way an instance of a workflow comes into being — the contract's `StartEvent`. A workflow may
 * declare several, each a row of `Workflow.startEvents` with an author-chosen `id`.
 *
 * Embedded in the workflow because a start event means nothing outside it, and the one embedded row of
 * the workflow that nests a list of its own: its required artifacts, below.
 */
export const WORKFLOW_START_EVENT_ENTITY_NAME = 'Workflow Start Event';

/**
 * One artifact — and optionally the state it has to be in — that an `INPUT_ARTIFACT` start event waits
 * for.
 *
 * Embedded in the start event because it is part of that event and nothing else, and an entity of its
 * own because `requiredArtifacts` is a list the author edits row by row. The `state` is base-state's to
 * interpret; base-workflow records it and never resolves it.
 */
export const WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME = 'Workflow Required Start Artifact';

/**
 * One intermediate event of a workflow — the contract's `EventUse`: a catalog event the workflow throws or
 * catches while it runs. A row of `Workflow.events` with an author-chosen `id`, embedded for the reason a
 * start event is.
 */
export const WORKFLOW_EVENT_USE_ENTITY_NAME = 'Workflow Event Use';

/**
 * The catalog event a `TRIGGERING_EVENT` start event waits for — an entity of **base-event**, not of this
 * library. Named here as a string rather than imported, because features meet through each other's
 * metadata and never through a package dependency: the `FOREIGN_KEY` resolves it through the application's
 * `BASE_ENTITY_FACADE_REGISTRY`, into which the host spreads `BASE_EVENT_ENTITY_FACADES`. A host without
 * base-event still renders the control; it just has nothing to offer.
 *
 * Deliberately absent from `BASE_WORKFLOW_ENTITY_FACADES` for the same reason.
 */
export const EVENT_DEFINITION_ENTITY_NAME = 'Event Definition';

/**
 * The state one task of one workflow expects an input artifact in, and the state it leaves an output
 * artifact in — the contract's `TaskArtifactState`.
 *
 * Embedded in the task assignment because a state is true of a task only *in this workflow*: the shared
 * task definition says which artifacts it reads and writes, the workflow says in which states. Display and
 * save-time validation only — base-state still owns the transition.
 */
export const WORKFLOW_TASK_ARTIFACT_STATE_ENTITY_NAME = 'Workflow Task Artifact State';

export const TASK_STEP_DEFINITION_ENTITY_NAME = 'Task Step Definition';
export const TOOL_DEFINITION_ENTITY_NAME = 'Tool Definition';
export const TOOL_OPERATION_ENTITY_NAME = 'Tool Operation';
// endregion

// region execution layer — run time, read-only
export const WORKFLOW_INSTANCE_ENTITY_NAME = 'Workflow Instance';
export const TASK_INSTANCE_ENTITY_NAME = 'Task Instance';
export const ARTIFACT_INSTANCE_ENTITY_NAME = 'Artifact Instance';
export const TASK_STEP_RESULT_ENTITY_NAME = 'Task Step Result';
/** The run-time state of one intermediate event of an instance — read-only, like the rest of the run. */
export const EVENT_INSTANCE_ENTITY_NAME = 'Event Instance';
// endregion
