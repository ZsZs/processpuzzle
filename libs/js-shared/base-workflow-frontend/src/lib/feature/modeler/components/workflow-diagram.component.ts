import { Component, inject, Injector, Input, OnChanges } from '@angular/core';
import { initializeModel, NgDiagramComponent, NgDiagramConfig, NgDiagramEdgeTemplateMap, NgDiagramNodeTemplateMap, provideNgDiagram } from 'ng-diagram';
import { WorkflowLayoutService } from '../../../domain/modeler/graph/workflow-layout.service';
import { WORKFLOW_LANE_TYPE, WORKFLOW_NODE_TYPE, WORKFLOW_RELATION_EDGE_TYPE, WorkflowEdge, WorkflowGraph, WorkflowNode } from '../../../domain/modeler/workflow-graph';
import { WorkflowElementNodeComponent } from './workflow-element-node.component';
import { WorkflowLaneNodeComponent } from './workflow-lane-node.component';
import { WorkflowRelationEdgeComponent } from './workflow-relation-edge.component';

/**
 * Draws one modeler perspective, read-only.
 *
 * It takes a converted {@link WorkflowGraph} and, optionally, the {@link WorkflowGraphLayout} that places
 * it — and nothing else. That is what makes it perspective-agnostic: the Roles diagram, the Workflows one
 * and the Tasks one to come differ in their converter and their layout, and share this canvas. Everything
 * that makes a diagram *appear* — resolving the symbols, registering the templates, taking the editing
 * gestures away — is identical for all of them.
 *
 * Both node templates and the relation edge template are registered unconditionally. A registered type
 * nothing emits costs nothing, whereas registering per perspective would be this component knowing which
 * perspective it was drawing.
 *
 * The model is rebuilt through `initializeModel(..., injector)` whenever an input changes, which is
 * ng-diagram's prescribed path for data arriving after construction; nothing here mutates nodes in place,
 * since the library's reactivity runs through the model adapter.
 *
 * Read-only in five places. Nodes are marked undraggable, unresizable and unrotatable, because ng-diagram
 * 1.3 has no `readOnly` config and `draggable: false` is what stops the invitation rather than merely
 * undoing its effect. `validateConnection` refuses new edges and `canGroup` refuses lane membership changes,
 * since either would stand for a relation nothing would persist. `shortcuts: []` drops the destructive
 * keyboard bindings. And `zIndex.elevateOnSelection` is turned off, without which selecting a lane would
 * lift its band over every edge on the canvas — see {@link config}. Selection itself is deliberately left
 * alone: clicking an element to read it is not a change.
 *
 * `zoomToFit.onInit` is why the host renders this component only once it has a graph — see
 * {@link RoleModelerTabComponent}. The fit happens when the diagram initializes, so a canvas created empty
 * and filled a moment later, once the catalogs arrive, would frame nothing.
 */
/**
 * The connection validator the canvas installs: no edge the user draws is ever accepted, because an edge
 * here is a responsibility, and responsibilities are authored on the generated Role form.
 *
 * Named rather than inlined so the spec can call it: `NgDiagramConfig` is a deep-partial of ng-diagram's
 * config, which erases the call signature of every function in it.
 */
export const REFUSE_CONNECTION = (): boolean => false;

/**
 * The grouping validator: no node is ever moved into or out of a lane, because lane membership is
 * `WorkflowTaskAssignment.performedBy` and that is authored on the generated Workflow form.
 *
 * An invariant rather than a defence. ng-diagram only consults `canGroup` on the drag path, and nothing on
 * this canvas is draggable, so it cannot currently be reached — it is here so that a change making something
 * draggable does not silently also make lane membership editable.
 */
export const REFUSE_GROUPING = (): boolean => false;

/**
 * How a graph's nodes get their positions. Two implement it: `WorkflowLayoutService` for a flat flow and
 * `SwimlaneLayoutService` for lanes.
 *
 * An interface rather than a `'flow' | 'swimlane'` union, because this component's whole claim is that it
 * does not know the perspectives — a union of their names in the file that says so would be a contradiction
 * that grows by one member per perspective. It also lets a spec pass a stub and assert the exact positions
 * the model received, with no layout engine in the test.
 */
export interface WorkflowGraphLayout {
  place(nodes: WorkflowNode[], edges: WorkflowEdge[]): WorkflowNode[];
}

@Component({
  selector: 'pp-workflow-diagram',
  standalone: true,
  imports: [NgDiagramComponent],
  providers: [provideNgDiagram()],
  template: ` <ng-diagram [model]="model" [config]="config" [nodeTemplateMap]="nodeTemplateMap" [edgeTemplateMap]="edgeTemplateMap" /> `,
  styles: `
    :host {
      display: flex;
      height: 100%;
      min-height: 300px;
    }
    /* The zero min-width is there because ng-diagram measures itself against this box, and a flex item's
       automatic minimum would otherwise let its content widen it. */
    ng-diagram {
      flex: 1;
      min-width: 0;
    }
  `,
})
export class WorkflowDiagramComponent implements OnChanges {
  /** What to draw. Absent until the converter has something to convert; an absent graph draws nothing. */
  @Input() graph?: WorkflowGraph;

  /**
   * How each perspective's graph is laid out. Defaults to the flat flow, which is what the Roles diagram
   * has always used and what a lane-less graph wants; the Workflows tab passes the swimlane layout.
   */
  @Input() layout: WorkflowGraphLayout = inject(WorkflowLayoutService);

  /**
   * Both node templates, registered unconditionally. A type nothing emits costs nothing, and registering per
   * perspective would make this component know which perspective it was drawing.
   */
  readonly nodeTemplateMap = new NgDiagramNodeTemplateMap([
    [WORKFLOW_NODE_TYPE, WorkflowElementNodeComponent],
    [WORKFLOW_LANE_TYPE, WorkflowLaneNodeComponent],
  ]);

  /**
   * The typed-relation edge. Only the Workflows perspective sets an edge `type`; a Roles edge leaves it
   * unset and so keeps ng-diagram's default template, exactly as before.
   */
  readonly edgeTemplateMap = new NgDiagramEdgeTemplateMap([[WORKFLOW_RELATION_EDGE_TYPE, WorkflowRelationEdgeComponent]]);

  /**
   * Refuses every gesture that would change what is drawn, and frames the whole diagram on init.
   *
   * `zIndex.elevateOnSelection` is the one that is not obvious, and the one that matters most once there are
   * lanes. ng-diagram defaults it to true with a `selectedZIndex` of 10000, and it offers no way to make a
   * node unselectable — so a click anywhere on a lane's band would lift that band above every edge on the
   * canvas and hide most of the flow behind it. Selection itself is left alone, here as in the Roles
   * diagram: clicking an element to read it is not a change.
   *
   * `shortcuts: []` drops Delete, Backspace, cut and paste. They act on the in-memory model, so on a
   * read-only diagram they can only ever make it disagree with the workflow it is drawing until the next
   * rebuild.
   */
  readonly config: NgDiagramConfig = {
    linking: { validateConnection: REFUSE_CONNECTION },
    grouping: { canGroup: REFUSE_GROUPING },
    zoom: { zoomToFit: { onInit: true, padding: 40 } },
    zIndex: { elevateOnSelection: false },
    shortcuts: [],
  };

  private readonly injector = inject(Injector);

  /** Empty until a graph arrives — an unloaded canvas shows nothing, not a placeholder diagram. */
  model = initializeModel({ nodes: [], edges: [] }, this.injector);

  ngOnChanges(): void {
    if (!this.graph) {
      this.model = initializeModel({ nodes: [], edges: [] }, this.injector);
      return;
    }
    // A **new** model identity on every change, which is what re-frames the diagram. ng-diagram destroys
    // and re-creates its flow core when the `model` reference changes, which re-fires `diagramInit` and so
    // `zoomToFit.onInit` — so a layer toggle reframes to the new content for free. Mutating the adapter
    // through `NgDiagramModelService` instead would silently stop that happening.
    this.model = initializeModel({ nodes: this.lock(this.layout.place(this.graph.nodes, this.graph.edges)), edges: this.graph.edges }, this.injector);
  }

  /**
   * Takes the drag, resize and rotate handles off every node, lanes included.
   *
   * Every node, not just the ones with handles worth hiding: ng-diagram expands a drag to a group's children
   * and *then* filters by `draggable`, so a child left unset defaults to draggable and would move with its
   * lane. Mapping the whole array is what makes that impossible.
   */
  private lock(nodes: WorkflowNode[]): WorkflowNode[] {
    return nodes.map((node) => ({ ...node, draggable: false, resizable: false, rotatable: false }));
  }
}
