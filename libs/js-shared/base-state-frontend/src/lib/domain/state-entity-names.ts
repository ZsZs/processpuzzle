/**
 * Entity names of the state machine graph, kept in one dependency-free module.
 *
 * They live here rather than next to their descriptors because the graph is cyclic: the
 * `State Machine Definition` descriptor aggregates `State Machine Transition`, and that one names the
 * definition back as its `componentParent`. Each descriptor module re-exports its own name, so
 * importers are unaffected. Same arrangement as base-app's `app-entity-names.ts`.
 *
 * Every name is prefixed, because `BASE_ENTITY_FACADE_REGISTRY` is one flat map for the whole
 * application: a bare `State` or `Transition` would be a name another feature could plausibly claim.
 */
export const STATE_MACHINE_DEFINITION_ENTITY_NAME = 'State Machine Definition';
export const STATE_MACHINE_STATE_ENTITY_NAME = 'State Machine State';
export const STATE_MACHINE_TRANSITION_ENTITY_NAME = 'State Machine Transition';
/**
 * A guard and an action carry the same two fields — a Spring bean name and its static params — and are
 * told apart only by where they sit on a transition. Two names rather than one shared entity, because a
 * `EMBEDDED_COMPONENTS` control resolves its child by name, and `guards` and `actions` are two lists.
 */
export const STATE_TRANSITION_GUARD_ENTITY_NAME = 'State Transition Guard';
export const STATE_TRANSITION_ACTION_ENTITY_NAME = 'State Transition Action';
