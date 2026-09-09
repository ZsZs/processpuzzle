import { describe, expect, it } from 'vitest';
import { DiagramEdgeLayout, DiagramNodeLayout, DiagramViewport, NodeSize, Point, WorkflowDiagram } from '../models/workflow-diagram';
import { WORKFLOW_LANE_TYPE, WORKFLOW_NODE_TYPE, WorkflowEdge, WorkflowNode } from '../workflow-graph';
import { applySavedLayout, toDiagram } from './workflow-diagram.converter';

/** As `SwimlaneLayoutService` leaves them: placed, sized, and with `autoSize` turned off. */
function placedNodes(): WorkflowNode[] {
  return [
    {
      id: 'lane:clerk',
      type: WORKFLOW_LANE_TYPE,
      isGroup: true,
      highlighted: false,
      position: { x: 0, y: 0 },
      size: { width: 600, height: 108 },
      autoSize: false,
      data: { kind: 'role', elementId: 'clerk', label: 'Order Clerk' },
    },
    {
      id: 'task:review-order',
      type: WORKFLOW_NODE_TYPE,
      groupId: 'lane:clerk',
      position: { x: 156, y: 16 },
      size: { width: 170, height: 76 },
      autoSize: false,
      data: { kind: 'task', elementId: 'review-order', label: 'Review Order' },
    },
  ];
}

function placedEdges(): WorkflowEdge[] {
  return [{ id: 'task:a->task:b', source: 'task:a', target: 'task:b', sourcePort: 'port-right', targetPort: 'port-left', data: { relation: 'sequence' } }];
}

describe('workflow diagram converter', () => {
  describe('applySavedLayout', () => {
    // The "never arranged" case, and the normal starting point.
    it('leaves the computed layout standing when there is no saved one', () => {
      const nodes = placedNodes();
      const edges = placedEdges();

      expect(applySavedLayout(nodes, edges, undefined)).toEqual({ nodes, edges });
    });

    it('moves a node to where it was saved', () => {
      const saved = new WorkflowDiagram({ workflowId: 'w', nodes: [new DiagramNodeLayout({ nodeId: 'task:review-order', position: new Point({ x: 900, y: 800 }) })] });

      const { nodes } = applySavedLayout(placedNodes(), placedEdges(), saved);

      expect(nodes.find((node) => node.id === 'task:review-order')?.position).toEqual({ x: 900, y: 800 });
    });

    /**
     * The whole reason there is no "unplaced" list to carry: the automatic layout has already placed
     * everything, so a task added since the last save keeps the lane and column it computed rather than
     * landing at the origin.
     */
    it('leaves a node the saved layout does not mention exactly where it was placed', () => {
      const saved = new WorkflowDiagram({ workflowId: 'w', nodes: [new DiagramNodeLayout({ nodeId: 'task:review-order', position: new Point({ x: 900, y: 800 }) })] });

      const { nodes } = applySavedLayout(placedNodes(), placedEdges(), saved);

      expect(nodes.find((node) => node.id === 'lane:clerk')?.position).toEqual({ x: 0, y: 0 });
    });

    // A row naming a node the diagram no longer draws is normal — updateWorkflow may drop a task.
    it('ignores a row for a node that is not on the canvas', () => {
      const saved = new WorkflowDiagram({ workflowId: 'w', nodes: [new DiagramNodeLayout({ nodeId: 'task:deleted-since', position: new Point({ x: 1, y: 1 }) })] });

      expect(applySavedLayout(placedNodes(), placedEdges(), saved).nodes).toEqual(placedNodes());
    });

    /**
     * `autoSize: false` goes with every restored size: ng-diagram's `NodeSizeDirective` defaults `autoSize`
     * to true and, when true, *discards* an explicit size — so a resized lane restored without it would
     * keep none of its saved height.
     */
    it('restores a saved size and turns auto-sizing off with it', () => {
      const saved = new WorkflowDiagram({
        workflowId: 'w',
        nodes: [new DiagramNodeLayout({ nodeId: 'lane:clerk', position: new Point(), size: new NodeSize({ width: 1200, height: 300 }) })],
      });

      const lane = applySavedLayout(placedNodes(), placedEdges(), saved).nodes[0];

      expect(lane.size).toEqual({ width: 1200, height: 300 });
      expect(lane.autoSize).toBe(false);
    });

    it('leaves the computed size alone when the row records none', () => {
      const saved = new WorkflowDiagram({ workflowId: 'w', nodes: [new DiagramNodeLayout({ nodeId: 'lane:clerk', position: new Point({ x: 5, y: 5 }) })] });

      expect(applySavedLayout(placedNodes(), placedEdges(), saved).nodes[0].size).toEqual({ width: 600, height: 108 });
    });

    it('restores the port anchors and the routing mode of an edge', () => {
      const saved = new WorkflowDiagram({
        workflowId: 'w',
        edges: [new DiagramEdgeLayout({ edgeId: 'task:a->task:b', sourcePort: 'port-bottom', targetPort: 'port-top', routing: 'bezier' })],
      });

      const edge = applySavedLayout(placedNodes(), placedEdges(), saved).edges[0];

      expect([edge.sourcePort, edge.targetPort, edge.routing]).toEqual(['port-bottom', 'port-top', 'bezier']);
    });

    /**
     * Waypoints are only honoured in `manual` mode — in `auto` mode ng-diagram owns `points` and recomputes
     * them — so the mode follows from whether the row actually recorded any.
     */
    it('switches an edge with saved waypoints to manual routing', () => {
      const saved = new WorkflowDiagram({
        workflowId: 'w',
        edges: [new DiagramEdgeLayout({ edgeId: 'task:a->task:b', points: [new Point({ x: 3, y: 4 })] })],
      });

      const edge = applySavedLayout(placedNodes(), placedEdges(), saved).edges[0];

      expect(edge.points).toEqual([{ x: 3, y: 4 }]);
      expect(edge.routingMode).toBe('manual');
    });

    it('leaves an edge with no saved waypoints on automatic routing', () => {
      const saved = new WorkflowDiagram({ workflowId: 'w', edges: [new DiagramEdgeLayout({ edgeId: 'task:a->task:b', sourcePort: 'port-right' })] });

      expect(applySavedLayout(placedNodes(), placedEdges(), saved).edges[0].routingMode).toBeUndefined();
    });

    // ng-diagram's model owns these objects and its reactivity runs through the adapter.
    it('does not mutate the arrays it is given', () => {
      const nodes = placedNodes();
      const saved = new WorkflowDiagram({ workflowId: 'w', nodes: [new DiagramNodeLayout({ nodeId: 'task:review-order', position: new Point({ x: 900, y: 800 }) })] });

      applySavedLayout(nodes, placedEdges(), saved);

      expect(nodes.find((node) => node.id === 'task:review-order')?.position).toEqual({ x: 156, y: 16 });
    });
  });

  describe('toDiagram', () => {
    it('takes a row off every node and edge on the canvas', () => {
      const layout = toDiagram('order-fulfillment-workflow', placedNodes(), placedEdges());

      expect(layout.workflowId).toBe('order-fulfillment-workflow');
      expect(layout.nodes.map((node) => node.nodeId)).toEqual(['lane:clerk', 'task:review-order']);
      expect(layout.nodes[0]).toEqual(new DiagramNodeLayout({ nodeId: 'lane:clerk', position: new Point(), size: new NodeSize({ width: 600, height: 108 }) }));
      expect(layout.edges.map((edge) => edge.edgeId)).toEqual(['task:a->task:b']);
    });

    it('carries the viewport it is handed', () => {
      const layout = toDiagram('w', placedNodes(), placedEdges(), new DiagramViewport({ x: -20, y: -30, scale: 0.9 }));

      expect(layout.viewport).toEqual(new DiagramViewport({ x: -20, y: -30, scale: 0.9 }));
    });

    /**
     * The write is optimistic-locked: saving an arrangement read at version 3 has to say 3, or the server
     * cannot tell a concurrent edit from a stale one.
     */
    it('carries the version it was read at, so the save is locked against a concurrent one', () => {
      const previous = new WorkflowDiagram({ workflowId: 'w', version: 3, orgKey: 'org' });

      const layout = toDiagram('w', placedNodes(), placedEdges(), undefined, previous);

      expect([layout.version, layout.orgKey]).toEqual([3, 'org']);
    });

    /**
     * The merge rule, and the reason this converter is not a straight projection of the model. A layer
     * toggle takes the artifacts off the canvas entirely, and saving in that state must not be how the user
     * loses the positions they gave them.
     */
    it('keeps rows for nodes a hidden layer has taken off the canvas', () => {
      const previous = new WorkflowDiagram({
        workflowId: 'w',
        nodes: [
          new DiagramNodeLayout({ nodeId: 'task:review-order', position: new Point({ x: 1, y: 1 }) }),
          new DiagramNodeLayout({ nodeId: 'artifact:order-entity', position: new Point({ x: 7, y: 7 }) }),
        ],
        edges: [new DiagramEdgeLayout({ edgeId: 'artifact:order-entity->task:review-order' })],
      });

      const layout = toDiagram('w', placedNodes(), placedEdges(), undefined, previous);

      expect(layout.nodes.find((node) => node.nodeId === 'artifact:order-entity')?.position).toEqual(new Point({ x: 7, y: 7 }));
      expect(layout.edges.map((edge) => edge.edgeId)).toEqual(['task:a->task:b', 'artifact:order-entity->task:review-order']);
    });

    // What the model holds wins: a retained row must never shadow the position just dragged.
    it('lets the canvas win over a retained row for the same node', () => {
      const previous = new WorkflowDiagram({ workflowId: 'w', nodes: [new DiagramNodeLayout({ nodeId: 'task:review-order', position: new Point({ x: 1, y: 1 }) })] });

      const layout = toDiagram('w', placedNodes(), placedEdges(), undefined, previous);

      expect(layout.nodes.filter((node) => node.nodeId === 'task:review-order')).toHaveLength(1);
      expect(layout.nodes.find((node) => node.nodeId === 'task:review-order')?.position).toEqual(new Point({ x: 156, y: 16 }));
    });

    /**
     * In `auto` mode `points` holds whatever the routing algorithm last computed, and persisting that would
     * freeze a derived path into the layout — the next load would replay stale geometry instead of routing
     * around wherever the nodes now sit.
     */
    it('saves waypoints only for an edge the user routed by hand', () => {
      const auto: WorkflowEdge = { ...placedEdges()[0], points: [{ x: 50, y: 50 }] };
      const manual: WorkflowEdge = { ...placedEdges()[0], id: 'task:c->task:d', points: [{ x: 60, y: 60 }], routingMode: 'manual' };

      const layout = toDiagram('w', placedNodes(), [auto, manual]);

      expect(layout.edges[0].points).toEqual([]);
      expect(layout.edges[1].points).toEqual([new Point({ x: 60, y: 60 })]);
    });

    it('round-trips an arrangement through applySavedLayout unchanged', () => {
      const nodes = placedNodes();
      const edges = placedEdges();

      const restored = applySavedLayout(nodes, edges, toDiagram('w', nodes, edges));

      expect(restored.nodes.map((node) => node.position)).toEqual(nodes.map((node) => node.position));
      expect(restored.nodes.map((node) => node.size)).toEqual(nodes.map((node) => node.size));
    });
  });
});
