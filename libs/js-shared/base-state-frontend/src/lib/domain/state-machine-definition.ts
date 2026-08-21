import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Frontend model of the knowledge layer of `base-state-api.yaml`: a `StateMachineDefinition` with its
 * `states` and `transitions`, and the guard / action references a transition carries.
 *
 * Nothing is flattened here, unlike `AppDefinition`'s `theme` and `layout`: every field of the contract
 * is either a scalar or a nested *array*, and an array is exactly what an `EMBEDDED_COMPONENTS` control
 * edits. `states` and `transitions` therefore stay nested and travel inside this entity's payload, which
 * the full-replacement `PUT /state-machines/{entityName}` requires — a control that dropped them would
 * wipe the machine on the next save.
 *
 * The nested definitions are classes rather than interfaces, because each is an embedded entity of its
 * own: `EmbeddedEntityFacade` mints the blank row an `Add` opens the child's form on, and that needs a
 * constructor. They stay plain data — the rows of a loaded machine are the parsed JSON, never instances
 * of these classes, so nothing may rely on `instanceof` or on a method.
 *
 * Field names are the *contract's*, and `StateMachineDefinitionMapper` is what makes that safe: the seed
 * YAML both backends are provisioned from spells a state's flags `isFinal` / `isLocked` (that being how
 * base-state-backend's own `State` record names them), and json-server serves that YAML verbatim. The
 * mapper reads either spelling and writes the contract's, so nothing below has to know.
 */

/**
 * A key/value map the generic form can author. Both {@link State.metadata} and {@link BeanRef.params}
 * are `additionalProperties: true` in the contract — any JSON value — but the `ADDITIONAL_PROPERTIES`
 * control edits text, so the model narrows to what a user can actually type. A value a backend put there
 * survives a round trip regardless; it is simply shown as text.
 */
export type PropertyMap = Record<string, string>;

/**
 * Names a Spring bean implementing `TransitionGuard` or `TransitionAction`, resolved by bean name. One
 * class for both, because the contract's `GuardRef` and `ActionRef` are the same two fields: which of the
 * two a row is follows from the list it sits in, and the two entity names — `State Transition Guard` and
 * `State Transition Action` — are what keep the two forms apart.
 */
export class BeanRef implements BaseEntity {
  /**
   * Declared, never assigned. The contract gives a guard no `id` — `beanName` identifies it, see
   * `BEAN_REF_ID_FIELD` — but `BaseEntity`'s only property is an optional `id`, and TypeScript's
   * weak-type rule rejects a type that shares no property with it. `declare` emits nothing, so the
   * payload stays exactly the shape the schema describes.
   */
  declare readonly id?: string;

  beanName: string;
  /** Static configuration handed to the bean alongside the run-time transition context. */
  params?: PropertyMap;

  constructor(init: Partial<BeanRef> = {}) {
    this.beanName = init.beanName ?? '';
    this.params = init.params;
  }
}

/**
 * A single, flat, mutually exclusive value of the state attribute. No parallel and no nested states in
 * this version, so a state has no children and the embedded list is not a tree.
 */
export class State implements BaseEntity {
  /** Declared, never assigned: `key` identifies a state. Same reason as {@link BeanRef.id}. */
  declare readonly id?: string;

  /** Unique within the machine; the literal value written to the entity object's state attribute. */
  key: string;
  name: string;
  description?: string;
  /** No transition may declare this state as its source — enforced at definition save time. */
  terminal: boolean;
  /** While an object sits in this state, only the state attribute itself may change on it. */
  locked: boolean;
  /** UI hints only — colour, icon — for rendering the machine's graph. Opaque to the backend. */
  metadata?: PropertyMap;

  constructor(init: Partial<State> = {}) {
    this.key = init.key ?? '';
    this.name = init.name ?? '';
    this.description = init.description;
    this.terminal = init.terminal ?? false;
    this.locked = init.locked ?? false;
    this.metadata = init.metadata;
  }
}

/** One edge of the machine: the trigger that moves an object from one state to another. */
export class Transition implements BaseEntity {
  /** Declared, never assigned: `key` identifies a transition. Same reason as {@link BeanRef.id}. */
  declare readonly id?: string;

  /** Unique within the machine. */
  key: string;
  name?: string;
  /** Must resolve to a declared, non-terminal {@link State.key}. */
  sourceStateKey: string;
  /** Must resolve to a declared {@link State.key}. */
  targetStateKey: string;
  /**
   * The verb callers invoke — `approve`. Unique in combination with {@link sourceStateKey}, which is how
   * one button can mean different things depending on where the object currently sits.
   */
  triggerKey: string;
  /** Evaluated in order, AND semantics, short-circuiting. Empty means the transition is unconditional. */
  guards: BeanRef[];
  /** Executed in order, only once every guard has passed. */
  actions: BeanRef[];

  constructor(init: Partial<Transition> = {}) {
    this.key = init.key ?? '';
    this.name = init.name;
    this.sourceStateKey = init.sourceStateKey ?? '';
    this.targetStateKey = init.targetStateKey ?? '';
    this.triggerKey = init.triggerKey ?? '';
    // Empty arrays rather than undefined, so an embedded list always has something to append to.
    this.guards = init.guards ?? [];
    this.actions = init.actions ?? [];
  }
}

/**
 * The state machine of one entity type. Bound 1:1 to `entityName` — the contract addresses a definition
 * by that name rather than by a key of its own, exactly as base-entity's
 * `/entity-definitions/{entityName}` does — so {@link id} is a *mirror* of {@link entityName} and not an
 * independent field. That mirroring is what lets the generic screens work unchanged: every single-record
 * URL `BaseEntityRestService` builds comes from `id`, and so does the details link
 * `BaseFormNavigatorSingletonStore` renders.
 */
export class StateMachineDefinition implements BaseEntity {
  /** Mirror of {@link entityName}; maintained by the mapper and never edited. See the class comment. */
  id: string;
  /** The entity type this machine governs, resolved within the organization. */
  entityName: string;
  name: string;
  description: string | undefined;
  /**
   * The TEXT attribute of `entityName` whose value holds the current state. base-state is its only
   * writer — see "Ownership and the single entry point" in `base-state-api.yaml`.
   */
  stateAttributeKey: string;
  /** The {@link State.key} a newly created object of this type starts in. */
  initialStateKey: string;
  states: State[];
  transitions: Transition[];
  // region server-assigned
  orgKey: string | undefined;
  version: number | undefined;
  createdAt: string | undefined;
  updatedAt: string | undefined;
  // endregion

  constructor(init: Partial<StateMachineDefinition> = {}) {
    this.entityName = init.entityName ?? '';
    this.id = init.id ?? this.entityName;
    this.name = init.name ?? '';
    this.description = init.description;
    this.stateAttributeKey = init.stateAttributeKey ?? '';
    this.initialStateKey = init.initialStateKey ?? '';
    this.states = init.states ?? [];
    this.transitions = init.transitions ?? [];
    this.orgKey = init.orgKey;
    this.version = init.version;
    this.createdAt = init.createdAt;
    this.updatedAt = init.updatedAt;
  }
}
