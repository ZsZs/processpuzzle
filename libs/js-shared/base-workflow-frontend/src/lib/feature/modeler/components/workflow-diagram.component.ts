import { Component, effect, ElementRef, inject, Injector, Input, OnChanges, signal, SimpleChanges } from '@angular/core';
import {
  ConnectionValidationContext,
  initializeModel,
  NgDiagramComponent,
  NgDiagramConfig,
  NgDiagramEdgeTemplateMap,
  NgDiagramNodeTemplateMap,
  Node,
  provideNgDiagram,
  SelectionChangedEvent,
} from 'ng-diagram';
import { applySavedLayout, toDiagram } from '../../../domain/modeler/graph/workflow-diagram.converter';
import { WorkflowLayoutService } from '../../../domain/modeler/graph/workflow-layout.service';
import { DiagramViewport, WorkflowDiagram } from '../../../domain/modeler/models/workflow-diagram';
import {
  isBoundaryNode,
  isLaneNode,
  WORKFLOW_LANE_TYPE,
  WORKFLOW_NODE_TYPE,
  WORKFLOW_RELATION_EDGE_TYPE,
  WorkflowEdge,
  WorkflowEdgeData,
  WorkflowGraph,
  WorkflowNode,
  WorkflowNodeData,
} from '../../../domain/modeler/workflow-graph';
import { EdgeContextMenuService } from '../services/edge-context-menu.service';
import { WorkflowSelectionService } from '../services/workflow-selection.service';
import { EdgeRoutingMenuComponent } from './edge-routing-menu.component';
import { activeEdgeRouting, DEFAULT_EDGE_ROUTING, EdgeRoutingChoice } from './edge-routing-options';
import { WorkflowElementNodeComponent } from './workflow-element-node.component';
import { WorkflowLaneNodeComponent } from './workflow-lane-node.component';
import { WorkflowRelationEdgeComponent } from './workflow-relation-edge.component';

/**
 * Draws one modeler perspective.
 *
 * It takes a converted {@link WorkflowGraph} and, optionally, the {@link WorkflowGraphLayout} that places it
 * and the {@link WorkflowDiagram} that overrides that placement — and nothing else. That is what makes it
 * perspective-agnostic: the Roles diagram, the Workflows one and the Tasks one to come differ in their
 * converter and their layout, and share this canvas. Everything that makes a diagram *appear* — resolving
 * the symbols, registering the templates, restoring a saved arrangement — is identical for all of them.
 *
 * Both node templates and the relation edge template are registered unconditionally. A registered type
 * nothing emits costs nothing, whereas registering per perspective would be this component knowing which
 * perspective it was drawing.
 *
 * The model is rebuilt through `initializeModel(..., injector)` whenever an input changes, which is
 * ng-diagram's prescribed path for data arriving after construction; nothing here mutates nodes in place,
 * since the library's reactivity runs through the model adapter.
 *
 * ## What {@link editable} does and does not open up
 *
 * Read-only is the default, and {@link editable} lifts the restrictions on *arranging*: the drag, resize and
 * rotate handles come back, so a user can move a task out of an edge's way and give a lane the height they
 * want; an edge's ends can be relinked to another port of the node they already join; and a right-click on
 * a relation opens the routing menu. {@link toLayout} is how that arrangement is read back out, and the
 * host's Save button is what persists it.
 *
 * The other four restrictions hold either way, and each guards something an arrangement must not be able to
 * change. `validateConnection` refuses new edges and any relink to a *different* node, because an edge here
 * is a `dependsOn` entry or a task's declared input — authored on a generated form, with fields nothing on
 * this canvas could supply. Moving an end between ports of the same node changes the picture only.
 * `canGroup` refuses lane membership changes, because a node's lane *is*
 * `WorkflowTaskAssignment.performedBy`; without it, dragging a task into the lane below would silently
 * reassign who performs it. `shortcuts: []` drops Delete, Backspace, cut and paste, which act on the
 * in-memory model and so could only ever make the diagram disagree with the workflow it is drawing. And
 * `zIndex.elevateOnSelection` stays off, without which clicking a lane would lift its band over every edge
 * on the canvas — see {@link config}.
 *
 * Selection is deliberately available in both modes: clicking an element to read it in the properties panel
 * is not a change.
 */
/**
 * The connection validator the canvas installs: no edge the user draws is ever accepted, because an edge
 * stands for a relation authored on a generated form — a responsibility on the Roles perspective, a
 * dependency or a declared input on the Workflows one.
 *
 * A relink is accepted only when the dragged end lands on the node it already joined. That is a different
 * *port* of the same element — arrangement, persisted as the edge's port in the layout — whereas any other
 * node would be a different relation, and so a change to the workflow this canvas does not make.
 *
 * Named rather than inlined so the spec can call it: `NgDiagramConfig` is a deep-partial of ng-diagram's
 * config, which erases the call signature of every function in it.
 */
export const SAME_NODE_RELINK_ONLY = (source: Node | null, _sourcePort: unknown, target: Node | null, _targetPort: unknown, context?: ConnectionValidationContext): boolean => {
  if (context?.reason !== 'relink' || !context.edge) return false;
  return context.end === 'source' ? source?.id === context.edge.source : target?.id === context.edge.target;
};

/**
 * The grouping validator: no node is ever moved into or out of a lane, because lane membership is
 * `WorkflowTaskAssignment.performedBy` and that is authored on the generated Workflow form.
 *
 * This is the one refusal that {@link WorkflowDiagramComponent.editable} makes load-bearing. ng-diagram only
 * consults `canGroup` on the drag path, so while nothing was draggable it could not be reached at all; now
 * that tasks drag, it is the whole of what keeps dragging one into the band below from reassigning it.
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
  imports: [NgDiagramComponent, EdgeRoutingMenuComponent],
  providers: [provideNgDiagram(), EdgeContextMenuService],
  template: `
    <ng-diagram [model]="model" [config]="config" [nodeTemplateMap]="nodeTemplateMap" [edgeTemplateMap]="edgeTemplateMap" (selectionChanged)="onSelectionChanged($event)" />

    @if (editable && contextMenu.target(); as target) {
      <pp-edge-routing-menu
        [x]="menuPosition().x"
        [y]="menuPosition().y"
        [active]="activeRouting(target.edgeId)"
        (chosen)="applyRouting(target.edgeId, $event)"
        (closed)="contextMenu.close()"
      />
    }
  `,
  styles: `
    /* Positioned, because the routing menu is placed inside this box - see EdgeRoutingMenuComponent. */
    :host {
      display: flex;
      position: relative;
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
   * The saved arrangement, applied over whatever {@link layout} computed. Absent is the normal starting
   * point — a workflow that has never been arranged — and leaves the computed layout standing.
   */
  @Input() savedLayout?: WorkflowDiagram;

  /**
   * Lets the user rearrange what is drawn: drag a node, resize a lane. See the class comment for what this
   * does *not* open up, which is everything that would change the workflow rather than its picture.
   *
   * Off by default, so the Roles perspective keeps the behaviour it has always had without saying so.
   */
  @Input() editable = false;

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
   * Refuses every gesture that would change *what* is drawn, and frames the diagram on init unless a saved
   * viewport says where to look instead.
   *
   * Reassigned in {@link ngOnChanges} rather than fixed, which works because ng-diagram's `config` is an
   * input signal and so tracks the reference. The only thing that varies is `zoomToFit.onInit`: an
   * automatic fit and a restored viewport are two answers to the same question, and leaving the fit on
   * would throw away the pan and zoom the user saved. Everything else is the same in both modes — see the
   * class comment.
   *
   * `zIndex.elevateOnSelection` is the one that is not obvious, and the one that matters most once there are
   * lanes. ng-diagram defaults it to true with a `selectedZIndex` of 10000, and it offers no way to make a
   * node unselectable — so a click anywhere on a lane's band would lift that band above every edge on the
   * canvas and hide most of the flow behind it.
   */
  config: NgDiagramConfig = diagramConfig(true, false);

  protected readonly contextMenu = inject(EdgeContextMenuService);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly selection = inject(WorkflowSelectionService);

  /** Where the open routing menu sits, as an offset inside this component's box. */
  private readonly menuPositionSignal = signal({ x: 0, y: 0 });
  protected readonly menuPosition = this.menuPositionSignal.asReadonly();

  /** Empty until a graph arrives — an unloaded canvas shows nothing, not a placeholder diagram. */
  model = initializeModel({ nodes: [], edges: [] }, this.injector);

  constructor() {
    // The right-click reports viewport coordinates and the menu is placed inside this box, so the offset is
    // computed here — once per open, rather than measuring the box on every change detection.
    effect(() => {
      const target = this.contextMenu.target();
      if (!target) return;
      const bounds = this.host.nativeElement.getBoundingClientRect();
      this.menuPositionSignal.set({ x: target.clientX - bounds.left, y: target.clientY - bounds.top });
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    // A save writes the arrangement back into the store, which feeds `savedLayout` again — with the very
    // arrangement the canvas is already showing. Rebuilding on that would cost the user their selection and
    // re-frame the diagram to show nothing new, so the only `savedLayout` change worth rebuilding for is the
    // *first* one: a layout that arrived after the graph did.
    if (isPostSaveRefresh(changes)) return;
    // The model is about to be rebuilt, so the edge an open routing menu was opened on may not survive it.
    this.contextMenu.close();

    const viewport = this.viewportToKeep();
    const fitOnInit = !viewport;
    // Reassigned only when it actually differs: `config` is an input signal, so a new object identity is a
    // change ng-diagram would act on.
    if (this.config.zoom?.zoomToFit?.onInit !== fitOnInit || this.config.linking?.defaultRelinkable !== this.editable) this.config = diagramConfig(fitOnInit, this.editable);
    // A reload replaces the graph, so whatever was selected in the previous one no longer exists.
    this.selection.clear();

    if (!this.graph) {
      this.model = initializeModel({ nodes: [], edges: [] }, this.injector);
      return;
    }

    // Placed, then moved to where it was saved, then locked: the automatic layout has to run first because
    // it is what computes each lane's band from the tasks in it, and a node the saved arrangement does not
    // mention keeps the position it computed — which is how a task added since the last save turns up in
    // the right lane rather than at the origin.
    const placed = applySavedLayout(this.layout.place(this.graph.nodes, this.graph.edges), this.graph.edges, this.savedLayout);

    // A **new** model identity on every change, which is what re-frames the diagram. ng-diagram destroys
    // and re-creates its flow core when the `model` reference changes, which re-fires `diagramInit` and so
    // `zoomToFit.onInit` — so a layer toggle reframes to the new content for free. Mutating the adapter
    // through `NgDiagramModelService` instead would silently stop that happening.
    this.model = initializeModel(
      {
        nodes: this.lockUnlessEditable(placed.nodes),
        edges: placed.edges,
        ...(viewport ? { metadata: { viewport } } : {}),
      },
      this.injector,
    );
  }

  /**
   * The arrangement as it now stands, in the shape the layout resource persists — or `undefined` when there
   * is no graph to arrange.
   *
   * Read from the *model* rather than from the inputs, because the model is where a drag and a resize land,
   * and while the modeler is open the model is the authority on where things sit. The viewport comes from the
   * model's metadata rather than being tracked here, so a pan or zoom the user made without touching a node
   * is still saved.
   *
   * `workflowId` is a parameter rather than an input: it is the identity of what is being saved, which the
   * host knows and this component has no other use for.
   */
  toLayout(workflowId: string): WorkflowDiagram | undefined {
    if (!this.graph) return undefined;
    const viewport = this.model.getMetadata().viewport;
    return toDiagram(
      workflowId,
      this.model.getNodes() as WorkflowNode[],
      this.model.getEdges() as WorkflowEdge[],
      viewport ? new DiagramViewport({ x: viewport.x, y: viewport.y, scale: viewport.scale }) : undefined,
      this.savedLayout,
    );
  }

  /**
   * Forwards a selection as the `data` behind it, so the properties panels have a subject.
   *
   * Only single selections reach the panels: a box selection of three tasks has no one subject to show, and
   * clearing is the honest answer. A lane is reported as one, because a lane's `data` is a role's `data` and
   * nothing in it says which of the two was clicked.
   */
  protected onSelectionChanged({ selectedNodes, selectedEdges }: SelectionChangedEvent): void {
    if (selectedNodes.length === 1 && selectedEdges.length === 0) {
      const node = selectedNodes[0] as WorkflowNode;
      this.selection.selectElement(node.data as WorkflowNodeData, isLaneNode(node));
    } else if (selectedEdges.length === 1 && selectedNodes.length === 0) {
      this.selection.selectRelation((selectedEdges[0].data ?? {}) as WorkflowEdgeData);
    } else this.selection.clear();
  }

  /**
   * The routing the edge behind the open menu is drawn with, which is the item the menu ticks. An edge the
   * model no longer holds reads as the default; the gesture that removed it closes the menu anyway.
   */
  protected activeRouting(edgeId: string): EdgeRoutingChoice {
    return activeEdgeRouting(this.model.getEdges().find((edge) => edge.id === edgeId)?.routing);
  }

  /**
   * Draws one relation with the chosen routing. Written into the model, so it shows at once and is picked up
   * by the next {@link toLayout} — which persists it as the edge's `routing`. Waypoints are kept: the new
   * algorithm draws its path through them, as the previous one did.
   */
  protected applyRouting(edgeId: string, routing: EdgeRoutingChoice): void {
    this.model.updateEdges((edges) => edges.map((edge) => (edge.id === edgeId ? { ...edge, routing } : edge)));
    this.contextMenu.close();
  }

  /**
   * Where the canvas should be looking after the rebuild, or `undefined` to let the automatic fit decide.
   *
   * **The live viewport wins over the saved one.** A layer toggle rebuilds the model, and taking the saved
   * viewport there would snap the canvas back to wherever it was last *saved* — throwing away the pan the
   * user has made since, which is a worse answer than the fit this replaced.
   *
   * Read off the model only once it holds a graph. The empty canvas every instance starts with reports a
   * default viewport of its own, and honouring that would suppress the automatic fit on the first real
   * build — leaving a large diagram unframed at 100%.
   */
  private viewportToKeep(): { x: number; y: number; scale: number } | undefined {
    const live = this.model.getNodes().length > 0 ? this.model.getMetadata().viewport : undefined;
    const source = live ?? this.savedLayout?.viewport;
    return source ? { x: source.x, y: source.y, scale: source.scale } : undefined;
  }

  /**
   * Takes the drag, resize and rotate handles off every node unless {@link editable} is set.
   *
   * Every node, not just the ones with handles worth hiding: ng-diagram expands a drag to a group's children
   * and *then* filters by `draggable`, so a child left unset defaults to draggable and would move with its
   * lane. Mapping the whole array is what makes that impossible.
   *
   * Rotation stays off even when editable. A rotated task card conveys nothing, and the layout's arithmetic —
   * columns pitched by a node's width, bands measured by its height — is stated in unrotated boxes.
   */
  private lockUnlessEditable(nodes: WorkflowNode[]): WorkflowNode[] {
    // A boundary event stays locked either way: it is pinned to its task's edge, and re-pinned on every
    // build, so a position given to it by hand would not survive the next one.
    if (this.editable) return nodes.map((node) => ({ ...node, rotatable: false, ...(isBoundaryNode(node) ? { draggable: false, resizable: false } : {}) }));
    return nodes.map((node) => ({ ...node, draggable: false, resizable: false, rotatable: false }));
  }
}

// region private helper functions
/**
 * Whether this change is a saved arrangement coming back *after* it was saved, rather than one arriving to be
 * applied — which is the one case the canvas should ignore. See {@link WorkflowDiagramComponent.ngOnChanges}.
 *
 * "Only `savedLayout` changed, and there was already one" is the whole test: an arrangement that arrives late
 * changes it from `undefined`, and everything else about the diagram — the graph, the layout service, whether
 * it is editable — is a reason to rebuild whatever `savedLayout` did.
 */
function isPostSaveRefresh(changes: SimpleChanges): boolean {
  return Object.keys(changes).length === 1 && changes['savedLayout'] !== undefined && !changes['savedLayout'].firstChange && !!changes['savedLayout'].previousValue;
}

/**
 * The canvas's config, with the automatic fit on or off and edge ends relinkable or not. Built by a function
 * rather than spread from a constant so that nothing can mutate a shared nested object — `NgDiagramConfig`
 * is a deep partial, and a shallow spread would share `zoom` between every instance.
 *
 * `defaultRouting` restates ng-diagram's own default, because the routing menu has to tick the routing an
 * edge is actually drawn with and most edges name none of their own.
 */
function diagramConfig(fitOnInit: boolean, relinkable: boolean): NgDiagramConfig {
  return {
    linking: { validateConnection: SAME_NODE_RELINK_ONLY, defaultRelinkable: relinkable },
    edgeRouting: { defaultRouting: DEFAULT_EDGE_ROUTING },
    grouping: { canGroup: REFUSE_GROUPING },
    zoom: { zoomToFit: { onInit: fitOnInit, padding: 40 } },
    zIndex: { elevateOnSelection: false },
    shortcuts: [],
  };
}
// endregion
