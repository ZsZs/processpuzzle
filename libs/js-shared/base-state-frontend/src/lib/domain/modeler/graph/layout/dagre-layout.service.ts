import dagre from '@dagrejs/dagre';
import { Injectable } from '@angular/core';
import { StateNode, TransitionEdge } from '../state-machine-graph';

/**
 * Estimated node box, used only to space the graph out. The real nodes are auto-sized by their content, so
 * their measured size is not known until ng-diagram has rendered them once — laying out against a stated
 * estimate is both simpler and stable across reloads, which a measured layout would not be.
 */
const ESTIMATED_NODE_SIZE = { width: 150, height: 60 };
/** Gap between parked nodes, and between the parked row and the arrangement above it. */
const PARKING_GAP = 40;

/**
 * Places the state nodes a saved arrangement said nothing about.
 *
 * Two cases, and the distinction matters more than the algorithm does:
 *
 * **Nothing is placed** — the machine has never been arranged, which is what
 * `GET /diagrams/{entityName}` answering 404 means. The whole graph is laid out left to right by Dagre.
 * This is the fallback the contract describes.
 *
 * **Some are placed** — states were added to the machine after it was last arranged. Those are *parked* in
 * a row below everything already positioned, and nothing else moves. Re-running Dagre over the whole graph
 * would be the obvious thing to do and is wrong: it would rearrange every state the user positioned by
 * hand because one new state appeared, and the modeler has no way to undo that on their behalf.
 */
@Injectable({ providedIn: 'root' })
export class DagreLayoutService {
  /**
   * Returns the nodes with positions filled in for the keys in `unplaced`. Nodes not named there are
   * returned untouched, and the input array is never mutated — ng-diagram's model is the owner of these
   * objects, and its reactivity runs through the adapter rather than through in-place writes.
   */
  place(nodes: StateNode[], edges: TransitionEdge[], unplaced: Iterable<string>): StateNode[] {
    const unplacedKeys = new Set(unplaced);
    if (unplacedKeys.size === 0) return nodes;
    return nodes.length === unplacedKeys.size ? this.layoutAll(nodes, edges) : parkBelow(nodes, unplacedKeys);
  }

  /** Layered left-to-right, which reads the way a state machine is described: initial state on the left. */
  private layoutAll(nodes: StateNode[], edges: TransitionEdge[]): StateNode[] {
    const graph = new dagre.graphlib.Graph();
    graph.setGraph({ rankdir: 'LR', nodesep: 30, ranksep: 50 });
    graph.setDefaultEdgeLabel(() => ({}));

    nodes.forEach((node) => graph.setNode(node.id, { ...(node.size ?? ESTIMATED_NODE_SIZE) }));
    // Only edges whose ends are both nodes of this graph: a transition may name a state the machine no
    // longer declares, and Dagre would silently invent a node for it and lay out a phantom.
    const nodeIds = new Set(nodes.map((node) => node.id));
    edges.filter((edge) => nodeIds.has(edge.source) && nodeIds.has(edge.target)).forEach((edge) => graph.setEdge(edge.source, edge.target));

    dagre.layout(graph);

    return nodes.map((node) => {
      const placed = graph.node(node.id);
      // Dagre reports a node's centre; ng-diagram positions by its top-left corner.
      const size = node.size ?? ESTIMATED_NODE_SIZE;
      return { ...node, position: { x: placed.x - size.width / 2, y: placed.y - size.height / 2 } };
    });
  }
}

// region private helper functions
/**
 * Parks the unplaced nodes in a row under the bounding box of the placed ones. Deterministic, so two
 * reloads before the next save show the same thing, and visibly "new" rather than hidden under an existing
 * node — which is what placing them at the origin would risk.
 */
function parkBelow(nodes: StateNode[], unplacedKeys: Set<string>): StateNode[] {
  const placed = nodes.filter((node) => !unplacedKeys.has(node.id));
  const left = Math.min(...placed.map((node) => node.position.x));
  const bottom = Math.max(...placed.map((node) => node.position.y + (node.size?.height ?? ESTIMATED_NODE_SIZE.height)));

  let column = 0;
  return nodes.map((node) => {
    if (!unplacedKeys.has(node.id)) return node;
    const position = { x: left + column * (ESTIMATED_NODE_SIZE.width + PARKING_GAP), y: bottom + PARKING_GAP };
    column++;
    return { ...node, position };
  });
}
// endregion
