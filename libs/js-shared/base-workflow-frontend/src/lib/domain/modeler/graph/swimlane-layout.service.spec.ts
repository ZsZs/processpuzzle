import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import {
  BOUNDARY_EVENT_NODE_SIZE,
  elementEdgeId,
  elementNodeId,
  EVENT_NODE_SIZE,
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

    // One node per state: `[CONFIRMED]` is what review-order leaves and approve-shipment reads, and it hangs
    // under the task that confirms the order.
    it('hangs an object in a state under the task that leaves it in that state', () => {
      const confirmed = elementNodeId('artifact', 'order-entity[CONFIRMED]');
      const placed = service.place([...seededNodes(), element('artifact', 'order-entity[CONFIRMED]')], [...seededEdges(), edge(REVIEW, confirmed, 'output'), edge(confirmed, APPROVE, 'input')]);

      expect(positionOf(placed, confirmed)?.x).toBe(positionOf(placed, REVIEW)?.x);
    });

    // Even where a reader comes earlier — a flow looping back — the producer is what the node is about.
    it('prefers the producer’s column to an earlier reader’s', () => {
      const placed = service.place([...seededNodes(), element('artifact', 'order-entity')], [...seededEdges(), edge(ORDER, REVIEW, 'input'), edge(CONFIRM, ORDER, 'output')]);

      expect(positionOf(placed, ORDER)?.x).toBe(positionOf(placed, CONFIRM)?.x);
    });

    it('places a loose node joined to nothing in the first column rather than at the origin', () => {
      const placed = service.place([...seededNodes(), element('artifact', 'order-entity')], seededEdges());

      expect(positionOf(placed, ORDER)?.x).toBe(positionOf(placed, REVIEW)?.x);
    });
  });

  describe('the events', () => {
    const START = elementNodeId('start', 'order-drafted');
    const END = elementNodeId('end', 'end');

    /** An event as the converter emits one: a fixed circle, already in its preliminary lane. */
    function event(kind: 'start' | 'end', id: string, laneRoleId: string): WorkflowNode {
      return { ...element(kind, id, laneRoleId), size: { ...EVENT_NODE_SIZE }, autoSize: false };
    }

    /** The seeded chain opened by a start event in the clerk's lane and closed by the end. */
    function withEvents(endLane = 'clerk'): { nodes: WorkflowNode[]; edges: WorkflowEdge[] } {
      return {
        nodes: [...seededNodes(), event('start', 'order-drafted', 'clerk'), event('end', 'end', endLane)],
        edges: [...seededEdges(), edge(START, REVIEW, 'sequence'), edge(CONFIRM, END, 'sequence')],
      };
    }

    // They ride the sequence edges, so they take a column of their own on either side of the tasks.
    it('ranks a start event before the first task and the end after the last', () => {
      const { nodes, edges } = withEvents();
      const placed = service.place(nodes, edges);

      expect(positionOf(placed, START)?.x as number).toBeLessThan(positionOf(placed, REVIEW)?.x as number);
      expect(positionOf(placed, END)?.x as number).toBeGreaterThan(positionOf(placed, CONFIRM)?.x as number);
    });

    it('places both events inside a band rather than in the strip', () => {
      const { nodes, edges } = withEvents();
      const placed = service.place(nodes, edges);
      const clerkLane = placed.find((node) => node.id === laneNodeId('clerk')) as WorkflowNode;

      [START, END].forEach((id) => {
        const placedEvent = placed.find((node) => node.id === id) as WorkflowNode;
        expect(placedEvent.groupId).toBe(laneNodeId('clerk'));
        expect(placedEvent.position.y).toBeGreaterThanOrEqual(clerkLane.position.y);
        expect(placedEvent.position.y + (placedEvent.size?.height as number)).toBeLessThanOrEqual(clerkLane.position.y + (clerkLane.size?.height as number));
      });
    });

    // The circle stays the box the converter stated, centred on the row its neighbours' edges run along.
    it('keeps the event box and centres it in its cell', () => {
      const { nodes, edges } = withEvents();
      const placed = service.place(nodes, edges);
      const start = placed.find((node) => node.id === START) as WorkflowNode;
      const review = placed.find((node) => node.id === REVIEW) as WorkflowNode;

      expect(start.size).toEqual(EVENT_NODE_SIZE);
      expect(start.autoSize).toBe(false);
      expect(start.position.y + EVENT_NODE_SIZE.height / 2).toBe(review.position.y + (review.size?.height as number) / 2);
    });

    // The converter guesses the end's lane before the columns exist; the layout corrects it.
    it('moves the end into the lane of the feeder in the last column', () => {
      const { nodes, edges } = withEvents('manager');
      const placed = service.place(nodes, [...edges, edge(APPROVE, END, 'sequence')]);

      expect(placed.find((node) => node.id === END)?.groupId).toBe(laneNodeId('clerk'));
    });

    it('widens the lanes to hold the end event’s column', () => {
      const { nodes, edges } = withEvents();
      const placed = service.place(nodes, edges);
      const laneBox = placed.filter(isLaneNode)[0];

      expect(laneBox.size?.width as number).toBeGreaterThanOrEqual((positionOf(placed, END)?.x as number) + EVENT_NODE_SIZE.width);
    });

    it('feeds a start artifact from the strip under the start event', () => {
      const { nodes, edges } = withEvents();
      const placed = service.place([...nodes, element('artifact', 'order-entity')], [...edges, edge(ORDER, START, 'start')]);

      // The start event's column, left of the first task: the artifact card's box contains the circle's x.
      const orderX = positionOf(placed, ORDER)?.x as number;
      expect(orderX).toBeLessThan(positionOf(placed, REVIEW)?.x as number);
      expect(positionOf(placed, START)?.x as number).toBeGreaterThan(orderX);
      expect(positionOf(placed, START)?.x as number).toBeLessThan(orderX + 170);
    });
  });

  describe('intermediate events', () => {
    const PAID = elementNodeId('event', 'payment-received');
    const END = elementNodeId('end', 'end');

    /** An intermediate event as the converter emits one: a fixed circle in the lane of its dependency. */
    function event(id: string, laneRoleId: string): WorkflowNode {
      return { ...element('event', id, laneRoleId), size: { ...EVENT_NODE_SIZE }, autoSize: false };
    }

    /** The seeded chain with a caught payment between the review and the approval. */
    function withPayment(): { nodes: WorkflowNode[]; edges: WorkflowEdge[] } {
      return {
        nodes: [...seededNodes(), event('payment-received', 'clerk')],
        edges: [edge(REVIEW, PAID, 'sequence'), edge(PAID, APPROVE, 'sequence'), edge(APPROVE, CONFIRM, 'sequence')],
      };
    }

    // A node of the flow like a task, so it takes a column of its own between the two it sits between.
    it('ranks an event in a column of its own between its dependency and its dependent', () => {
      const { nodes, edges } = withPayment();
      const placed = service.place(nodes, edges);

      expect(positionOf(placed, PAID)?.x as number).toBeGreaterThan(positionOf(placed, REVIEW)?.x as number);
      expect(positionOf(placed, PAID)?.x as number).toBeLessThan(positionOf(placed, APPROVE)?.x as number);
    });

    it('places the event inside its band, keeping its box and centring it in its cell', () => {
      const { nodes, edges } = withPayment();
      const placed = service.place(nodes, edges);
      const paid = placed.find((node) => node.id === PAID) as WorkflowNode;
      const review = placed.find((node) => node.id === REVIEW) as WorkflowNode;

      expect(paid.groupId).toBe(laneNodeId('clerk'));
      expect(paid.size).toEqual(EVENT_NODE_SIZE);
      expect(paid.autoSize).toBe(false);
      expect(paid.position.y + EVENT_NODE_SIZE.height / 2).toBe(review.position.y + (review.size?.height as number) / 2);
    });

    it('moves the end into the lane of an event feeding it from the last column', () => {
      const { nodes, edges } = withPayment();
      const thrown = event('order-shipped', 'manager');
      const placed = service.place(
        [...nodes, thrown, { ...element('end', 'end', 'clerk'), size: { ...EVENT_NODE_SIZE }, autoSize: false }],
        [...edges, edge(CONFIRM, thrown.id, 'sequence'), edge(thrown.id, END, 'sequence')],
      );

      expect(placed.find((node) => node.id === END)?.groupId).toBe(laneNodeId('manager'));
    });
  });

  describe('boundary events', () => {
    const OVERDUE = elementNodeId('event', 'review-overdue');
    const ESCALATE = elementNodeId('task', 'escalate-review');

    /** A boundary event as the converter emits one: the small circle, in its host's lane, naming its host. */
    function boundary(id: string, host: string, laneRoleId: string): WorkflowNode {
      const node = element('event', id, laneRoleId);
      return { ...node, size: { ...BOUNDARY_EVENT_NODE_SIZE }, autoSize: false, data: { ...node.data, attachedTo: host, interrupting: true } };
    }

    /** The seeded chain, the review carrying a timer that escalates it to the manager. */
    function withOverdueReview(): { nodes: WorkflowNode[]; edges: WorkflowEdge[] } {
      return {
        nodes: [...seededNodes(), element('task', 'escalate-review', 'manager'), boundary('review-overdue', REVIEW, 'clerk')],
        edges: [...seededEdges(), edge(OVERDUE, ESCALATE, 'sequence')],
      };
    }

    it('pins the event across the lower edge of its task, inside the task’s width', () => {
      const { nodes, edges } = withOverdueReview();
      const placed = service.place(nodes, edges);
      const review = placed.find((node) => node.id === REVIEW) as WorkflowNode;
      const overdue = placed.find((node) => node.id === OVERDUE) as WorkflowNode;

      expect(overdue.size).toEqual(BOUNDARY_EVENT_NODE_SIZE);
      expect(overdue.autoSize).toBe(false);
      expect(overdue.position.y + BOUNDARY_EVENT_NODE_SIZE.height / 2).toBe(review.position.y + (review.size?.height as number));
      expect(overdue.position.x).toBeGreaterThanOrEqual(review.position.x);
      expect(overdue.position.x + BOUNDARY_EVENT_NODE_SIZE.width).toBeLessThanOrEqual(review.position.x + (review.size?.width as number));
    });

    // Ranked as an edge out of its task: what follows the event comes one column after the task, not first.
    it('ranks what depends on the event a column after the event’s task', () => {
      const { nodes, edges } = withOverdueReview();
      const placed = service.place(nodes, edges);

      expect(positionOf(placed, ESCALATE)?.x).toBe(positionOf(placed, APPROVE)?.x);
    });

    // It takes no row of its band: the clerk's lane is no deeper for it.
    it('takes neither a column nor a row of its own', () => {
      const plain = service.place(seededNodes(), seededEdges());
      const { nodes, edges } = withOverdueReview();
      const placed = service.place(nodes, edges);
      const clerkHeight = (graph: WorkflowNode[]) => graph.find((node) => node.id === laneNodeId('clerk'))?.size?.height;

      expect(clerkHeight(placed)).toBe(clerkHeight(plain));
      expect(positionOf(placed, CONFIRM)).toEqual(positionOf(plain, CONFIRM));
    });

    it('spreads a task’s several boundary events along its edge, from the right', () => {
      const { nodes, edges } = withOverdueReview();
      const placed = service.place([...nodes, boundary('review-cancelled', REVIEW, 'clerk')], edges);
      const first = positionOf(placed, OVERDUE) as { x: number; y: number };
      const second = positionOf(placed, elementNodeId('event', 'review-cancelled')) as { x: number; y: number };

      expect(second.y).toBe(first.y);
      expect(second.x).toBeLessThan(first.x - BOUNDARY_EVENT_NODE_SIZE.width + 1);
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
