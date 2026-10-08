import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { BOUNDARY_EVENT_NODE_SIZE, WORKFLOW_NODE_TYPE, WorkflowEdge, WorkflowElementKind, WorkflowNode } from '../workflow-graph';
import { WorkflowLayoutService } from './workflow-layout.service';

function node(id: string, kind: WorkflowElementKind = 'role'): WorkflowNode {
  return { id, type: WORKFLOW_NODE_TYPE, position: { x: 0, y: 0 }, autoSize: true, data: { kind, label: id } };
}

function edge(source: string, target: string): WorkflowEdge {
  return { id: `${source}->${target}`, source, target, data: {} };
}

describe('WorkflowLayoutService', () => {
  let service: WorkflowLayoutService;

  beforeEach(() => {
    service = TestBed.inject(WorkflowLayoutService);
  });

  it('places every node it is given', () => {
    const placed = service.place([node('role:clerk'), node('artifact:order', 'artifact')], [edge('role:clerk', 'artifact:order')]);

    expect(placed.map((each) => each.id)).toEqual(['role:clerk', 'artifact:order']);
    expect(placed.every((each) => Number.isFinite(each.position.x) && Number.isFinite(each.position.y))).toBe(true);
  });

  /** Left to right, because that is the direction the relation reads in: a role owns an artifact. */
  it('puts the target of a relation to the right of its source', () => {
    const [role, artifact] = service.place([node('role:clerk'), node('artifact:order', 'artifact')], [edge('role:clerk', 'artifact:order')]);

    expect(artifact.position.x).toBeGreaterThan(role.position.x);
  });

  it('separates two nodes of the same rank', () => {
    const [first, second] = service.place([node('artifact:a', 'artifact'), node('artifact:b', 'artifact')], []);

    expect(first.position).not.toEqual(second.position);
  });

  // ng-diagram's model owns these objects and its reactivity runs through the adapter, so a layout that
  // wrote positions in place would be a change the renderer never hears about.
  it('returns new nodes rather than repositioning the ones handed in', () => {
    const original = node('role:clerk');
    const placed = service.place([original], []);

    expect(placed[0]).not.toBe(original);
    expect(original.position).toEqual({ x: 0, y: 0 });
  });

  it('has nothing to place for an empty graph', () => {
    const nodes: WorkflowNode[] = [];

    expect(service.place(nodes, [])).toBe(nodes);
  });

  /**
   * An edge may name an element the catalog no longer holds — the converter draws such a reference as an
   * `unresolved` node, but a perspective that chose to drop it would leave the edge dangling. Dagre would
   * invent a node for the missing end and lay out a phantom, so the edge is dropped from the layout
   * instead.
   */
  it('ignores an edge whose other end is not in the graph', () => {
    const placed = service.place([node('role:clerk')], [edge('role:clerk', 'artifact:deleted')]);

    expect(placed.map((each) => each.id)).toEqual(['role:clerk']);
  });

  describe('boundary events', () => {
    /** A boundary event on `task:review`, as the converter emits one. */
    function boundary(id: string): WorkflowNode {
      return { ...node(id, 'event'), size: { ...BOUNDARY_EVENT_NODE_SIZE }, autoSize: false, data: { kind: 'event', label: id, attachedTo: 'task:review', interrupting: true } };
    }

    // Lanes off: the event is still pinned, and its task is given the box it was laid out as so the visible
    // card ends where the event sits.
    it('pins the event across the lower edge of its task, and states the task’s box', () => {
      const [review, overdue] = service.place([node('task:review', 'task'), boundary('event:overdue')], []);

      expect(review.autoSize).toBe(false);
      expect(overdue.position.y + BOUNDARY_EVENT_NODE_SIZE.height / 2).toBe(review.position.y + (review.size?.height as number));
      expect(overdue.position.x).toBeGreaterThan(review.position.x);
    });

    it('ranks what depends on the event after the event’s task', () => {
      const [review, , escalate] = service.place(
        [node('task:review', 'task'), boundary('event:overdue'), node('task:escalate', 'task')],
        [edge('event:overdue', 'task:escalate')],
      );

      expect(escalate.position.x).toBeGreaterThan(review.position.x);
    });

    it('leaves a task with no boundary event to be measured', () => {
      const [review] = service.place([node('task:review', 'task')], []);

      expect(review.autoSize).toBe(true);
      expect(review.size).toBeUndefined();
    });
  });
});
