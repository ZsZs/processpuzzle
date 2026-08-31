import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { WORKFLOW_LANE_TYPE, WORKFLOW_NODE_TYPE, WORKFLOW_RELATION_EDGE_TYPE, WorkflowGraph, WorkflowNode } from '../../../domain/modeler/workflow-graph';
import { REFUSE_CONNECTION, REFUSE_GROUPING, WorkflowDiagramComponent, WorkflowGraphLayout } from './workflow-diagram.component';

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
    expect(component.config.grouping?.canGroup).toBe(REFUSE_GROUPING);
    expect(REFUSE_GROUPING()).toBe(false);
  });

  /**
   * The one read-only setting that is not about gestures, and the one that breaks a swimlane diagram if it
   * is missed. ng-diagram defaults `elevateOnSelection` to true with a `selectedZIndex` of 10000, and offers
   * no way to make a node unselectable — so a click anywhere on a lane's full-width band would lift it above
   * every edge on the canvas and hide most of the flow behind it.
   */
  it('does not let a selected node rise above the edges', () => {
    expect(component.config.zIndex?.elevateOnSelection).toBe(false);
  });

  // They act on the in-memory model, so on a read-only diagram they can only make it disagree with the
  // workflow it draws until the next rebuild.
  it('binds no destructive keyboard shortcut', () => {
    expect(component.config.shortcuts).toEqual([]);
  });

  describe('lanes and typed edges', () => {
    const swimlaneGraph: WorkflowGraph = {
      nodes: [
        { id: 'lane:clerk', type: WORKFLOW_LANE_TYPE, isGroup: true, highlighted: false, position: { x: 0, y: 0 }, data: { kind: 'role', label: 'Order Clerk' } },
        { id: 'task:review-order', type: WORKFLOW_NODE_TYPE, groupId: 'lane:clerk', position: { x: 0, y: 0 }, autoSize: true, data: { kind: 'task', label: 'Review Order' } },
      ],
      edges: [{ id: 'a->b', source: 'task:review-order', target: 'task:review-order', type: WORKFLOW_RELATION_EDGE_TYPE, data: { relation: 'sequence' } }],
    };

    it('registers a template for a lane as well as for an element', () => {
      expect(component.nodeTemplateMap.get(WORKFLOW_NODE_TYPE)).toBeDefined();
      expect(component.nodeTemplateMap.get(WORKFLOW_LANE_TYPE)).toBeDefined();
    });

    it('registers the typed relation edge', () => {
      expect(component.edgeTemplateMap.get(WORKFLOW_RELATION_EDGE_TYPE)).toBeDefined();
    });

    it('keeps a lane a group node and locks it like everything else', () => {
      fixture.componentRef.setInput('graph', swimlaneGraph);
      fixture.detectChanges();

      const lane = component.model.getNodes().find((node) => node.id === 'lane:clerk') as WorkflowNode;
      expect('isGroup' in lane).toBe(true);
      expect([lane.draggable, lane.resizable, lane.rotatable]).toEqual([false, false, false]);
    });

    /**
     * The layout is an input, not a name this component switches on — which is the whole reason a spec can
     * assert the positions the model received without a layout engine anywhere near the test.
     */
    it('places the graph with the layout it was given', () => {
      const stub: WorkflowGraphLayout = { place: (nodes) => nodes.map((node, index) => ({ ...node, position: { x: 11 * (index + 1), y: 22 } })) };
      fixture.componentRef.setInput('layout', stub);
      fixture.componentRef.setInput('graph', swimlaneGraph);
      fixture.detectChanges();

      expect(component.model.getNodes().map((node) => node.position)).toEqual([
        { x: 11, y: 22 },
        { x: 22, y: 22 },
      ]);
    });

    it('defaults to the flat flow layout, so the Roles diagram is unchanged', () => {
      fixture.componentRef.setInput('graph', graph);
      fixture.detectChanges();

      // The flow layout's signature: an LR chain, so the two nodes differ in x and share a y.
      const positions = component.model.getNodes().map((node) => node.position);
      expect(positions[0].x).not.toBe(positions[1].x);
    });
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
