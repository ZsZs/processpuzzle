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

  it('should render nodes and edges', () => {
    component.nodes = [mockNode];
    component.edges = [mockEdge];
    fixture.detectChanges();

    const nodeElement = fixture.nativeElement.querySelector('#state1');
    const edgeElement = fixture.nativeElement.querySelector('#trans1');

    expect(nodeElement).toBeTruthy();
    expect(edgeElement).toBeTruthy();
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
