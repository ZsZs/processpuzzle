import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { WORKFLOW_NODE_TYPE, WorkflowGraph } from '../../../domain/modeler/workflow-graph';
import { REFUSE_CONNECTION, WorkflowDiagramComponent } from './workflow-diagram.component';

/**
 * Asserted on the model rather than on the rendered DOM: the diagram is drawn by `<ng-diagram>`, whose
 * element structure is its own and is measurement-driven, so the ids of the nodes handed to it are nowhere
 * in this component's markup. The model is what this component is responsible for.
 */
describe('WorkflowDiagramComponent', () => {
  const graph: WorkflowGraph = {
    nodes: [
      { id: 'role:clerk', type: WORKFLOW_NODE_TYPE, position: { x: 0, y: 0 }, autoSize: true, data: { kind: 'role', label: 'Order Clerk' } },
      { id: 'artifact:order-entity', type: WORKFLOW_NODE_TYPE, position: { x: 0, y: 0 }, autoSize: true, data: { kind: 'artifact', label: 'Order Entity' } },
    ],
    edges: [{ id: 'role:clerk->artifact:order-entity', source: 'role:clerk', target: 'artifact:order-entity', data: {} }],
  };

  let component: WorkflowDiagramComponent;
  let fixture: ComponentFixture<WorkflowDiagramComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [WorkflowDiagramComponent] }).compileComponents();

    fixture = TestBed.createComponent(WorkflowDiagramComponent);
    component = fixture.componentInstance;
  });

  it('should compile and create component', () => {
    expect(component).toBeTruthy();
  });

  it('should start empty, rather than showing a diagram nobody asked for', () => {
    fixture.detectChanges();

    expect(component.model.getNodes()).toEqual([]);
    expect(component.model.getEdges()).toEqual([]);
  });

  it('draws the nodes and edges of the graph it is given', () => {
    fixture.componentRef.setInput('graph', graph);
    fixture.detectChanges();

    expect(component.model.getNodes().map((node) => node.id)).toEqual(['role:clerk', 'artifact:order-entity']);
    expect(component.model.getEdges().map((edge) => [edge.source, edge.target])).toEqual([['role:clerk', 'artifact:order-entity']]);
  });

  it('draws every element through the one element template', () => {
    fixture.componentRef.setInput('graph', graph);
    fixture.detectChanges();

    expect(component.model.getNodes().map((node) => node.type)).toEqual([WORKFLOW_NODE_TYPE, WORKFLOW_NODE_TYPE]);
    expect(component.nodeTemplateMap.get(WORKFLOW_NODE_TYPE)).toBeDefined();
  });

  // The graph arrives from a converter with no positions; the layout service is what places it, and a
  // diagram whose nodes all sat at the origin would draw one node on top of another.
  it('places the nodes it was handed unplaced', () => {
    fixture.componentRef.setInput('graph', graph);
    fixture.detectChanges();

    const positions = component.model.getNodes().map((node) => node.position);
    expect(positions[0]).not.toEqual(positions[1]);
  });

  /**
   * Read-only is on the nodes, not merely on the model: ng-diagram 1.3 has no `readOnly` config, and its
   * own recipe — a middleware that cancels model actions — suppresses the *effect* of a gesture the user
   * was still invited to make.
   */
  it('offers no gesture that would change what is drawn', () => {
    fixture.componentRef.setInput('graph', graph);
    fixture.detectChanges();

    expect(component.model.getNodes().every((node) => node.draggable === false && node.resizable === false && node.rotatable === false)).toBe(true);
    expect(component.config.linking?.validateConnection).toBe(REFUSE_CONNECTION);
    expect(REFUSE_CONNECTION()).toBe(false);
  });

  it('frames the whole diagram once, when it initializes', () => {
    expect(component.config.zoom?.zoomToFit?.onInit).toBe(true);
  });

  it('empties the canvas when the graph goes away', () => {
    fixture.componentRef.setInput('graph', graph);
    fixture.detectChanges();
    fixture.componentRef.setInput('graph', undefined);
    fixture.detectChanges();

    expect(component.model.getNodes()).toEqual([]);
  });
});
