import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { DiagramDefinitionMapper } from '../../../domain/modeler/data-access/diagram-definition.mapper';
import { STATE_NODE_TYPE } from '../../../domain/modeler/graph/state-machine-graph';
import { DIAGRAM_DEFINITION_DTO } from '../../../domain/modeler/models/test-diagram-definition';
import { StateMachineDefinitionMapper } from '../../../domain/state-machine-definition.mapper';
import { STATE_MACHINE_DEFINITION_DTO } from '../../../domain/test-state-machine-definition';
import { DiagramSelectionService } from '../services/diagram-selection.service';
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
      providers: [DiagramSelectionService],
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
});
