/**
 * Entity names of the whole workflow graph, kept in one dependency-free module.
 *
 * They live here rather than next to their descriptors because the graph is cyclic: the
 * `Process Definition` descriptor aggregates `Process Task Assignment`, and that one names the
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
 * The four catalog aggregates a tenant authors independently, and the process that composes them.
 *
 * A role, an artifact, a task and a tool are each addressable on their own — `/roles`, `/artifacts`,
 * `/tasks`, `/tools` — and shared across processes, so each has its own list screen. The process
 * *references* them; only {@link PROCESS_TASK_ASSIGNMENT_ENTITY_NAME} is private to it.
 */
export const PROCESS_DEFINITION_ENTITY_NAME = 'Process Definition';
export const WORKFLOW_ROLE_DEFINITION_ENTITY_NAME = 'Workflow Role Definition';
export const ARTIFACT_DEFINITION_ENTITY_NAME = 'Artifact Definition';
export const TASK_DEFINITION_ENTITY_NAME = 'Task Definition';

/**
 * A task's place in one process: which of the task's `performedByRoles` performs it here, what has to
 * finish first, and whether it may run beside its siblings.
 *
 * An entity of its own rather than fields on the task, because the task is shared and none of those
 * three answers is: `dependsOn` names siblings of *one* process, `parallel` orders it against them,
 * and `override` belongs to that process's `extends` chain. The row therefore stays embedded in the
 * process, and it is the only thing that does.
 */
export const PROCESS_TASK_ASSIGNMENT_ENTITY_NAME = 'Process Task Assignment';

/**
 * A task's inputs and its outputs carry the same three fields — a reference type, the id it points
 * at and an optional label — and are told apart only by which list they sit in. Two names rather
 * than one shared entity, because an `EMBEDDED_COMPONENTS` control resolves its child by name and
 * `inputs` / `outputs` are two lists: `BaseEntityDescriptor.embeddedAttrFor()` refuses a child type
 * carried by two attributes, since the route segment names the entity. Same shape as base-state's
 * `State Transition Guard` / `State Transition Action` over one `BeanRef`.
 */
export const TASK_INPUT_REFERENCE_ENTITY_NAME = 'Task Input Reference';
export const TASK_OUTPUT_REFERENCE_ENTITY_NAME = 'Task Output Reference';

export const TASK_STEP_DEFINITION_ENTITY_NAME = 'Task Step Definition';
export const TOOL_DEFINITION_ENTITY_NAME = 'Tool Definition';
export const TOOL_OPERATION_ENTITY_NAME = 'Tool Operation';
// endregion

// region execution layer — run time, read-only
export const PROCESS_INSTANCE_ENTITY_NAME = 'Process Instance';
export const TASK_INSTANCE_ENTITY_NAME = 'Task Instance';
export const ARTIFACT_INSTANCE_ENTITY_NAME = 'Artifact Instance';
export const TASK_STEP_RESULT_ENTITY_NAME = 'Task Step Result';
// endregion
