import { describe, expect, it } from 'vitest';
import { BOUNDARY_EVENT_NODE_SIZE, elementEdgeId, WORKFLOW_NODE_TYPE, WorkflowEdge, WorkflowNode } from '../workflow-graph';
import { placeBoundaryEvents, rankingEdges } from './boundary-event-placement';

/** A task as a layout leaves it: placed and sized. */
function task(id: string, x = 200, y = 100): WorkflowNode {
  return { id, type: WORKFLOW_NODE_TYPE, position: { x, y }, size: { width: 170, height: 76 }, autoSize: false, data: { kind: 'task', label: id } };
}

/** A boundary event on `host`, not placed yet. */
function boundary(id: string, host: string): WorkflowNode {
  return {
    id,
    type: WORKFLOW_NODE_TYPE,
    position: { x: 0, y: 0 },
    size: { ...BOUNDARY_EVENT_NODE_SIZE },
    autoSize: false,
    data: { kind: 'event', label: id, attachedTo: host, interrupting: true },
  };
}

function edge(source: string, target: string): WorkflowEdge {
  return { id: elementEdgeId(source, target), source, target, data: { relation: 'sequence' } };
}

const centreOf = (node: WorkflowNode) => ({ x: node.position.x + BOUNDARY_EVENT_NODE_SIZE.width / 2, y: node.position.y + BOUNDARY_EVENT_NODE_SIZE.height / 2 });

describe('placeBoundaryEvents', () => {
  it('centres the event on its host’s lower edge, near the right corner', () => {
    const [, overdue] = placeBoundaryEvents([task('task:review'), boundary('event:overdue', 'task:review')]);

    expect(centreOf(overdue)).toEqual({ x: 200 + 170 - 24, y: 100 + 76 });
  });

  // From the right, so one boundary event leaves the middle of the edge — the data lines' port — clear.
  it('keeps a single event clear of the middle of the edge', () => {
    const [, overdue] = placeBoundaryEvents([task('task:review'), boundary('event:overdue', 'task:review')]);

    expect(overdue.position.x).toBeGreaterThan(200 + 170 / 2);
  });

  it('spreads several events of one host leftwards, in declaration order, all on the card', () => {
    const placed = placeBoundaryEvents([task('task:review'), ...['a', 'b', 'c', 'd', 'e', 'f'].map((id) => boundary(`event:${id}`, 'task:review'))]);
    const xs = placed.slice(1).map((node) => centreOf(node).x);

    expect(xs).toEqual([...xs].sort((left, right) => right - left));
    expect(new Set(xs).size).toBe(xs.length);
    expect(Math.min(...xs)).toBeGreaterThanOrEqual(200);
  });

  it('gives each host its own spread', () => {
    const [, , first, second] = placeBoundaryEvents([task('task:a', 0), task('task:b', 400), boundary('event:a', 'task:a'), boundary('event:b', 'task:b')]);

    expect(centreOf(second).x - centreOf(first).x).toBe(400);
  });

  it('leaves an event whose host is not drawn where it was', () => {
    const lost = boundary('event:lost', 'task:deleted');

    expect(placeBoundaryEvents([lost])[0]).toBe(lost);
  });

  it('never mutates the nodes it was given', () => {
    const nodes = [task('task:review'), boundary('event:overdue', 'task:review')];
    const snapshot = JSON.stringify(nodes);

    placeBoundaryEvents(nodes);

    expect(JSON.stringify(nodes)).toBe(snapshot);
  });
});

describe('rankingEdges', () => {
  const nodes = [task('task:review'), boundary('event:overdue', 'task:review'), task('task:escalate')];

  it('reads an edge out of a boundary event as one out of its host', () => {
    expect(rankingEdges(nodes, [edge('event:overdue', 'task:escalate')]).map((each) => [each.source, each.target])).toEqual([['task:review', 'task:escalate']]);
  });

  it('leaves every other edge as it was', () => {
    expect(rankingEdges(nodes, [edge('task:review', 'task:escalate')]).map((each) => [each.source, each.target])).toEqual([['task:review', 'task:escalate']]);
  });

  it('drops an edge that would join a host to itself', () => {
    expect(rankingEdges(nodes, [edge('event:overdue', 'task:review')])).toEqual([]);
  });
});
