import { describe, expect, it } from 'vitest';
import { DiagramEdgeLayout, DiagramNodeLayout, DiagramViewport, NodeSize, Point, WorkflowDiagram } from './workflow-diagram';

describe('workflow diagram model', () => {
  describe('Point', () => {
    it('defaults to the origin, so a row arriving without coordinates is still renderable', () => {
      expect({ ...new Point() }).toEqual({ x: 0, y: 0 });
    });

    it('keeps what it is given', () => {
      expect({ ...new Point({ x: 12, y: -4 }) }).toEqual({ x: 12, y: -4 });
    });
  });

  describe('NodeSize', () => {
    it('defaults to zero', () => {
      expect({ ...new NodeSize() }).toEqual({ width: 0, height: 0 });
    });
  });

  describe('DiagramNodeLayout', () => {
    it('is keyed by the diagram node id, prefix and all', () => {
      expect(new DiagramNodeLayout({ nodeId: 'task:review-order' }).nodeId).toBe('task:review-order');
    });

    /**
     * An absent size means "keep whatever the automatic layout computed", so it stays absent rather than
     * becoming a 0x0 box the canvas would collapse.
     */
    it('leaves the size absent rather than inventing one', () => {
      expect(new DiagramNodeLayout({ nodeId: 'task:a' }).size).toBeUndefined();
    });

    it('has a position even when none was given', () => {
      expect({ ...new DiagramNodeLayout().position }).toEqual({ x: 0, y: 0 });
    });
  });

  describe('DiagramEdgeLayout', () => {
    // An empty array rather than undefined, so a waypoint always has something to append to.
    it('starts with an empty waypoint list', () => {
      expect(new DiagramEdgeLayout({ edgeId: 'a->b' }).points).toEqual([]);
    });

    it('carries the port anchors and the routing mode', () => {
      const edge = new DiagramEdgeLayout({ edgeId: 'a->b', sourcePort: 'port-right', targetPort: 'port-left', routing: 'orthogonal' });

      expect([edge.sourcePort, edge.targetPort, edge.routing]).toEqual(['port-right', 'port-left', 'orthogonal']);
    });
  });

  describe('DiagramViewport', () => {
    it('defaults to 100% at the origin, not to a zero scale that would collapse the canvas', () => {
      expect({ ...new DiagramViewport() }).toEqual({ x: 0, y: 0, scale: 1 });
    });
  });

  describe('WorkflowDiagram', () => {
    /**
     * `id` mirrors `workflowId` because the contract addresses a layout by that id and gives it no key of
     * its own, while `BaseEntityRestService` builds every single-record URL from `id`.
     */
    it('mirrors the workflow id into id', () => {
      expect(new WorkflowDiagram({ workflowId: 'order-fulfillment-workflow' }).id).toBe('order-fulfillment-workflow');
    });

    it('keeps an explicitly given id', () => {
      expect(new WorkflowDiagram({ workflowId: 'a', id: 'b' }).id).toBe('b');
    });

    // A workflow opened but never arranged is the normal starting point, and the canvas appends to these.
    it('starts with empty node and edge lists', () => {
      const diagram = new WorkflowDiagram({ workflowId: 'a' });

      expect([diagram.nodes, diagram.edges]).toEqual([[], []]);
    });

    it('leaves the viewport absent until the canvas has been panned or zoomed', () => {
      expect(new WorkflowDiagram({ workflowId: 'a' }).viewport).toBeUndefined();
    });

    it('carries the server-assigned fields through', () => {
      const diagram = new WorkflowDiagram({ workflowId: 'a', orgKey: 'org', version: 3, createdAt: 'then', updatedAt: 'now' });

      expect([diagram.orgKey, diagram.version, diagram.createdAt, diagram.updatedAt]).toEqual(['org', 3, 'then', 'now']);
    });
  });
});
