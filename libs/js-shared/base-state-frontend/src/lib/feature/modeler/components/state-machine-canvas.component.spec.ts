import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { StateMachineCanvasComponent } from './state-machine-canvas.component';
import { DiagramSelectionService } from '../services/diagram-selection.service';
import { GraphNode } from '../../../domain/modeler/graph/graph-model/graph-node';
import { GraphEdge } from '../../../domain/modeler/graph/graph-model/graph-edge';

describe('StateMachineCanvasComponent', () => {
  let component: StateMachineCanvasComponent;
  let fixture: ComponentFixture<StateMachineCanvasComponent>;
  let selectionService: DiagramSelectionService;

  const mockNode: GraphNode = {
    id: 'state1',
    type: 'state',
    label: 'State 1',
    data: { name: 'State 1', description: 'Test State', locked: false, terminal: false },
    position: { x: 100, y: 150 },
  };

  const mockEdge: GraphEdge = {
    id: 'trans1',
    type: 'transition',
    source: 'state1',
    target: 'state2',
    label: 'Transition 1',
    data: { name: 'Transition 1' },
  };

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

  // Asserted on the model rather than on the rendered DOM: the graph is drawn by `<ng-diagram>`, whose
  // element structure is its own and is measurement-driven, so the ids of the nodes handed to it are
  // nowhere in this component's markup. The model is what the component is actually responsible for.
  it('should render nodes and edges', () => {
    fixture.componentRef.setInput('nodes', [mockNode]);
    fixture.componentRef.setInput('edges', [mockEdge]);
    fixture.detectChanges();

    expect(component.model.getNodes().map((node) => node.id)).toEqual(['state1']);
    expect(component.model.getEdges().map((edge) => [edge.id, edge.source, edge.target])).toEqual([['trans1', 'state1', 'state2']]);
  });

  // The label is what a node template reads, and the State/Transition the properties panels edit has
  // to survive the trip — so `data` carries both rather than one replacing the other.
  it('should carry the label and the domain payload of every node and edge', () => {
    fixture.componentRef.setInput('nodes', [mockNode]);
    fixture.componentRef.setInput('edges', [mockEdge]);
    fixture.detectChanges();

    expect(component.model.getNodes()[0].data).toMatchObject({ label: 'State 1', name: 'State 1', description: 'Test State' });
    expect(component.model.getNodes()[0].position).toEqual({ x: 100, y: 150 });
    expect(component.model.getEdges()[0].data).toMatchObject({ label: 'Transition 1', name: 'Transition 1' });
  });

  // StateToNodeConverter leaves `position` for the layout engine to fill in, and ng-diagram requires
  // one — an unplaced node is drawn at the origin rather than dropped.
  it('should place a node the layout engine has not positioned yet at the origin', () => {
    fixture.componentRef.setInput('nodes', [{ ...mockNode, position: undefined }]);
    fixture.detectChanges();

    expect(component.model.getNodes()[0].position).toEqual({ x: 0, y: 0 });
  });

  it('should start empty, rather than showing a graph nobody asked for', () => {
    fixture.detectChanges();

    expect(component.model.getNodes()).toEqual([]);
    expect(component.model.getEdges()).toEqual([]);
  });

  it('should handle node click selection', () => {
    component.onNodeClick(mockNode);
    expect(selectionService.selectedNode).toBe(mockNode);
    expect(selectionService.selectedEdge).toBeUndefined();
  });

  it('should handle edge click selection', () => {
    component.onEdgeClick(mockEdge);
    expect(selectionService.selectedEdge).toBe(mockEdge);
    expect(selectionService.selectedNode).toBeUndefined();
  });
});
