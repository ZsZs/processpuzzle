import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { State, Transition } from '../../../state-machine-definition';
import { STATE_NODE_TYPE, StateNode, TransitionEdge } from '../state-machine-graph';
import { DagreLayoutService } from './dagre-layout.service';

describe('DagreLayoutService', () => {
  let service: DagreLayoutService;

  const node = (key: string, position = { x: 0, y: 0 }, size?: { width: number; height: number }): StateNode => ({
    id: key,
    type: STATE_NODE_TYPE,
    position,
    ...(size ? { size } : {}),
    data: { state: new State({ key, name: key }), label: key, initial: false },
  });

  const edge = (key: string, source: string, target: string): TransitionEdge => ({
    id: key,
    source,
    target,
    data: { transition: new Transition({ key, sourceStateKey: source, targetStateKey: target }), label: key },
  });

  /** NaN for a key that is not in the graph, so a missing node fails the comparison rather than passing it. */
  const xOf = (placed: StateNode[], key: string): number => placed.find((node) => node.id === key)?.position.x ?? NaN;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [DagreLayoutService] });
    service = TestBed.inject(DagreLayoutService);
  });

  it('leaves a fully arranged graph exactly as it is', () => {
    const nodes = [node('DRAFT', { x: 40, y: 80 }), node('DELIVERED', { x: 320, y: 80 })];

    expect(service.place(nodes, [edge('confirm', 'DRAFT', 'DELIVERED')], [])).toBe(nodes);
  });

  // The 404 path: nothing has ever been arranged, so the whole graph is laid out.
  describe('when nothing is placed', () => {
    const nodes = [node('DRAFT'), node('DELIVERED'), node('CANCELLED')];
    const edges = [edge('confirm', 'DRAFT', 'DELIVERED'), edge('cancel', 'DRAFT', 'CANCELLED')];

    it('spreads every node out instead of leaving them stacked at the origin', () => {
      const placed = service.place(nodes, edges, ['DRAFT', 'DELIVERED', 'CANCELLED']);

      const positions = placed.map((placedNode) => `${placedNode.position.x},${placedNode.position.y}`);
      expect(new Set(positions).size).toBe(3);
    });

    // Left to right, so the machine reads the way it is described: initial state first.
    it('puts a target state to the right of its source', () => {
      const placed = service.place(nodes, edges, ['DRAFT', 'DELIVERED', 'CANCELLED']);

      expect(xOf(placed, 'DELIVERED')).toBeGreaterThan(xOf(placed, 'DRAFT'));
      expect(xOf(placed, 'CANCELLED')).toBeGreaterThan(xOf(placed, 'DRAFT'));
    });

    it('does not mutate the nodes it was given', () => {
      service.place(nodes, edges, ['DRAFT', 'DELIVERED', 'CANCELLED']);

      expect(nodes.map((given) => given.position)).toEqual([
        { x: 0, y: 0 },
        { x: 0, y: 0 },
        { x: 0, y: 0 },
      ]);
    });

    // Dagre reports a node's centre, ng-diagram positions by the top-left corner. Without the correction
    // every node sits half its own width down and to the right of where the layout intended.
    it('converts Dagre centres into top-left corners', () => {
      const single = [node('DRAFT', { x: 0, y: 0 }, { width: 200, height: 100 })];

      const placed = service.place(single, [], ['DRAFT']);

      expect(placed[0].position).toEqual({ x: 0, y: 0 });
    });

    // A transition may name a state the machine no longer declares; Dagre would invent a node for it.
    it('ignores an edge whose ends are not both in the graph', () => {
      const placed = service.place([node('DRAFT')], [edge('cancel', 'DRAFT', 'GONE')], ['DRAFT']);

      expect(placed).toHaveLength(1);
      expect(placed[0].id).toBe('DRAFT');
    });
  });

  // A state added after the last arrangement. Re-running the layout would move every state the user
  // positioned by hand, which is not something the modeler can undo for them.
  describe('when only some are placed', () => {
    const nodes = [node('DRAFT', { x: 100, y: 200 }), node('DELIVERED', { x: 400, y: 200 }), node('CANCELLED')];

    it('leaves the arranged states untouched', () => {
      const placed = service.place(nodes, [], ['CANCELLED']);

      expect(placed[0].position).toEqual({ x: 100, y: 200 });
      expect(placed[1].position).toEqual({ x: 400, y: 200 });
    });

    it('parks the new state below the arrangement, aligned with its left edge', () => {
      const placed = service.place(nodes, [], ['CANCELLED']);

      expect(placed[2].position.x).toBe(100);
      expect(placed[2].position.y).toBeGreaterThan(200);
    });

    it('parks several new states side by side rather than on top of each other', () => {
      const withTwoNew = [...nodes, node('REFUNDED')];

      const placed = service.place(withTwoNew, [], ['CANCELLED', 'REFUNDED']);

      expect(placed[3].position.x).toBeGreaterThan(placed[2].position.x);
      expect(placed[3].position.y).toBe(placed[2].position.y);
    });

    it('clears the bottom of a tall arranged node', () => {
      const tall = [node('DRAFT', { x: 0, y: 0 }, { width: 150, height: 400 }), node('CANCELLED')];

      const placed = service.place(tall, [], ['CANCELLED']);

      expect(placed[1].position.y).toBeGreaterThan(400);
    });
  });
});
