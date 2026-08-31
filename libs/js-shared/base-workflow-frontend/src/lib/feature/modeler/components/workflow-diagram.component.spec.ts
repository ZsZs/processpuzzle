import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { DiagramEdgeLayout, DiagramNodeLayout, DiagramViewport, NodeSize, Point, WorkflowDiagram } from '../../../domain/modeler/models/workflow-diagram';
import { WORKFLOW_LANE_TYPE, WORKFLOW_NODE_TYPE, WORKFLOW_RELATION_EDGE_TYPE, WorkflowGraph, WorkflowNode } from '../../../domain/modeler/workflow-graph';
import { WorkflowSelectionService } from '../services/workflow-selection.service';
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

  /**
   * `editable` lifts exactly one restriction — the handles come back — and the four that guard what the
   * workflow *contains* hold either way. Which of them matters most changes, though: `canGroup` could not be
   * reached at all while nothing was draggable, and is now the whole of what keeps dragging a task into the
   * band below from reassigning who performs it.
   */
  describe('when editable', () => {
    beforeEach(() => {
      fixture.componentRef.setInput('editable', true);
      fixture.componentRef.setInput('graph', graph);
      fixture.detectChanges();
    });

    it('lets every node be dragged and resized', () => {
      expect(component.model.getNodes().every((node) => node.draggable !== false && node.resizable !== false)).toBe(true);
    });

    /**
     * A rotated task card conveys nothing, and the layout's arithmetic — columns pitched by a node's width,
     * bands measured by its height — is stated in unrotated boxes.
     */
    it('still refuses rotation', () => {
      expect(component.model.getNodes().every((node) => node.rotatable === false)).toBe(true);
    });

    it('still refuses a new edge and a lane membership change', () => {
      expect(component.config.linking?.validateConnection).toBe(REFUSE_CONNECTION);
      expect(component.config.grouping?.canGroup).toBe(REFUSE_GROUPING);
    });

    it('still binds no destructive keyboard shortcut', () => {
      expect(component.config.shortcuts).toEqual([]);
    });
  });

  describe('a saved arrangement', () => {
    const saved = new WorkflowDiagram({
      workflowId: 'order-fulfillment-workflow',
      nodes: [new DiagramNodeLayout({ nodeId: 'artifact:order-entity', position: new Point({ x: 900, y: 800 }), size: new NodeSize({ width: 200, height: 90 }) })],
      edges: [new DiagramEdgeLayout({ edgeId: 'role:clerk->artifact:order-entity', sourcePort: 'port-bottom', targetPort: 'port-top' })],
      viewport: new DiagramViewport({ x: -20, y: -30, scale: 0.9 }),
    });

    beforeEach(() => {
      fixture.componentRef.setInput('editable', true);
      fixture.componentRef.setInput('savedLayout', saved);
      fixture.componentRef.setInput('graph', graph);
      fixture.detectChanges();
    });

    it('puts a saved node where it was left', () => {
      expect(component.model.getNodes().find((node) => node.id === 'artifact:order-entity')?.position).toEqual({ x: 900, y: 800 });
    });

    // A node the arrangement does not mention keeps the position the automatic layout computed.
    it('leaves an unsaved node where the layout placed it', () => {
      expect(component.model.getNodes().find((node) => node.id === 'role:clerk')?.position).not.toEqual({ x: 900, y: 800 });
    });

    it('restores the port anchors of a saved edge', () => {
      const edge = component.model.getEdges()[0];

      expect([edge.sourcePort, edge.targetPort]).toEqual(['port-bottom', 'port-top']);
    });

    it('restores the viewport the canvas was left at', () => {
      expect(component.model.getMetadata().viewport).toMatchObject({ x: -20, y: -30, scale: 0.9 });
    });

    /**
     * A layer toggle rebuilds the model, and taking the *saved* viewport there would snap the canvas back to
     * wherever it was last saved — throwing away the pan the user has made since.
     */
    it('keeps where the user has panned to across a rebuild', () => {
      component.model.updateMetadata((metadata) => ({ ...metadata, viewport: { x: -500, y: -600, scale: 2 } }));

      fixture.componentRef.setInput('graph', { ...graph, nodes: [...graph.nodes] });
      fixture.detectChanges();

      expect(component.model.getMetadata().viewport).toMatchObject({ x: -500, y: -600, scale: 2 });
    });

    /**
     * An automatic fit and a restored viewport are two answers to the same question, and leaving the fit on
     * would throw away the pan and zoom the user saved.
     */
    it('does not also fit the diagram on init', () => {
      expect(component.config.zoom?.zoomToFit?.onInit).toBe(false);
    });

    /**
     * A save writes the arrangement back into the store, which feeds it here again — the very arrangement the
     * canvas is already showing. Rebuilding on that would cost the user their selection and re-frame the
     * diagram to show nothing new.
     */
    it('does not rebuild when a save hands the same arrangement back', () => {
      const selection = TestBed.inject(WorkflowSelectionService);
      selection.selectElement({ kind: 'task', label: 'Review Order' });
      const before = component.model;

      fixture.componentRef.setInput('savedLayout', new WorkflowDiagram({ ...saved, version: 3 }));
      fixture.detectChanges();

      expect(component.model).toBe(before);
      expect(selection.selectedElement()).toBeDefined();
    });

    // The arrangement still updates, so the next save is locked on the version the server just assigned.
    it('still carries the refreshed version into the next save', () => {
      fixture.componentRef.setInput('savedLayout', new WorkflowDiagram({ ...saved, version: 3 }));
      fixture.detectChanges();

      expect(component.toLayout('order-fulfillment-workflow')?.version).toBe(3);
    });

    describe('toLayout', () => {
      it('reads the arrangement back off the model', () => {
        const layout = component.toLayout('order-fulfillment-workflow');

        expect(layout?.workflowId).toBe('order-fulfillment-workflow');
        expect(layout?.nodes.find((node) => node.nodeId === 'artifact:order-entity')?.position).toEqual(new Point({ x: 900, y: 800 }));
      });

      it('carries the viewport, so a pan with no drag is still saved', () => {
        expect(component.toLayout('order-fulfillment-workflow')?.viewport).toEqual(new DiagramViewport({ x: -20, y: -30, scale: 0.9 }));
      });

      // The write is optimistic-locked, so the version read has to be the version sent.
      it('carries the version the arrangement was read at', () => {
        fixture.componentRef.setInput('savedLayout', new WorkflowDiagram({ workflowId: 'order-fulfillment-workflow', version: 7 }));
        fixture.detectChanges();

        expect(component.toLayout('order-fulfillment-workflow')?.version).toBe(7);
      });

      it('has nothing to save when there is no graph', () => {
        fixture.componentRef.setInput('graph', undefined);
        fixture.detectChanges();

        expect(component.toLayout('order-fulfillment-workflow')).toBeUndefined();
      });
    });
  });

  /**
   * Clicking an element to read it is not a change, so selection is live in both modes — it is what gives the
   * properties panels a subject.
   */
  describe('selection', () => {
    let selection: WorkflowSelectionService;

    beforeEach(() => {
      selection = TestBed.inject(WorkflowSelectionService);
      fixture.componentRef.setInput('graph', graph);
      fixture.detectChanges();
    });

    function select(nodeIds: string[], edgeIds: string[] = []): void {
      component['onSelectionChanged']({
        selectedNodes: component.model.getNodes().filter((node) => nodeIds.includes(node.id)),
        selectedEdges: component.model.getEdges().filter((edge) => edgeIds.includes(edge.id)),
      } as never);
    }

    it('forwards a single selected node as the data behind it', () => {
      select(['role:clerk']);

      expect(selection.selectedElement()?.label).toBe('Order Clerk');
      expect(selection.selectedElementIsLane()).toBe(false);
    });

    it('forwards a single selected edge as its relation', () => {
      select([], ['role:clerk->artifact:order-entity']);

      expect(selection.selectedRelation()).toBeDefined();
      expect(selection.selectedElement()).toBeUndefined();
    });

    // A box selection of three tasks has no one subject to show, and clearing is the honest answer.
    it('clears on a multiple selection', () => {
      select(['role:clerk']);
      select(['role:clerk', 'artifact:order-entity']);

      expect(selection.selectedElement()).toBeUndefined();
    });

    it('clears when the canvas is deselected', () => {
      select(['role:clerk']);
      select([]);

      expect(selection.selectedElement()).toBeUndefined();
    });

    // A reload replaces the graph, so whatever was selected in the previous one no longer exists.
    it('clears when the graph is rebuilt', () => {
      select(['role:clerk']);
      fixture.componentRef.setInput('graph', { ...graph, nodes: [...graph.nodes] });
      fixture.detectChanges();

      expect(selection.selectedElement()).toBeUndefined();
    });

    it('reports a lane as one', () => {
      const laneGraph: WorkflowGraph = {
        nodes: [{ id: 'lane:clerk', type: WORKFLOW_LANE_TYPE, isGroup: true, highlighted: false, position: { x: 0, y: 0 }, data: { kind: 'role', label: 'Order Clerk' } }],
        edges: [],
      };
      fixture.componentRef.setInput('graph', laneGraph);
      fixture.detectChanges();

      select(['lane:clerk']);

      expect(selection.selectedElementIsLane()).toBe(true);
    });
  });
});
