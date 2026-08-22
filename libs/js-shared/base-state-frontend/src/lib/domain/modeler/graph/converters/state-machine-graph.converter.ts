import { DiagramDefinition, DiagramViewport, EdgeLayout, NodeLayout, NodeSize, Point } from '../../models/diagram-definition';
import { State, StateMachineDefinition, Transition } from '../../../state-machine-definition';
import { StateMachineGraph, STATE_NODE_TYPE, StateNode, TransitionEdge } from '../state-machine-graph';

/**
 * Joins the two halves of a modelled state machine — the topology (`StateMachineDefinition`) and the
 * arrangement (`DiagramDefinition`) — into the graph ng-diagram draws, and takes both back apart again
 * after the user has drawn on it: {@link toLayout} yields the arrangement, {@link toMachine} the topology.
 *
 * The two resources are separate on the wire for good reasons (see `DiagramDefinition`'s class comment),
 * which leaves exactly one place that has to know how they fit together. This is it: nothing else in the
 * modeler reads a `NodeLayout` row or writes one.
 *
 * Three rules are worth stating, because each is a decision rather than a mechanical mapping.
 *
 * **Keys are the identity.** A node's `id` is the `State.key` and an edge's is the `Transition.key` — not
 * a synthetic id — so a layout row finds its subject by the same key the contract persists, and a reload
 * cannot re-associate them differently.
 *
 * **A stale layout row is ignored, not an error.** `updateStateMachineDefinition` is free to drop a state,
 * so a row naming a key the machine no longer declares is normal; the contract's `saveDiagramDefinition`
 * says as much, and {@link toLayout} prunes it on the next save simply by not emitting it.
 *
 * **An unarranged state is reported, not guessed at.** {@link toGraph} places what the layout knows about
 * and lists the rest in `unplacedStateKeys` for `DagreLayoutService`. It does not lay out anything itself:
 * a converter that silently invented positions would make "this machine has never been arranged" and "this
 * machine is arranged with everything at the origin" indistinguishable.
 */
export class StateMachineGraphConverter {
  /**
   * Builds the graph of `machine`, positioned by `layout` where it says anything.
   *
   * `layout` is optional because a machine with no arrangement is the normal starting point —
   * `GET /diagrams/{entityName}` answers 404 — and not a case the caller should have to special-case.
   */
  static toGraph(machine: StateMachineDefinition, layout?: DiagramDefinition): StateMachineGraph {
    // Indexed once rather than searched per row: a machine with fifty states and fifty transitions would
    // otherwise be quadratic in the two lists that grow together.
    const nodeLayouts = new Map((layout?.nodes ?? []).map((node) => [node.stateKey, node]));
    const edgeLayouts = new Map((layout?.edges ?? []).map((edge) => [edge.transitionKey, edge]));
    const unplacedStateKeys: string[] = [];

    const nodes = (machine.states ?? []).map((state) => {
      const nodeLayout = nodeLayouts.get(state.key);
      if (!nodeLayout) unplacedStateKeys.push(state.key);
      return toStateNode(state, machine.initialStateKey, nodeLayout);
    });
    const edges = (machine.transitions ?? []).map((transition) => toTransitionEdge(transition, edgeLayouts.get(transition.key)));

    return {
      nodes,
      edges,
      metadata: layout?.viewport ? { viewport: { x: layout.viewport.x, y: layout.viewport.y, scale: layout.viewport.scale } } : undefined,
      unplacedStateKeys,
    };
  }

  /**
   * Takes the arrangement back off a drawn graph, as the layout resource persists it.
   *
   * Deliberately narrow: positions, sizes, port anchors, waypoints and the viewport, and nothing else. The
   * `state` and `transition` each node and edge carries belong to `StateMachineDefinition`, and writing
   * them here through a `PUT /diagrams/{entityName}` — a resource with a version of its own — is how the
   * two would start to disagree.
   *
   * `version` is carried over from `previous` when given, because the write is optimistic-locked: saving an
   * arrangement read at version 3 has to say 3, or the server cannot tell a concurrent edit from a stale one.
   */
  /**
   * Takes the *topology* back off a drawn graph — the counterpart of {@link toLayout}, and the modeler's
   * write path into `StateMachineDefinition`.
   *
   * The nodes and edges are the machine's states and transitions, not a projection of them: each carries
   * its domain object in `data`, and this reads it back. That makes the drawn graph the authority on what
   * the machine contains while the modeler is open, which is what lets the palette add a state and a
   * deleted node remove one. Deleting a node cannot leave a dangling transition, because ng-diagram's
   * `deleteNodes` removes the attached edges with it.
   *
   * Everything that is *not* drawn — the name, the description, the state attribute, and above all the
   * `version` the save is optimistic-locked on — is carried over from `base` untouched.
   */
  static toMachine(base: StateMachineDefinition, nodes: StateNode[], edges: TransitionEdge[]): StateMachineDefinition {
    return new StateMachineDefinition({
      ...base,
      states: nodes.map((node) => node.data.state),
      // `data.transition` is typed as present but is not there on an edge the *user* drew, which carries
      // only what ng-diagram's linking put in it. Such an edge names no trigger and no guard, so there is
      // no transition to save — see the canvas's `linking.validateConnection`, which is why one should not
      // arise in the first place.
      transitions: edges.map((edge) => edge.data?.transition).filter((transition): transition is Transition => transition !== undefined),
      initialStateKey: nodes.find((node) => node.data.initial)?.data.state.key ?? base.initialStateKey,
    });
  }

  static toLayout(entityName: string, nodes: StateNode[], edges: TransitionEdge[], viewport?: DiagramViewport, previous?: DiagramDefinition): DiagramDefinition {
    return new DiagramDefinition({
      entityName,
      nodes: nodes.map(toNodeLayout),
      edges: edges.map(toEdgeLayout),
      viewport,
      orgKey: previous?.orgKey,
      version: previous?.version,
      createdAt: previous?.createdAt,
      updatedAt: previous?.updatedAt,
    });
  }
}

// region private helper functions
function toStateNode(state: State, initialStateKey: string, layout?: NodeLayout): StateNode {
  return {
    id: state.key,
    type: STATE_NODE_TYPE,
    // The origin is a placeholder for an unarranged state, which `unplacedStateKeys` is what actually
    // reports — ng-diagram requires a position, so there is nothing else to put here.
    position: layout ? { x: layout.position.x, y: layout.position.y } : { x: 0, y: 0 },
    // Left off when the layout carries none, so ng-diagram sizes the node by its content. A 0x0 box would
    // collapse it.
    ...(layout?.size ? { size: { width: layout.size.width, height: layout.size.height } } : {}),
    data: { state, label: state.name || state.key, initial: state.key === initialStateKey },
  };
}

/**
 * Waypoints are only honoured in `manual` routing mode — in `auto` mode ng-diagram owns `points` and
 * recomputes them — so the mode is set from whether the layout actually recorded any. An edge with no
 * recorded waypoints keeps the automatic routing it had.
 *
 * The ports are passed through as they were saved, including absent: an edge with no port anchors floats
 * to whichever node borders face each other, which is the right default for a computed layout.
 */
function toTransitionEdge(transition: Transition, layout?: EdgeLayout): TransitionEdge {
  const points = layout?.points ?? [];
  return {
    id: transition.key,
    source: transition.sourceStateKey,
    target: transition.targetStateKey,
    sourcePort: layout?.sourcePort,
    targetPort: layout?.targetPort,
    routing: layout?.routing,
    ...(points.length > 0 ? { points: points.map((point) => ({ x: point.x, y: point.y })), routingMode: 'manual' as const } : {}),
    data: { transition, label: transition.name || transition.triggerKey || transition.key },
  };
}

function toNodeLayout(node: StateNode): NodeLayout {
  return new NodeLayout({
    stateKey: node.id,
    position: new Point({ x: node.position.x, y: node.position.y }),
    size: node.size ? new NodeSize({ width: node.size.width, height: node.size.height }) : undefined,
  });
}

/**
 * Only waypoints the user placed are saved. In `auto` mode `points` holds whatever the routing algorithm
 * last computed, and persisting that would freeze a derived path into the layout — the next load would
 * replay stale geometry instead of routing around wherever the nodes now sit.
 */
function toEdgeLayout(edge: TransitionEdge): EdgeLayout {
  return new EdgeLayout({
    transitionKey: edge.id,
    points: edge.routingMode === 'manual' ? (edge.points ?? []).map((point) => new Point({ x: point.x, y: point.y })) : [],
    sourcePort: edge.sourcePort,
    targetPort: edge.targetPort,
    routing: edge.routing,
  });
}
// endregion
