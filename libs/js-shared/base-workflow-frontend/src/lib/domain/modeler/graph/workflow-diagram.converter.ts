import { DiagramEdgeLayout, DiagramNodeLayout, DiagramViewport, NodeSize, Point, WorkflowDiagram } from '../models/workflow-diagram';
import { WorkflowEdge, WorkflowNode } from '../workflow-graph';

/**
 * Joins a laid-out modeler graph to a saved arrangement, and takes the arrangement back off it again:
 * {@link applySavedLayout} is the read direction, {@link toDiagram} the write one.
 *
 * The two resources are separate on the wire for good reasons (see `WorkflowDiagram`'s class comment),
 * which leaves exactly one place that has to know how they fit together. This is it: nothing else in the
 * modeler reads a `DiagramNodeLayout` row or writes one.
 *
 * Three rules are worth stating, because each is a decision rather than a mechanical mapping.
 *
 * **The automatic layout runs first, and the saved one overrides it.** This is the one real departure from
 * base-state's `StateMachineGraphConverter`, which reports the states a layout does not mention as
 * `unplacedStateKeys` and has `DagreLayoutService` place only those. Here `SwimlaneLayoutService` has
 * already placed *everything* by the time this runs — it has to, since it is what computes each lane's
 * band from the tasks in it — so a node the saved layout does not mention needs no special handling: it
 * simply keeps its computed position. That is what makes a task added since the last save appear in the
 * right lane rather than at the origin.
 *
 * **A stale row is ignored, not an error.** `updateWorkflow` is free to drop a task, so a row naming a
 * node the diagram no longer draws is normal; the contract's `saveWorkflowDiagram` says as much.
 *
 * **A row for a node that is not on screen is kept, not pruned.** The modeler's layer toggles can take
 * the artifacts or the tools off the canvas entirely, and saving in that state must not be how a user
 * loses the positions they gave them. So {@link toDiagram} merges: what the model holds wins, and rows of
 * `previous` naming nodes the model does not hold survive. Pruning still happens — a node that is gone
 * from the *workflow* is gone from the canvas whatever the toggles say, and its row is only retained
 * while the layer that draws it is off.
 */

/**
 * The graph with the saved arrangement applied over the computed one. Neither input array is mutated —
 * ng-diagram's model owns these objects and its reactivity runs through the adapter rather than through
 * in-place writes.
 *
 * An absent `diagram` returns the inputs unchanged, which is the "never arranged" case and the normal
 * starting point.
 */
export function applySavedLayout(nodes: WorkflowNode[], edges: WorkflowEdge[], diagram?: WorkflowDiagram): { nodes: WorkflowNode[]; edges: WorkflowEdge[] } {
  if (!diagram) return { nodes, edges };

  // Indexed once rather than searched per node: a workflow with thirty tasks and thirty saved rows would
  // otherwise be quadratic in two lists that grow together.
  const nodeLayouts = new Map(diagram.nodes.map((node) => [node.nodeId, node]));
  const edgeLayouts = new Map(diagram.edges.map((edge) => [edge.edgeId, edge]));

  return {
    nodes: nodes.map((node) => applyNodeLayout(node, nodeLayouts.get(node.id))),
    edges: edges.map((edge) => applyEdgeLayout(edge, edgeLayouts.get(edge.id))),
  };
}

/**
 * The arrangement as the canvas now stands, in the shape the layout resource persists.
 *
 * Deliberately narrow: positions, sizes, port anchors, waypoints and the viewport, and nothing else. What
 * each node *stands for* belongs to the workflow and its four catalogs, and writing any of it here —
 * through a resource with a version of its own — is how the two would start to disagree.
 *
 * `version` is carried over from `previous` when given, because the write is optimistic-locked: saving an
 * arrangement read at version 3 has to say 3, or the server cannot tell a concurrent edit from a stale one.
 */
export function toDiagram(
  workflowId: string,
  nodes: WorkflowNode[],
  edges: WorkflowEdge[],
  viewport?: DiagramViewport,
  previous?: WorkflowDiagram,
): WorkflowDiagram {
  const drawnNodeIds = new Set(nodes.map((node) => node.id));
  const drawnEdgeIds = new Set(edges.map((edge) => edge.id));

  return new WorkflowDiagram({
    workflowId,
    nodes: [...nodes.map(toNodeLayout), ...(previous?.nodes ?? []).filter((row) => !drawnNodeIds.has(row.nodeId))],
    edges: [...edges.map(toEdgeLayout), ...(previous?.edges ?? []).filter((row) => !drawnEdgeIds.has(row.edgeId))],
    viewport,
    orgKey: previous?.orgKey,
    version: previous?.version,
    createdAt: previous?.createdAt,
    updatedAt: previous?.updatedAt,
  });
}

// region private helper functions
/**
 * One node moved to where it was saved.
 *
 * `autoSize: false` goes with every stated size, and is not decoration: ng-diagram's `NodeSizeDirective`
 * defaults `autoSize` to **true** and, when it is true, *discards* an explicit `size` and re-applies its
 * own. A lane restored without it would keep none of its saved height. A row with no size leaves both the
 * size and the flag exactly as the automatic layout left them.
 */
function applyNodeLayout(node: WorkflowNode, layout?: DiagramNodeLayout): WorkflowNode {
  if (!layout) return node;
  const positioned = { ...node, position: { x: layout.position.x, y: layout.position.y } };
  if (!layout.size) return positioned;
  return { ...positioned, size: { width: layout.size.width, height: layout.size.height }, autoSize: false };
}

/**
 * One edge routed as it was saved.
 *
 * Waypoints are only honoured in `manual` routing mode — in `auto` mode ng-diagram owns `points` and
 * recomputes them — so the mode is set from whether the row actually recorded any. An edge with no
 * recorded waypoints keeps the automatic routing it had.
 *
 * The ports are taken from the row even when absent, which *unpins* an edge the converter had pinned. That
 * is deliberate: the row is the arrangement the user last saved, and a saved edge with no anchors is one
 * they left floating.
 */
function applyEdgeLayout(edge: WorkflowEdge, layout?: DiagramEdgeLayout): WorkflowEdge {
  if (!layout) return edge;
  const points = layout.points ?? [];
  return {
    ...edge,
    sourcePort: layout.sourcePort,
    targetPort: layout.targetPort,
    routing: layout.routing,
    ...(points.length > 0 ? { points: points.map((point) => ({ x: point.x, y: point.y })), routingMode: 'manual' as const } : {}),
  };
}

function toNodeLayout(node: WorkflowNode): DiagramNodeLayout {
  return new DiagramNodeLayout({
    nodeId: node.id,
    position: new Point({ x: node.position.x, y: node.position.y }),
    size: node.size ? new NodeSize({ width: node.size.width, height: node.size.height }) : undefined,
  });
}

/**
 * Only waypoints the user placed are saved. In `auto` mode `points` holds whatever the routing algorithm
 * last computed, and persisting that would freeze a derived path into the layout — the next load would
 * replay stale geometry instead of routing around wherever the nodes now sit.
 */
function toEdgeLayout(edge: WorkflowEdge): DiagramEdgeLayout {
  return new DiagramEdgeLayout({
    edgeId: edge.id,
    points: edge.routingMode === 'manual' ? (edge.points ?? []).map((point) => new Point({ x: point.x, y: point.y })) : [],
    sourcePort: edge.sourcePort,
    targetPort: edge.targetPort,
    routing: edge.routing,
  });
}
// endregion
