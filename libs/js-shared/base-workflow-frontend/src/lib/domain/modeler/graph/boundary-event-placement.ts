import { BOUNDARY_EVENT_NODE_SIZE, isBoundaryNode, WorkflowEdge, WorkflowNode } from '../workflow-graph';

/**
 * The box a host task is assumed to have when it states none — the card both layouts lay a task out as.
 * Only the flat layout's tasks can lack a `size`, and it states one on every task that carries a boundary
 * event, so this is a fallback rather than a measurement.
 */
const HOST_SIZE = { width: 170, height: 76 };

/**
 * How far in from the host's right edge the first boundary event's centre sits, and the most its neighbours
 * are spaced apart. From the right rather than centred, so that a single boundary event leaves the card's
 * bottom port clear — that is where the task's data and tool lines leave it. 40px apart puts the second one
 * just right of the middle as well; more than three are squeezed to fit rather than run off the card.
 */
const RIGHT_INSET = 24;
const PITCH = 40;

/**
 * Pins every boundary event across the lower edge of the task it is attached to: centred on the edge, so the
 * circle half overlaps the card as BPMN draws it, and spread leftwards from the right corner when a task has
 * several, in declaration order.
 *
 * Run *after* a layout has placed the tasks — by both layouts, and again by `applySavedLayout` once a saved
 * arrangement has moved them — because the event's position is a fact about its host's, not one of its own.
 * A boundary event whose host is not drawn is left where it was; the converter draws a dangling host, so
 * this only happens to a graph built by hand.
 *
 * Never mutates its input, for the reason every layout here gives.
 */
export function placeBoundaryEvents(nodes: WorkflowNode[]): WorkflowNode[] {
  const nodesById = new Map(nodes.map((node) => [node.id, node]));
  const boundaryIdsByHost = new Map<string, string[]>();
  nodes.filter(isBoundaryNode).forEach((node) => {
    const hostId = node.data.attachedTo as string;
    boundaryIdsByHost.set(hostId, [...(boundaryIdsByHost.get(hostId) ?? []), node.id]);
  });

  return nodes.map((node) => {
    if (!isBoundaryNode(node)) return node;
    const hostId = node.data.attachedTo as string;
    const host = nodesById.get(hostId);
    if (!host) return node;

    const siblings = boundaryIdsByHost.get(hostId) as string[];
    const hostSize = host.size ?? HOST_SIZE;
    const size = node.size ?? BOUNDARY_EVENT_NODE_SIZE;
    const span = hostSize.width - 2 * RIGHT_INSET;
    const pitch = siblings.length > 1 ? Math.min(PITCH, span / (siblings.length - 1)) : 0;
    const centre = {
      x: host.position.x + hostSize.width - RIGHT_INSET - siblings.indexOf(node.id) * pitch,
      y: host.position.y + hostSize.height,
    };
    return {
      ...node,
      position: { x: centre.x - size.width / 2, y: centre.y - size.height / 2 },
      size: { width: size.width, height: size.height },
      autoSize: false,
    };
  });
}

/**
 * The edges as a layout should rank them: an edge leaving a boundary event leaves from its host instead.
 *
 * A boundary event takes no column, so to a ranking it does not exist — but what depends on it still comes
 * *after* its host, and dropping the edge would rank that dependent as a root, in the first column. Read as
 * an edge from the host, it lands one column on, which is where the flow out of the event actually goes.
 * An edge that would join the host to itself is dropped.
 */
export function rankingEdges(nodes: WorkflowNode[], edges: WorkflowEdge[]): WorkflowEdge[] {
  const hostOf = new Map(nodes.filter(isBoundaryNode).map((node) => [node.id, node.data.attachedTo as string]));
  return edges
    .map((edge) => ({ ...edge, source: hostOf.get(edge.source) ?? edge.source, target: hostOf.get(edge.target) ?? edge.target }))
    .filter((edge) => edge.source !== edge.target);
}
