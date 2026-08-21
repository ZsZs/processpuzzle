import { describe, expect, it } from 'vitest';
import { State, StateMachineDefinition, Transition } from '../../../state-machine-definition';
import { StateMachineDefinitionMapper } from '../../../state-machine-definition.mapper';
import { STATE_MACHINE_DEFINITION_DTO } from '../../../test-state-machine-definition';
import { DiagramDefinitionMapper } from '../../data-access/diagram-definition.mapper';
import { DiagramDefinition, DiagramViewport, EdgeLayout, NodeLayout, NodeSize, Point } from '../../models/diagram-definition';
import { DIAGRAM_DEFINITION_DTO } from '../../models/test-diagram-definition';
import { STATE_NODE_TYPE, StateNode, TransitionEdge } from '../state-machine-graph';
import { StateMachineGraphConverter } from './state-machine-graph.converter';

/**
 * Both halves are built by running the shared DTO fixtures through their real mappers rather than by
 * hand-writing entities: the two fixtures describe the same `order` machine, so this also checks that the
 * persistence and graph halves actually fit each other.
 */
describe('StateMachineGraphConverter', () => {
  const machine = new StateMachineDefinitionMapper().fromDto(STATE_MACHINE_DEFINITION_DTO);
  const layout = new DiagramDefinitionMapper().fromDto(DIAGRAM_DEFINITION_DTO);

  describe('toGraph', () => {
    it('identifies a node by the state key the layout rows are keyed on', () => {
      const graph = StateMachineGraphConverter.toGraph(machine, layout);

      expect(graph.nodes.map((node) => node.id)).toEqual(['DRAFT', 'DELIVERED']);
      expect(graph.nodes.map((node) => node.type)).toEqual([STATE_NODE_TYPE, STATE_NODE_TYPE]);
    });

    it('positions and sizes the nodes from the saved arrangement', () => {
      const graph = StateMachineGraphConverter.toGraph(machine, layout);

      expect(graph.nodes[0].position).toEqual({ x: 40, y: 80 });
      expect(graph.nodes[0].size).toEqual({ width: 160, height: 64 });
      expect(graph.unplacedStateKeys).toEqual([]);
    });

    // Absent means "size me by my content", which is ng-diagram's default; a 0x0 box would collapse.
    it('leaves an auto-sized node unsized', () => {
      expect(StateMachineGraphConverter.toGraph(machine, layout).nodes[1].size).toBeUndefined();
    });

    it('carries the whole state, its label and whether it is the initial one', () => {
      const graph = StateMachineGraphConverter.toGraph(machine, layout);

      expect(graph.nodes[0].data.state).toBe(machine.states[0]);
      expect(graph.nodes[0].data.label).toBe('Draft');
      expect(graph.nodes[0].data.initial).toBe(true);
      expect(graph.nodes[1].data.initial).toBe(false);
    });

    it('labels a state that has no name by its key, so no node is ever blank', () => {
      const unnamed = new StateMachineDefinition({ entityName: 'order', initialStateKey: 'DRAFT', states: [new State({ key: 'DRAFT' })] });

      expect(StateMachineGraphConverter.toGraph(unnamed).nodes[0].data.label).toBe('DRAFT');
    });

    it('routes an edge between the states its transition names, keeping the saved port anchors', () => {
      const edge = StateMachineGraphConverter.toGraph(machine, layout).edges[0];

      expect([edge.id, edge.source, edge.target]).toEqual(['confirm', 'DRAFT', 'DELIVERED']);
      expect(edge.sourcePort).toBe('port-right');
      expect(edge.targetPort).toBe('port-left');
      expect(edge.routing).toBe('orthogonal');
      expect(edge.data.transition).toBe(machine.transitions[0]);
      expect(edge.data.label).toBe('Confirm');
    });

    // In auto mode ng-diagram owns `points` and recomputes them, so saved waypoints only survive if the
    // edge says it is routed manually.
    it('marks an edge with saved waypoints as manually routed, or the waypoints are overwritten', () => {
      const edge = StateMachineGraphConverter.toGraph(machine, layout).edges[0];

      expect(edge.routingMode).toBe('manual');
      expect(edge.points).toEqual([
        { x: 210, y: 112 },
        { x: 300, y: 112 },
      ]);
    });

    it('leaves an edge the layout says nothing about floating and automatically routed', () => {
      const graph = StateMachineGraphConverter.toGraph(machine);

      expect(graph.edges[0].sourcePort).toBeUndefined();
      expect(graph.edges[0].targetPort).toBeUndefined();
      expect(graph.edges[0].routingMode).toBeUndefined();
      expect(graph.edges[0].points).toBeUndefined();
    });

    // The 404 path: a machine that has never been arranged. Every key is reported so the layout engine
    // places the whole graph.
    it('reports every state as unplaced when there is no saved arrangement', () => {
      const graph = StateMachineGraphConverter.toGraph(machine);

      expect(graph.unplacedStateKeys).toEqual(['DRAFT', 'DELIVERED']);
      expect(graph.nodes.map((node) => node.position)).toEqual([
        { x: 0, y: 0 },
        { x: 0, y: 0 },
      ]);
      expect(graph.metadata).toBeUndefined();
    });

    // A state added after the last arrangement. Reported on its own, so the states the user positioned by
    // hand are not re-flowed for it.
    it('reports only the states the arrangement is missing', () => {
      const partial = new DiagramDefinition({ entityName: 'order', nodes: [new NodeLayout({ stateKey: 'DRAFT', position: new Point({ x: 40, y: 80 }) })] });

      const graph = StateMachineGraphConverter.toGraph(machine, partial);

      expect(graph.unplacedStateKeys).toEqual(['DELIVERED']);
      expect(graph.nodes[0].position).toEqual({ x: 40, y: 80 });
    });

    // updateStateMachineDefinition may drop a state; the contract says a layout row left naming it is
    // harmless. Rendering it would mean a node with no state behind it.
    it('ignores a layout row naming a state the machine no longer declares', () => {
      const stale = new DiagramDefinition({
        entityName: 'order',
        nodes: [new NodeLayout({ stateKey: 'DRAFT', position: new Point({ x: 40, y: 80 }) }), new NodeLayout({ stateKey: 'CANCELLED', position: new Point({ x: 999, y: 999 }) })],
        edges: [new EdgeLayout({ transitionKey: 'cancel' })],
      });

      const graph = StateMachineGraphConverter.toGraph(machine, stale);

      expect(graph.nodes.map((node) => node.id)).toEqual(['DRAFT', 'DELIVERED']);
      expect(graph.edges.map((edge) => edge.id)).toEqual(['confirm']);
    });

    it('carries the saved viewport, so reopening returns to where the user was', () => {
      expect(StateMachineGraphConverter.toGraph(machine, layout).metadata).toEqual({ viewport: { x: -120, y: 0, scale: 1.25 } });
    });
  });

  describe('toLayout', () => {
    const graph = StateMachineGraphConverter.toGraph(machine, layout);

    it('reproduces the arrangement it was built from', () => {
      const saved = StateMachineGraphConverter.toLayout('order', graph.nodes, graph.edges, layout.viewport);

      expect(saved.entityName).toBe('order');
      expect(saved.id).toBe('order');
      expect(saved.nodes).toEqual(layout.nodes);
      expect(saved.edges).toEqual(layout.edges);
      expect(saved.viewport).toEqual(new DiagramViewport({ x: -120, y: 0, scale: 1.25 }));
    });

    it('emits the arrangement only, never the topology the machine resource owns', () => {
      const saved = StateMachineGraphConverter.toLayout('order', graph.nodes, graph.edges);

      expect(saved.nodes[0]).not.toHaveProperty('state');
      expect(saved.nodes[0]).not.toHaveProperty('label');
      expect(saved.edges[0]).not.toHaveProperty('transition');
      expect(saved.viewport).toBeUndefined();
    });

    it('picks up a node dragged to a new position', () => {
      const dragged = graph.nodes.map((node, index) => (index === 0 ? { ...node, position: { x: 500, y: 240 } } : node)) as StateNode[];

      expect(StateMachineGraphConverter.toLayout('order', dragged, graph.edges).nodes[0].position).toEqual(new Point({ x: 500, y: 240 }));
    });

    // In auto mode `points` is whatever the routing algorithm last computed. Persisting it would freeze a
    // derived path into the layout and replay stale geometry after the nodes move.
    it('does not persist the waypoints an automatically routed edge computed for itself', () => {
      const auto = [{ ...graph.edges[0], routingMode: undefined, points: [{ x: 1, y: 2 }] }] as TransitionEdge[];

      expect(StateMachineGraphConverter.toLayout('order', graph.nodes, auto).edges[0].points).toEqual([]);
    });

    it('keeps the port anchors, without which the diagram reopens with different geometry', () => {
      const saved = StateMachineGraphConverter.toLayout('order', graph.nodes, graph.edges);

      expect(saved.edges[0].sourcePort).toBe('port-right');
      expect(saved.edges[0].targetPort).toBe('port-left');
      expect(saved.edges[0].routing).toBe('orthogonal');
    });

    // The write is optimistic-locked, so a save has to declare the version it read.
    it('carries the version of the arrangement it is replacing', () => {
      const saved = StateMachineGraphConverter.toLayout('order', graph.nodes, graph.edges, undefined, layout);

      expect(saved.version).toBe(3);
      expect(saved.orgKey).toBe('processpuzzle-testbed');
    });

    it('leaves the version unset for a machine arranged for the first time', () => {
      expect(StateMachineGraphConverter.toLayout('order', graph.nodes, graph.edges).version).toBeUndefined();
    });

    it('saves an unsized node without inventing a size for it', () => {
      const saved = StateMachineGraphConverter.toLayout('order', graph.nodes, graph.edges);

      expect(saved.nodes[1].size).toBeUndefined();
      expect(saved.nodes[0].size).toEqual(new NodeSize({ width: 160, height: 64 }));
    });

    it('drops a node the user deleted from the canvas', () => {
      const saved = StateMachineGraphConverter.toLayout('order', [graph.nodes[0]], []);

      expect(saved.nodes).toHaveLength(1);
      expect(saved.edges).toEqual([]);
    });
  });

  it('round-trips an arrangement through the graph unchanged', () => {
    const graph = StateMachineGraphConverter.toGraph(machine, layout);
    const saved = StateMachineGraphConverter.toLayout('order', graph.nodes, graph.edges, layout.viewport, layout);
    const reopened = StateMachineGraphConverter.toGraph(machine, saved);

    expect(reopened.nodes).toEqual(graph.nodes);
    expect(reopened.edges).toEqual(graph.edges);
    expect(reopened.unplacedStateKeys).toEqual([]);
  });

  it('graphs a machine with no states at all without failing', () => {
    const empty = StateMachineGraphConverter.toGraph(new StateMachineDefinition({ entityName: 'order' }));

    expect(empty.nodes).toEqual([]);
    expect(empty.edges).toEqual([]);
    expect(empty.unplacedStateKeys).toEqual([]);
  });

  it('labels a transition by its trigger when it has no name of its own', () => {
    const machineWithUnnamedTransition = new StateMachineDefinition({
      entityName: 'order',
      states: [new State({ key: 'DRAFT' }), new State({ key: 'SENT' })],
      transitions: [new Transition({ key: 'send', sourceStateKey: 'DRAFT', targetStateKey: 'SENT', triggerKey: 'send' })],
    });

    expect(StateMachineGraphConverter.toGraph(machineWithUnnamedTransition).edges[0].data.label).toBe('send');
  });
});
