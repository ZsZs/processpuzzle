import dagre from '@dagrejs/dagre';
import { Injectable } from '@angular/core';
import { WorkflowEdge, WorkflowNode } from '../workflow-graph';

/**
 * Estimated node box, used only to space the graph out. The real nodes are auto-sized by their content, so
 * their measured size is not known until ng-diagram has rendered them once — laying out against a stated
 * estimate is both simpler and stable across reloads, which a measured layout would not be. The width is
 * the width {@link WorkflowElementNodeComponent} fixes its box at, so the estimate is only ever wrong about
 * the height of a description.
 */
const ESTIMATED_NODE_SIZE = { width: 170, height: 76 };

/**
 * Places every node of a modeler perspective, left to right.
 *
 * Every node, on every build — which is what makes this so much smaller than base-state's
 * `DagreLayoutService`. That one joins a topology to a *saved* arrangement, so it has the harder job of
 * placing only the nodes the arrangement said nothing about and leaving the user's own positions alone.
 * These diagrams persist no arrangement, so there is nothing to preserve and no way for a fresh layout to
 * undo work someone did by hand.
 *
 * Left to right because that is the direction the relations read in: a role owns an artifact, a task
 * consumes one. `ranksep` is the generous one of the two gaps, since the columns are what the edges cross.
 */
@Injectable({ providedIn: 'root' })
export class WorkflowLayoutService {
  /**
   * Returns the nodes with positions filled in. The input array is never mutated — ng-diagram's model is
   * the owner of these objects, and its reactivity runs through the adapter rather than through in-place
   * writes.
   */
  place(nodes: WorkflowNode[], edges: WorkflowEdge[]): WorkflowNode[] {
    if (nodes.length === 0) return nodes;

    const graph = new dagre.graphlib.Graph();
    graph.setGraph({ rankdir: 'LR', nodesep: 24, ranksep: 90 });
    graph.setDefaultEdgeLabel(() => ({}));

    nodes.forEach((node) => graph.setNode(node.id, { ...(node.size ?? ESTIMATED_NODE_SIZE) }));
    // Only edges whose ends are both nodes of this graph: a converter may name an element the catalog no
    // longer holds, and Dagre would silently invent a node for it and lay out a phantom.
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
