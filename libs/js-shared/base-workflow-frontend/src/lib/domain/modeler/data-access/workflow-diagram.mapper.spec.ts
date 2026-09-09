import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { DiagramEdgeLayout, DiagramNodeLayout, DiagramViewport, NodeSize, Point, WorkflowDiagram } from '../models/workflow-diagram';
import { WorkflowDiagramMapper } from './workflow-diagram.mapper';

describe('WorkflowDiagramMapper', () => {
  let mapper: WorkflowDiagramMapper;

  beforeEach(() => {
    mapper = TestBed.configureTestingModule({}).inject(WorkflowDiagramMapper);
  });

  describe('fromDto', () => {
    it('maps the whole layout', () => {
      const layout = mapper.fromDto({
        workflowId: 'order-fulfillment-workflow',
        nodes: [{ nodeId: 'task:review-order', position: { x: 10, y: 20 }, size: { width: 170, height: 76 } }],
        edges: [{ edgeId: 'task:a->task:b', points: [{ x: 1, y: 2 }], sourcePort: 'port-right', targetPort: 'port-left', routing: 'orthogonal' }],
        viewport: { x: -5, y: -6, scale: 0.8 },
        orgKey: 'processpuzzle-testbed',
        version: 4,
      });

      expect(layout.workflowId).toBe('order-fulfillment-workflow');
      expect(layout.nodes[0]).toEqual(new DiagramNodeLayout({ nodeId: 'task:review-order', position: new Point({ x: 10, y: 20 }), size: new NodeSize({ width: 170, height: 76 }) }));
      expect(layout.edges[0]).toEqual(
        new DiagramEdgeLayout({ edgeId: 'task:a->task:b', points: [new Point({ x: 1, y: 2 })], sourcePort: 'port-right', targetPort: 'port-left', routing: 'orthogonal' }),
      );
      expect(layout.viewport).toEqual(new DiagramViewport({ x: -5, y: -6, scale: 0.8 }));
      expect([layout.orgKey, layout.version]).toEqual(['processpuzzle-testbed', 4]);
    });

    // json-server keys a record by `id`; the Spring backend answers with `workflowId`. Either is enough.
    it('falls back to id when the payload names no workflowId', () => {
      expect(mapper.fromDto({ id: 'order-fulfillment-workflow' }).workflowId).toBe('order-fulfillment-workflow');
    });

    it('mirrors the workflow id into id', () => {
      expect(mapper.fromDto({ workflowId: 'a' }).id).toBe('a');
    });

    // A workflow opened but never arranged has neither list, and the canvas must still be able to append.
    it('reads absent lists as empty ones', () => {
      const layout = mapper.fromDto({ workflowId: 'a' });

      expect([layout.nodes, layout.edges]).toEqual([[], []]);
    });

    /**
     * A row arriving without a position still has to reach the canvas renderable, rather than throwing on
     * the first read of `position.x`.
     */
    it('gives a row with no position the origin', () => {
      expect(mapper.fromDto({ workflowId: 'a', nodes: [{ nodeId: 'task:a' }] }).nodes[0].position).toEqual(new Point());
    });

    it('leaves an absent size absent rather than making it a 0x0 box', () => {
      expect(mapper.fromDto({ workflowId: 'a', nodes: [{ nodeId: 'task:a', position: { x: 0, y: 0 } }] }).nodes[0].size).toBeUndefined();
    });

    /**
     * The three port/routing fields are `nullable: true` in the contract, and a `null` port handed to
     * ng-diagram is an anchor request rather than "anchor this automatically".
     */
    it('normalizes null ports and routing to undefined', () => {
      const edge = mapper.fromDto({ workflowId: 'a', edges: [{ edgeId: 'a->b', sourcePort: null, targetPort: null, routing: null }] }).edges[0];

      expect([edge.sourcePort, edge.targetPort, edge.routing]).toEqual([undefined, undefined, undefined]);
    });

    it('reads a null viewport as none at all', () => {
      expect(mapper.fromDto({ workflowId: 'a', viewport: null }).viewport).toBeUndefined();
    });
  });

  describe('toDto', () => {
    it('sends both the contract field and the id mirror', () => {
      const dto = mapper.toDto(new WorkflowDiagram({ workflowId: 'order-fulfillment-workflow' }));

      expect([dto.workflowId, dto.id]).toEqual(['order-fulfillment-workflow', 'order-fulfillment-workflow']);
    });

    it('falls back to id when the layout carries no workflowId', () => {
      expect(mapper.toDto(new WorkflowDiagram({ id: 'a' })).workflowId).toBe('a');
    });

    /**
     * `PUT /workflow-diagrams/{workflowId}` is a full replacement, so an absent list is a *cleared* layout
     * rather than an untouched one — which is what prunes rows for nodes the workflow no longer has.
     */
    it('emits the node and edge lists unconditionally', () => {
      const dto = mapper.toDto(new WorkflowDiagram({ workflowId: 'a' }));

      expect([dto.nodes, dto.edges]).toEqual([[], []]);
    });

    // Genuinely optional in the contract, so `undefined` drops out of the JSON body.
    it('omits an absent viewport', () => {
      expect(mapper.toDto(new WorkflowDiagram({ workflowId: 'a' })).viewport).toBeUndefined();
    });

    it('round-trips a layout unchanged', () => {
      const layout = new WorkflowDiagram({
        workflowId: 'a',
        nodes: [new DiagramNodeLayout({ nodeId: 'task:a', position: new Point({ x: 3, y: 4 }), size: new NodeSize({ width: 5, height: 6 }) })],
        edges: [new DiagramEdgeLayout({ edgeId: 'a->b', points: [new Point({ x: 7, y: 8 })], sourcePort: 'port-top', routing: 'bezier' })],
        viewport: new DiagramViewport({ x: 1, y: 2, scale: 1.5 }),
      });

      expect(mapper.fromDto(mapper.toDto(layout))).toEqual(layout);
    });

    /**
     * Listed field by field rather than spread, so nothing the canvas may hang on the layout later can leak
     * into the payload unnoticed.
     */
    it('does not carry an unknown property into the payload', () => {
      const layout = Object.assign(new WorkflowDiagram({ workflowId: 'a' }), { scratch: 'canvas state' });

      expect(mapper.toDto(layout)).not.toHaveProperty('scratch');
    });
  });
});
