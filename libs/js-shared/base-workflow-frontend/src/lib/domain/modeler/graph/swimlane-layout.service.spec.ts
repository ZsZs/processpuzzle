import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import {
  elementEdgeId,
  elementNodeId,
  isLaneNode,
  laneNodeId,
  WORKFLOW_LANE_TYPE,
  WORKFLOW_NODE_TYPE,
  WORKFLOW_RELATION_EDGE_TYPE,
  WorkflowEdge,
  WorkflowElementKind,
  WorkflowNode,
  WorkflowRelation,
} from '../workflow-graph';
import { SwimlaneLayoutService } from './swimlane-layout.service';

/** A lane, as the converter emits one: a group node with no size, which is this service's job to give it. */
function lane(roleId: string): WorkflowNode {
  return { id: laneNodeId(roleId), type: WORKFLOW_LANE_TYPE, isGroup: true, highlighted: false, position: { x: 0, y: 0 }, data: { kind: 'role', label: roleId } };
}

function element(kind: WorkflowElementKind, id: string, laneRoleId?: string): WorkflowNode {
  return {
    id: elementNodeId(kind, id),
    type: WORKFLOW_NODE_TYPE,
    position: { x: 0, y: 0 },
    autoSize: true,
    ...(laneRoleId === undefined ? {} : { groupId: laneNodeId(laneRoleId) }),
    data: { kind, label: id },
  };
}

function edge(source: string, target: string, relation: WorkflowRelation): WorkflowEdge {
  return { id: elementEdgeId(source, target), source, target, type: WORKFLOW_RELATION_EDGE_TYPE, data: { relation } };
}

const REVIEW = elementNodeId('task', 'review-order');
const APPROVE = elementNodeId('task', 'approve-shipment');
const CONFIRM = elementNodeId('task', 'confirm-delivery');
const ORDER = elementNodeId('artifact', 'order-entity');

/** The seeded workflow's shape: a three-task chain crossing twice between the clerk's and manager's lanes. */
function seededNodes(): WorkflowNode[] {
  return [lane('clerk'), lane('manager'), element('task', 'review-order', 'clerk'), element('task', 'approve-shipment', 'manager'), element('task', 'confirm-delivery', 'clerk')];
}

function seededEdges(): WorkflowEdge[] {
  return [edge(REVIEW, APPROVE, 'sequence'), edge(APPROVE, CONFIRM, 'sequence')];
}

function positionOf(placed: WorkflowNode[], id: string) {
  return placed.find((node) => node.id === id)?.position;
}

describe('SwimlaneLayoutService', () => {
  let service: SwimlaneLayoutService;

  beforeEach(() => {
    service = TestBed.inject(SwimlaneLayoutService);
  });

  describe('the columns', () => {
    it('advances one column per step of the chain', () => {
      const placed = service.place(seededNodes(), seededEdges());
      const xs = [REVIEW, APPROVE, CONFIRM].map((id) => positionOf(placed, id)?.x as number);

      expect(xs[0]).toBeLessThan(xs[1]);
      expect(xs[1]).toBeLessThan(xs[2]);
    });

    /**
     * What makes it a swimlane diagram rather than two flows. A task at the same point in the chain has to
     * sit at the same x whichever lane performs it, or a column means nothing.
     */
    it('gives two lanes’ tasks at the same step the same x', () => {
      const nodes = [lane('clerk'), lane('manager'), element('task', 'review-order', 'clerk'), element('task', 'approve-shipment', 'manager')];
      const placed = service.place(nodes, []);

      expect(positionOf(placed, REVIEW)?.x).toBe(positionOf(placed, APPROVE)?.x);
    });

    // Dagre reports ranks two apart, having reserved every other one for edge labels. Used unrenumbered they
    // would leave an empty column between every pair of tasks and double the diagram's width.
    it('leaves no empty column between consecutive steps', () => {
      const placed = service.place(seededNodes(), seededEdges());
      const xs = [REVIEW, APPROVE, CONFIRM].map((id) => positionOf(placed, id)?.x as number);

      expect(xs[1] - xs[0]).toBe(xs[2] - xs[1]);
    });

    it('keeps every task clear of the lane’s header column', () => {
      const placed = service.place(seededNodes(), seededEdges());

      expect(Math.min(...[REVIEW, APPROVE, CONFIRM].map((id) => positionOf(placed, id)?.x as number))).toBeGreaterThanOrEqual(140);
    });

    /**
     * The reason only the flow edges are given to Dagre. An artifact ranked as a column of its own would
     * push every task after it sideways, which would make the columns depend on a toggle.
     */
    it('is unmoved by turning the data layer on', () => {
      const withoutData = service.place(seededNodes(), seededEdges());
      const withData = service.place([...seededNodes(), element('artifact', 'order-entity')], [...seededEdges(), edge(ORDER, REVIEW, 'input')]);

      expect([REVIEW, APPROVE, CONFIRM].map((id) => positionOf(withData, id)?.x)).toEqual([REVIEW, APPROVE, CONFIRM].map((id) => positionOf(withoutData, id)?.x));
    });
  });

  describe('the bands', () => {
    it('gives every lane the same x and the same width', () => {
      const lanes = service.place(seededNodes(), seededEdges()).filter(isLaneNode);

      expect(new Set(lanes.map((laneNode) => laneNode.position.x)).size).toBe(1);
      expect(new Set(lanes.map((laneNode) => laneNode.size?.width)).size).toBe(1);
    });

    it('stacks the lanes without overlapping them', () => {
      const lanes = service.place(seededNodes(), seededEdges()).filter(isLaneNode);
      const [first, second] = lanes;

      expect(second.position.y).toBeGreaterThanOrEqual(first.position.y + (first.size?.height as number));
    });

    it('keeps each task inside the band of the lane performing it', () => {
      const placed = service.place(seededNodes(), seededEdges());
      const clerkLane = placed.filter(isLaneNode).find((node) => node.id === laneNodeId('clerk'));

      [REVIEW, CONFIRM].forEach((id) => {
        const task = placed.find((node) => node.id === id) as WorkflowNode;
        expect(task.position.y).toBeGreaterThanOrEqual((clerkLane as WorkflowNode).position.y);
        expect(task.position.y + (task.size?.height as number)).toBeLessThanOrEqual((clerkLane as WorkflowNode).position.y + ((clerkLane as WorkflowNode).size?.height as number));
      });
    });

    /**
     * A lane's depth is its busiest column, not its task count — three tasks in three columns is one row
     * deep. Without this a linear chain would make every lane as tall as the whole workflow.
     */
    it('keeps a lane one row deep when its tasks are in different columns', () => {
      const oneLane = service.place(seededNodes(), seededEdges()).filter(isLaneNode)[0];
      const twoInAColumn = service
        .place([lane('clerk'), element('task', 'review-order', 'clerk'), element('task', 'approve-shipment', 'clerk')], [])
        .filter(isLaneNode)[0];

      expect(twoInAColumn.size?.height).toBeGreaterThan(oneLane.size?.height as number);
    });

    it('stacks two tasks of one lane that land in the same column', () => {
      const placed = service.place([lane('clerk'), element('task', 'review-order', 'clerk'), element('task', 'approve-shipment', 'clerk')], []);

      expect(positionOf(placed, REVIEW)?.x).toBe(positionOf(placed, APPROVE)?.x);
      expect(positionOf(placed, REVIEW)?.y).not.toBe(positionOf(placed, APPROVE)?.y);
    });

    it('gives a lane with no tasks a band of its own rather than a line', () => {
      const empty = service.place([lane('clerk'), lane('auditor'), element('task', 'review-order', 'clerk')], []).filter(isLaneNode);

      expect(empty[1].size?.height).toBeGreaterThan(0);
    });
  });

  describe('sizes', () => {
    /**
     * The finding that would otherwise ship silently. ng-diagram defaults `autoSize` to true and, when it
     * is true, **throws the explicit size away** and re-applies its own default — so a lane emitted without
     * this keeps none of the width and height computed here.
     */
    it('turns auto-sizing off on everything it sizes', () => {
      const placed = service.place([...seededNodes(), element('artifact', 'order-entity')], [...seededEdges(), edge(ORDER, REVIEW, 'input')]);

      expect(placed.every((node) => node.autoSize === false)).toBe(true);
      expect(placed.every((node) => node.size !== undefined)).toBe(true);
    });

    it('keeps a lane a group node', () => {
      const placed = service.place(seededNodes(), seededEdges());

      expect(placed.filter(isLaneNode).map((node) => node.id)).toEqual([laneNodeId('clerk'), laneNodeId('manager')]);
    });

    it('leaves every lane wide enough for its widest column', () => {
      const lanes = service.place(seededNodes(), seededEdges()).filter(isLaneNode);
      const rightmost = Math.max(...[REVIEW, APPROVE, CONFIRM].map((id) => positionOf(service.place(seededNodes(), seededEdges()), id)?.x as number));

      expect(lanes[0].size?.width).toBeGreaterThanOrEqual(rightmost + 170);
    });
  });

  describe('the strip under the lanes', () => {
    // Under rather than inside, because a node that is not a lane member but overlaps its box reads as if it
    // were one — and ng-diagram would draw it there quite happily.
    it('places artifacts and tools below every band', () => {
      const placed = service.place([...seededNodes(), element('artifact', 'order-entity'), element('tool', 'automated-check-tool')], [
        ...seededEdges(),
        edge(ORDER, REVIEW, 'input'),
        edge(CONFIRM, elementNodeId('tool', 'automated-check-tool'), 'tool'),
      ]);
      const bandBottom = Math.max(...placed.filter(isLaneNode).map((node) => node.position.y + (node.size?.height as number)));

      expect(positionOf(placed, ORDER)?.y).toBeGreaterThan(bandBottom);
      expect(positionOf(placed, elementNodeId('tool', 'automated-check-tool'))?.y).toBeGreaterThan(bandBottom);
    });

    it('puts an artifact in the column of the task it is joined to, so its line is short and vertical', () => {
      const tool = elementNodeId('tool', 'automated-check-tool');
      const placed = service.place([...seededNodes(), element('tool', 'automated-check-tool')], [...seededEdges(), edge(CONFIRM, tool, 'tool')]);

      expect(positionOf(placed, tool)?.x).toBe(positionOf(placed, CONFIRM)?.x);
    });

    it('stacks two loose nodes that share a column', () => {
      const tool = elementNodeId('tool', 'automated-check-tool');
      const placed = service.place([...seededNodes(), element('artifact', 'order-entity'), element('tool', 'automated-check-tool')], [
        ...seededEdges(),
        edge(ORDER, REVIEW, 'input'),
        edge(REVIEW, tool, 'tool'),
      ]);

      expect(positionOf(placed, ORDER)?.x).toBe(positionOf(placed, tool)?.x);
      expect(positionOf(placed, ORDER)?.y).not.toBe(positionOf(placed, tool)?.y);
    });

    it('places a loose node joined to nothing in the first column rather than at the origin', () => {
      const placed = service.place([...seededNodes(), element('artifact', 'order-entity')], seededEdges());

      expect(positionOf(placed, ORDER)?.x).toBe(positionOf(placed, REVIEW)?.x);
    });
  });

  describe('degenerate input', () => {
    it('returns an empty graph untouched', () => {
      expect(service.place([], [])).toEqual([]);
    });

    /**
     * The Lanes toggle turned off. A flat left-to-right flow is what `WorkflowLayoutService` already
     * produces, so a graph with no lanes is handed to it whole rather than answered twice.
     */
    it('falls back to the flow layout when there are no lanes', () => {
      const placed = service.place([element('task', 'review-order'), element('task', 'approve-shipment')], [edge(REVIEW, APPROVE, 'sequence')]);

      // The flow layout's own signature: the chain still advances left to right, but nothing was sized or
      // banded — no lane box, and `autoSize` left as the converter set it.
      expect(positionOf(placed, REVIEW)?.x as number).toBeLessThan(positionOf(placed, APPROVE)?.x as number);
      expect(placed.filter(isLaneNode)).toEqual([]);
      expect(placed.every((node) => node.autoSize === true)).toBe(true);
    });

    it('places a lane whose task depends on itself, drawing no phantom loop', () => {
      const placed = service.place([lane('clerk'), element('task', 'review-order', 'clerk')], [edge(REVIEW, REVIEW, 'sequence')]);

      expect(positionOf(placed, REVIEW)).toBeDefined();
      expect(placed.filter(isLaneNode)[0].size?.height).toBeGreaterThan(0);
    });

    // A typo in the free TAGS control can make the dependencies cyclic. Dagre tolerates it; so must this.
    it('places every task of a cyclic flow', () => {
      const placed = service.place(seededNodes(), [...seededEdges(), edge(CONFIRM, REVIEW, 'sequence')]);

      expect([REVIEW, APPROVE, CONFIRM].every((id) => positionOf(placed, id) !== undefined)).toBe(true);
    });
  });

  describe('the contract it shares with the flow layout', () => {
    it('never mutates the nodes it was given', () => {
      const nodes = seededNodes();
      const snapshot = JSON.stringify(nodes);

      service.place(nodes, seededEdges());

      expect(JSON.stringify(nodes)).toBe(snapshot);
    });

    it('returns the nodes in the order it was given them, so the lanes stay ahead of their tasks', () => {
      const nodes = seededNodes();

      expect(service.place(nodes, seededEdges()).map((node) => node.id)).toEqual(nodes.map((node) => node.id));
    });
  });
});
