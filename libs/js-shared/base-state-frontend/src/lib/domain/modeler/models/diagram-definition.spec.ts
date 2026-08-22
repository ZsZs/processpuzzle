import { describe, expect, it } from 'vitest';
import { DiagramDefinition, DiagramViewport, EdgeLayout, NodeLayout, NodeSize, Point } from './diagram-definition';

describe('DiagramDefinition model', () => {
  it('mirrors entityName onto id, so the generic screens can address a layout', () => {
    const layout = new DiagramDefinition({ entityName: 'order' });

    expect(layout.id).toBe('order');
  });

  it('keeps an explicitly supplied id, which is what a loaded record carries', () => {
    const layout = new DiagramDefinition({ id: 'order', entityName: 'order' });

    expect(layout.id).toBe('order');
  });

  it('starts an unarranged layout with empty node and edge lists, which is the normal starting point', () => {
    const layout = new DiagramDefinition();

    expect(layout.nodes).toEqual([]);
    expect(layout.edges).toEqual([]);
    expect(layout.id).toBe('');
  });

  it('leaves the viewport absent until the canvas has been panned or zoomed', () => {
    const layout = new DiagramDefinition({ entityName: 'order' });

    expect(layout.viewport).toBeUndefined();
  });

  it('keeps the server-assigned fields a loaded layout carries', () => {
    const layout = new DiagramDefinition({ entityName: 'order', orgKey: 'acme', version: 3, updatedAt: '2026-08-21T10:00:00Z' });

    expect(layout.orgKey).toBe('acme');
    expect(layout.version).toBe(3);
    expect(layout.updatedAt).toBe('2026-08-21T10:00:00Z');
    expect(layout.createdAt).toBeUndefined();
  });

  it('origins a point at 0,0, so a node minted before its first drag is still renderable', () => {
    expect(new Point()).toEqual({ x: 0, y: 0 });
    expect(new Point({ x: 12.5, y: -34 })).toEqual({ x: 12.5, y: -34 });
  });

  it('gives a node a position even when none was supplied', () => {
    const node = new NodeLayout({ stateKey: 'DRAFT' });

    expect(node.position).toEqual({ x: 0, y: 0 });
  });

  it('leaves a node without a size rather than inventing one, since auto-sizing is the default', () => {
    const node = new NodeLayout({ stateKey: 'DRAFT', position: new Point({ x: 10, y: 20 }) });

    expect(node.size).toBeUndefined();
  });

  it('keeps the size a resized node was created with', () => {
    const node = new NodeLayout({ stateKey: 'DRAFT', size: new NodeSize({ width: 150, height: 60 }) });

    expect(node.size).toEqual({ width: 150, height: 60 });
  });

  it('starts an edge with an empty waypoint list, so a drag has something to append to', () => {
    const edge = new EdgeLayout({ transitionKey: 'submit' });

    expect(edge.points).toEqual([]);
  });

  it('keeps the port anchors an edge was drawn with, which is what stops it jumping on reload', () => {
    const edge = new EdgeLayout({ transitionKey: 'submit', sourcePort: 'port-right', targetPort: 'port-left', routing: 'orthogonal' });

    expect(edge.sourcePort).toBe('port-right');
    expect(edge.targetPort).toBe('port-left');
    expect(edge.routing).toBe('orthogonal');
  });

  it('leaves an automatically routed edge without ports or a routing mode', () => {
    const edge = new EdgeLayout({ transitionKey: 'submit' });

    expect(edge.sourcePort).toBeUndefined();
    expect(edge.targetPort).toBeUndefined();
    expect(edge.routing).toBeUndefined();
  });

  it('defaults a viewport to unzoomed at the origin', () => {
    expect(new DiagramViewport()).toEqual({ x: 0, y: 0, scale: 1 });
    expect(new DiagramViewport({ x: -40, y: 12, scale: 1.5 })).toEqual({ x: -40, y: 12, scale: 1.5 });
  });
});
