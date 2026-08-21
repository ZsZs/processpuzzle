import { describe, expect, it } from 'vitest';
import { DiagramDefinition, DiagramViewport, EdgeLayout, NodeLayout, NodeSize, Point } from 'libs/js-shared/base-state-frontend/src/lib/domain/modeler/models/diagram-definition';
import { DiagramDefinitionMapper } from 'libs/js-shared/base-state-frontend/src/lib/domain/modeler/data-access/diagram-definition.mapper';
import { DIAGRAM_DEFINITION_DTO, OTHER_DIAGRAM_DEFINITION_DTO } from 'libs/js-shared/base-state-frontend/src/lib/domain/modeler/data-access/test-diagram-definition';

describe('DiagramDefinitionMapper', () => {
  const mapper = new DiagramDefinitionMapper();

  describe('fromDto', () => {
    it('mirrors entityName onto id, which is what the generic URL builder addresses a layout by', () => {
      const layout = mapper.fromDto(DIAGRAM_DEFINITION_DTO);

      expect(layout.id).toBe('order');
      expect(layout.entityName).toBe('order');
    });

    // json-server keys a record by `id` and serves it back; the contract's own field is `entityName`.
    it('falls back to id when the payload carries no entityName', () => {
      expect(mapper.fromDto({ id: 'order' }).entityName).toBe('order');
    });

    it('maps node rows element by element, keeping position and size', () => {
      const layout = mapper.fromDto(DIAGRAM_DEFINITION_DTO);

      expect(layout.nodes).toHaveLength(2);
      expect(layout.nodes[0]).toBeInstanceOf(NodeLayout);
      expect(layout.nodes[0].position).toEqual(new Point({ x: 40, y: 80 }));
      expect(layout.nodes[0].size).toEqual(new NodeSize({ width: 160, height: 64 }));
    });

    // A 0x0 box would collapse the node on the canvas; absent means "size me by my content".
    it('leaves an unsized node unsized rather than giving it a zero box', () => {
      expect(mapper.fromDto(DIAGRAM_DEFINITION_DTO).nodes[1].size).toBeUndefined();
      expect(mapper.fromDto(OTHER_DIAGRAM_DEFINITION_DTO).nodes[0].size).toBeUndefined();
    });

    it('keeps a node renderable when the payload carries no position at all', () => {
      const layout = mapper.fromDto({ entityName: 'order', nodes: [{ stateKey: 'DRAFT' }] });

      expect(layout.nodes[0].position).toEqual(new Point({ x: 0, y: 0 }));
    });

    it('maps edge rows with their waypoints and port anchors', () => {
      const layout = mapper.fromDto(DIAGRAM_DEFINITION_DTO);

      expect(layout.edges[0]).toBeInstanceOf(EdgeLayout);
      expect(layout.edges[0].transitionKey).toBe('confirm');
      expect(layout.edges[0].points).toEqual([new Point({ x: 210, y: 112 }), new Point({ x: 300, y: 112 })]);
      expect(layout.edges[0].sourcePort).toBe('port-right');
      expect(layout.edges[0].targetPort).toBe('port-left');
      expect(layout.edges[0].routing).toBe('orthogonal');
    });

    // The contract marks these `nullable`, and a null port handed on unchanged reads as an anchor
    // request rather than as "anchor this edge automatically".
    it('normalizes the nullable port and routing fields to absent', () => {
      const edge = mapper.fromDto(OTHER_DIAGRAM_DEFINITION_DTO).edges[0];

      expect(edge.sourcePort).toBeUndefined();
      expect(edge.targetPort).toBeUndefined();
      expect(edge.routing).toBeUndefined();
      expect(edge.points).toEqual([]);
    });

    it('maps the viewport, so reopening a large machine returns to where the user was', () => {
      expect(mapper.fromDto(DIAGRAM_DEFINITION_DTO).viewport).toEqual(new DiagramViewport({ x: -120, y: 0, scale: 1.25 }));
    });

    it('leaves the viewport absent when the canvas has never been panned or zoomed', () => {
      expect(mapper.fromDto(OTHER_DIAGRAM_DEFINITION_DTO).viewport).toBeUndefined();
      expect(mapper.fromDto({ entityName: 'order' }).viewport).toBeUndefined();
    });

    it('starts an unarranged layout with empty node and edge lists', () => {
      const layout = mapper.fromDto({ entityName: 'order' });

      expect(layout.nodes).toEqual([]);
      expect(layout.edges).toEqual([]);
    });

    it('keeps the server-assigned fields a loaded layout carries', () => {
      const layout = mapper.fromDto(DIAGRAM_DEFINITION_DTO);

      expect(layout.orgKey).toBe('processpuzzle-testbed');
      expect(layout.version).toBe(3);
      expect(layout.createdAt).toBe('2026-01-01T00:00:00Z');
      expect(layout.updatedAt).toBe('2026-02-01T00:00:00Z');
    });
  });

  describe('toDto', () => {
    it('sends both the contract field and the id json-server keys a record by', () => {
      const dto = mapper.toDto(new DiagramDefinition({ entityName: 'order' }));

      expect(dto.entityName).toBe('order');
      expect(dto.id).toBe('order');
    });

    it('falls back to id when only the mirror was set', () => {
      const dto = mapper.toDto(new DiagramDefinition({ id: 'order', entityName: '' }));

      expect(dto.entityName).toBe('order');
    });

    // PUT /diagrams/{entityName} is a full replacement: an absent list is a cleared layout.
    it('emits nodes and edges unconditionally, since the write is a full replacement', () => {
      const dto = mapper.toDto(new DiagramDefinition({ entityName: 'order' }));

      expect(dto.nodes).toEqual([]);
      expect(dto.edges).toEqual([]);
    });

    it('round-trips a loaded layout unchanged apart from the added id', () => {
      const dto = mapper.toDto(mapper.fromDto(DIAGRAM_DEFINITION_DTO));

      expect(dto).toEqual({ ...DIAGRAM_DEFINITION_DTO, id: 'order' });
    });

    it('omits the size of an auto-sized node and the viewport of a never-panned canvas', () => {
      const dto = mapper.toDto(new DiagramDefinition({ entityName: 'order', nodes: [new NodeLayout({ stateKey: 'DRAFT' })] }));

      expect(dto.nodes?.[0].size).toBeUndefined();
      expect(dto.viewport).toBeUndefined();
    });

    it('sends the port anchors, without which the diagram reopens with different geometry', () => {
      const layout = new DiagramDefinition({
        entityName: 'order',
        edges: [new EdgeLayout({ transitionKey: 'confirm', sourcePort: 'port-right', targetPort: 'port-left', routing: 'orthogonal' })],
      });

      expect(mapper.toDto(layout).edges?.[0]).toEqual({ transitionKey: 'confirm', points: [], sourcePort: 'port-right', targetPort: 'port-left', routing: 'orthogonal' });
    });
  });
});
