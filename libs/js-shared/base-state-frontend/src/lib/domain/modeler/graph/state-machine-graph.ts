import { Edge, Metadata, Node } from 'ng-diagram';
import { State, Transition } from '../../definition/state-machine-definition';

/**
 * The ng-diagram graph of one state machine: what {@link StateMachineGraphConverter} produces by joining a
 * `StateMachineDefinition` (the topology) with a `DiagramDefinition` (where it sits on the canvas).
 *
 * Nothing here is a model of its own. The nodes and edges are ng-diagram's, so the result can be handed
 * straight to `initializeModel`, and the two `data` shapes below are the only thing this module adds —
 * they are how a state machine's own vocabulary survives the trip through a generic diagram library.
 */

/**
 * The `type` every state node carries, and the key `NgDiagramNodeTemplateMap` resolves `StateNodeComponent`
 * by. Prefixed, because the map is one flat registry per diagram and `state` is a name any feature might
 * plausibly claim.
 */
export const STATE_NODE_TYPE = 'ppState';

/**
 * The `type` every transition edge carries, and the key `NgDiagramEdgeTemplateMap` resolves
 * `TransitionEdgeComponent` by. Prefixed for the same reason {@link STATE_NODE_TYPE} is.
 *
 * A custom template for one reason: an edge has to be able to report a right-click on *itself*, and the
 * default template offers nowhere to listen. What it draws is deliberately the default look.
 */
export const TRANSITION_EDGE_TYPE = 'ppTransition';

/**
 * What a state node carries in ng-diagram's `data`.
 *
 * `state` is the whole {@link State}, not a projection of it: the properties panel reads its subject off
 * the selection, and a projection would mean deciding here which fields the panel is allowed to show.
 * `label` and `initial` are alongside it rather than derived in the template, so the node template stays
 * a template — and so the label rule (a state with no name is labelled by its key) is stated once.
 */
export interface StateNodeData {
  state: State;
  label: string;
  /** True for the one state the machine starts in, which the node template marks. */
  initial: boolean;
  /**
   * True for the state a *particular object* currently sits in — set only when the graph was built for one
   * object, which is what the State Machine tab of a governed entity does. Absent everywhere else,
   * including in the modeler: a definition being authored has no object to be anywhere.
   *
   * Alongside `initial` rather than derived in the template from a key the node would also have to be
   * given, so the node template keeps reading flags rather than comparing identities.
   */
  isCurrent?: boolean;
}

/** What a transition edge carries in ng-diagram's `data`. Same reasoning as {@link StateNodeData}. */
export interface TransitionEdgeData {
  transition: Transition;
  label: string;
}

export type StateNode = Node<StateNodeData>;
export type TransitionEdge = Edge<TransitionEdgeData>;

/**
 * A converted machine, ready for `initializeModel` once the unplaced nodes have been through
 * `DagreLayoutService`.
 *
 * {@link unplacedStateKeys} is part of the result rather than something the caller re-derives by looking
 * for nodes at the origin — a state legitimately arranged at `0,0` is indistinguishable from one that was
 * never arranged, and the difference decides between laying the whole graph out and parking one new state.
 */
export interface StateMachineGraph {
  nodes: StateNode[];
  edges: TransitionEdge[];
  /** Carries the persisted viewport, so reopening returns to where the user was. Absent until panned. */
  metadata?: Partial<Metadata>;
  /** Keys of the states the layout said nothing about — in `nodes` order. */
  unplacedStateKeys: string[];
}
