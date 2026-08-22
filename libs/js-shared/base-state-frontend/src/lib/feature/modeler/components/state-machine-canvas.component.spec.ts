import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { DiagramDefinitionMapper } from '../../../domain/modeler/data-access/diagram-definition.mapper';
import { STATE_NODE_TYPE, StateNode, TRANSITION_EDGE_TYPE } from '../../../domain/modeler/graph/state-machine-graph';
import { DIAGRAM_DEFINITION_DTO } from '../../../domain/modeler/models/test-diagram-definition';
import { State } from '../../../domain/state-machine-definition';
import { StateMachineDefinitionMapper } from '../../../domain/state-machine-definition.mapper';
import { STATE_MACHINE_DEFINITION_DTO } from '../../../domain/test-state-machine-definition';
import { DiagramSelectionService } from '../services/diagram-selection.service';
import { EdgeContextMenuService } from '../services/edge-context-menu.service';
import { PaletteStateKind, STATE_PALETTE_ITEMS } from './state-palette-items';
import { StateMachineCanvasComponent } from './state-machine-canvas.component';

describe('StateMachineCanvasComponent', () => {
  const machine = new StateMachineDefinitionMapper().fromDto(STATE_MACHINE_DEFINITION_DTO);
  const layout = new DiagramDefinitionMapper().fromDto(DIAGRAM_DEFINITION_DTO);

  let component: StateMachineCanvasComponent;
  let fixture: ComponentFixture<StateMachineCanvasComponent>;
  let selectionService: DiagramSelectionService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StateMachineCanvasComponent],
      // The palette rail the canvas hosts translates its labels; without a loader every `transloco` pipe
      // in it throws and the canvas cannot be created at all.
      providers: [DiagramSelectionService, provideTranslocoTesting({ translations: { en: {} } })],
    }).compileComponents();

    fixture = TestBed.createComponent(StateMachineCanvasComponent);
    component = fixture.componentInstance;
    selectionService = TestBed.inject(DiagramSelectionService);
  });

  it('should compile and create component', () => {
    expect(component).toBeTruthy();
  });

  it('should start empty, rather than showing a graph nobody asked for', () => {
    fixture.detectChanges();

    expect(component.model.getNodes()).toEqual([]);
    expect(component.model.getEdges()).toEqual([]);
  });

  // Asserted on the model rather than on the rendered DOM: the graph is drawn by `<ng-diagram>`, whose
  // element structure is its own and is measurement-driven, so the ids of the nodes handed to it are
  // nowhere in this component's markup. The model is what the component is responsible for.
  it('should render nodes and edges', () => {
    fixture.componentRef.setInput('machine', machine);
    fixture.componentRef.setInput('layout', layout);
    fixture.detectChanges();

    expect(component.model.getNodes().map((node) => node.id)).toEqual(['DRAFT', 'DELIVERED']);
    expect(component.model.getEdges().map((edge) => [edge.id, edge.source, edge.target])).toEqual([['confirm', 'DRAFT', 'DELIVERED']]);
  });

  it('should draw every state through the state node template', () => {
    fixture.componentRef.setInput('machine', machine);
    fixture.detectChanges();

    expect(component.model.getNodes().map((node) => node.type)).toEqual([STATE_NODE_TYPE, STATE_NODE_TYPE]);
    expect(component.nodeTemplateMap.get(STATE_NODE_TYPE)).toBeDefined();
  });

  it('should draw every transition through the transition edge template', () => {
    fixture.componentRef.setInput('machine', machine);
    fixture.detectChanges();

    expect(component.model.getEdges().map((edge) => edge.type)).toEqual([TRANSITION_EDGE_TYPE]);
    expect(component.edgeTemplateMap.get(TRANSITION_EDGE_TYPE)).toBeDefined();
  });

  /**
   * The routing menu, driven the way an edge drives it. The right-click itself belongs to
   * `TransitionEdgeComponent`; what is asserted here is everything downstream of it — that the menu opens
   * where the pointer was, ticks what the edge is drawn with, and that a pick reaches the model.
   *
   * `EdgeContextMenuService` is read off the component's own injector rather than the TestBed's, because
   * the canvas provides it: one open menu per canvas.
   */
  describe('routing menu', () => {
    const openMenuOn = (edgeId: string, clientX = 200, clientY = 150) => {
      fixture.debugElement.injector.get(EdgeContextMenuService).open(edgeId, new MouseEvent('contextmenu', { clientX, clientY }));
      fixture.detectChanges();
    };
    const menuItem = (routing: string) => (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(`[data-testid="routing-${routing}"]`);
    const menu = () => (fixture.nativeElement as HTMLElement).querySelector('[data-testid="edge-routing-menu"]');

    beforeEach(() => {
      fixture.componentRef.setInput('machine', machine);
      fixture.detectChanges();
    });

    it('shows nothing until an edge is right-clicked', () => {
      expect(menu()).toBeNull();
    });

    // An edge that names no routing is drawn with the default, so that is what the menu has to tick.
    it('ticks the routing the edge is drawn with', () => {
      openMenuOn('confirm');

      expect(menuItem('orthogonal')?.getAttribute('aria-checked')).toBe('true');
      expect(menuItem('bezier')?.getAttribute('aria-checked')).toBe('false');
    });

    it('routes the edge the menu was opened on, and closes', () => {
      openMenuOn('confirm');

      menuItem('bezier')?.click();
      fixture.detectChanges();

      expect(component.model.getEdges().map((edge) => edge.routing)).toEqual(['bezier']);
      expect(menu()).toBeNull();
    });

    // Reopened on the same edge, the menu now ticks what was picked.
    it('reports the routing it was given the next time it opens', () => {
      openMenuOn('confirm');
      menuItem('bezier')?.click();
      fixture.detectChanges();

      openMenuOn('confirm');

      expect(menuItem('bezier')?.getAttribute('aria-checked')).toBe('true');
    });

    // The routing is presentation, so it travels with the arrangement rather than with the machine.
    it('persists the choice as part of the layout', () => {
      openMenuOn('confirm');
      menuItem('bezier')?.click();
      fixture.detectChanges();

      expect(component.toLayout()?.edges.map((edge) => [edge.transitionKey, edge.routing])).toEqual([['confirm', 'bezier']]);
    });

    // A reload replaces the graph, so the edge the menu was about to route may no longer be there.
    it('closes when the machine is replaced', () => {
      openMenuOn('confirm');

      fixture.componentRef.setInput('machine', undefined);
      fixture.detectChanges();

      expect(menu()).toBeNull();
    });
  });

  it('should honour the saved arrangement', () => {
    fixture.componentRef.setInput('machine', machine);
    fixture.componentRef.setInput('layout', layout);
    fixture.detectChanges();

    expect(component.model.getNodes()[0].position).toEqual({ x: 40, y: 80 });
    expect(component.model.getMetadata().viewport).toMatchObject({ x: -120, y: 0, scale: 1.25 });
  });

  // The 404 path. Without the fallback every state would sit on top of the next one at the origin.
  it('should lay a never-arranged machine out instead of stacking it at the origin', () => {
    fixture.componentRef.setInput('machine', machine);
    fixture.detectChanges();

    const positions = component.model.getNodes().map((node) => `${node.position.x},${node.position.y}`);
    expect(new Set(positions).size).toBe(2);
  });

  it('should empty the canvas when the machine goes away', () => {
    fixture.componentRef.setInput('machine', machine);
    fixture.detectChanges();
    fixture.componentRef.setInput('machine', undefined);
    fixture.detectChanges();

    expect(component.model.getNodes()).toEqual([]);
  });

  describe('toLayout', () => {
    it('reports nothing to save while no machine is loaded', () => {
      fixture.detectChanges();

      expect(component.toLayout()).toBeUndefined();
    });

    it('reads the arrangement back off the model, which is where a drag lands', () => {
      fixture.componentRef.setInput('machine', machine);
      fixture.componentRef.setInput('layout', layout);
      fixture.detectChanges();
      component.model.updateNodes((nodes) => nodes.map((node) => (node.id === 'DRAFT' ? { ...node, position: { x: 500, y: 240 } } : node)));

      const saved = component.toLayout();

      expect(saved?.entityName).toBe('order');
      expect(saved?.nodes.find((node) => node.stateKey === 'DRAFT')?.position).toEqual({ x: 500, y: 240 });
    });

    // The write is optimistic-locked, so the save has to declare the version the canvas was opened with.
    it('carries the version of the arrangement it is replacing', () => {
      fixture.componentRef.setInput('machine', machine);
      fixture.componentRef.setInput('layout', layout);
      fixture.detectChanges();

      expect(component.toLayout()?.version).toBe(3);
    });

    it('has an arrangement to save even for a machine that was never arranged', () => {
      fixture.componentRef.setInput('machine', machine);
      fixture.detectChanges();

      const saved = component.toLayout();

      expect(saved?.nodes.map((node) => node.stateKey)).toEqual(['DRAFT', 'DELIVERED']);
      expect(saved?.version).toBeUndefined();
    });
  });

  describe('selection', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('machine', machine);
      fixture.detectChanges();
    });

    const selectionEvent = (nodeIds: string[], edgeIds: string[]) => ({
      selectedNodes: component.model.getNodes().filter((node) => nodeIds.includes(node.id)),
      selectedEdges: component.model.getEdges().filter((edge) => edgeIds.includes(edge.id)),
      previousNodes: [],
      previousEdges: [],
    });

    it('should handle node click selection', () => {
      component['onSelectionChanged'](selectionEvent(['DRAFT'], []));

      expect(selectionService.selectedState()).toBe(machine.states[0]);
      expect(selectionService.selectedTransition()).toBeUndefined();
    });

    it('should handle edge click selection', () => {
      component['onSelectionChanged'](selectionEvent([], ['confirm']));

      expect(selectionService.selectedTransition()).toBe(machine.transitions[0]);
      expect(selectionService.selectedState()).toBeUndefined();
    });

    // A box selection of several states has no single subject the properties panel could show.
    it('clears the selection when more than one element is selected', () => {
      component['onSelectionChanged'](selectionEvent(['DRAFT'], []));

      component['onSelectionChanged'](selectionEvent(['DRAFT', 'DELIVERED'], []));

      expect(selectionService.selectedState()).toBeUndefined();
    });

    it('clears the selection when the canvas is emptied', () => {
      component['onSelectionChanged'](selectionEvent(['DRAFT'], []));

      component['onSelectionChanged'](selectionEvent([], []));

      expect(selectionService.selectedState()).toBeUndefined();
      expect(selectionService.selectedTransition()).toBeUndefined();
    });

    // The reloaded graph is new objects; a selection pointing into the old one has nothing behind it.
    it('clears the selection when a different machine is loaded', () => {
      component['onSelectionChanged'](selectionEvent(['DRAFT'], []));

      fixture.componentRef.setInput('layout', layout);
      fixture.detectChanges();

      expect(selectionService.selectedState()).toBeUndefined();
    });
  });

  describe('palette drops', () => {
    /**
     * What ng-diagram's own `PaletteDropEventHandler` does — spread the palette item, give it the id
     * `computeNodeId` mints and the drop position, add it — followed by the component's handler. Rebuilt
     * here rather than driven through the DOM because a drop is an HTML5 drag on an `<ng-diagram>` that has
     * measured itself, and none of that is what these tests are about.
     */
    const drop = (kind: PaletteStateKind, position = { x: 300, y: 120 }): void => {
      const item = STATE_PALETTE_ITEMS.filter((candidate) => candidate.data.kind === kind)[0];
      const node: StateNode = { ...item, id: component.config.computeNodeId?.() ?? '', position };
      component.model.updateNodes((nodes) => [...nodes, node]);
      component['onPaletteItemDropped']({ node, dropPosition: position });
    };

    beforeEach(() => {
      fixture.componentRef.setInput('machine', machine);
      fixture.detectChanges();
    });

    it('turns a dropped symbol into a state of its own, keyed by the lowest free ordinal', () => {
      drop('state');

      const dropped = component.model.getNodes().find((node) => node.id === 'STATE_1') as StateNode;
      expect(dropped.data.state.key).toBe('STATE_1');
      expect(dropped.data.state.name).toBe('State 1');
      expect(dropped.position).toEqual({ x: 300, y: 120 });
    });

    it('counts on, so two drops are two states rather than one state twice', () => {
      drop('state');
      drop('state', { x: 420, y: 260 });

      expect(component.model.getNodes().map((node) => node.id)).toEqual(['DRAFT', 'DELIVERED', 'STATE_1', 'STATE_2']);
    });

    // The placeholder `State` in a palette item's `data` is shared by every drop of that item, so a drop
    // that failed to replace it would give the second node the first one's state.
    it('gives each drop a state of its own rather than the palette item placeholder', () => {
      drop('state');
      drop('state', { x: 420, y: 260 });

      const [first, second] = component.model.getNodes().slice(2) as StateNode[];
      expect(first.data.state).not.toBe(second.data.state);
      expect(STATE_PALETTE_ITEMS.map((item) => item.data.state.key)).toEqual(['', '', '']);
    });

    it('makes a dropped End symbol a final state', () => {
      drop('end');

      const dropped = component.model.getNodes().find((node) => node.id === 'STATE_1') as StateNode;
      expect(dropped.data.state.isFinal).toBe(true);
      expect(dropped.data.initial).toBe(false);
    });

    // A machine starts in exactly one state, so claiming the entry point has to release it too.
    it('moves the entry point onto a dropped Start symbol, off whichever state held it', () => {
      drop('start');

      const nodes = component.model.getNodes() as StateNode[];
      expect(nodes.filter((node) => node.data.initial).map((node) => node.id)).toEqual(['STATE_1']);
      expect(component.toMachine()?.initialStateKey).toBe('STATE_1');
    });

    it('adds the dropped state to the machine the toolbar saves', () => {
      drop('end');

      const saved = component.toMachine();
      expect(saved?.states.map((state) => state.key)).toEqual(['DRAFT', 'DELIVERED', 'STATE_1']);
      expect(saved?.transitions.map((transition) => transition.key)).toEqual(['confirm']);
      expect(saved?.version).toBe(3);
    });

    it('puts the dropped state in the arrangement the toolbar saves, at the position it was dropped at', () => {
      drop('state', { x: 500, y: 40 });

      expect(component.toLayout()?.nodes.find((node) => node.stateKey === 'STATE_1')?.position).toEqual({ x: 500, y: 40 });
    });
  });

  describe('toMachine', () => {
    it('reports nothing to save while no machine is loaded', () => {
      fixture.detectChanges();

      expect(component.toMachine()).toBeUndefined();
    });

    it('reproduces the loaded machine while nothing has been drawn', () => {
      fixture.componentRef.setInput('machine', machine);
      fixture.detectChanges();

      expect(component.toMachine()).toEqual(machine);
    });

    // ng-diagram removes the edges attached to a deleted node, so the transition goes with the state.
    it('drops a deleted state and the transitions that named it', () => {
      fixture.componentRef.setInput('machine', machine);
      fixture.detectChanges();
      component.model.updateNodes((nodes) => nodes.filter((node) => node.id !== 'DELIVERED'));
      component.model.updateEdges((edges) => edges.filter((edge) => edge.target !== 'DELIVERED'));

      const saved = component.toMachine();

      expect(saved?.states.map((state) => state.key)).toEqual(['DRAFT']);
      expect(saved?.transitions).toEqual([]);
    });
  });

  describe('applyStateEdit', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('machine', machine);
      fixture.detectChanges();
    });

    it('shows an edited name on the node and saves it with the machine', () => {
      const renamed = new State({ ...machine.states[0], name: 'Captured' });

      component.applyStateEdit({ previousKey: 'DRAFT', state: renamed, initial: true });

      const node = component.model.getNodes().find((candidate) => candidate.id === 'DRAFT') as StateNode;
      expect(node.data.label).toBe('Captured');
      expect(component.toMachine()?.states[0].name).toBe('Captured');
      expect(selectionService.selectedState()).toBe(renamed);
      expect(selectionService.selectedStateIsInitial()).toBe(true);
    });

    // The node id *is* the key, so a rename of the key is a re-identification of the node.
    it('re-keys the node when the key changes', () => {
      const rekeyed = new State({ ...machine.states[0], key: 'NEW' });

      component.applyStateEdit({ previousKey: 'DRAFT', state: rekeyed, initial: true });

      expect(component.model.getNodes().map((node) => node.id)).toEqual(['NEW', 'DELIVERED']);
      expect(component.toMachine()?.initialStateKey).toBe('NEW');
    });
  });
});
