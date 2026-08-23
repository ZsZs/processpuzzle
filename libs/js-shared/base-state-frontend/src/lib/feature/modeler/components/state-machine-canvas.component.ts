import { Component, effect, ElementRef, inject, Injector, Input, OnChanges, signal } from '@angular/core';
import {
  initializeModel,
  NgDiagramComponent,
  NgDiagramConfig,
  NgDiagramEdgeTemplateMap,
  NgDiagramNodeTemplateMap,
  PaletteItemDroppedEvent,
  provideNgDiagram,
  SelectionChangedEvent,
} from 'ng-diagram';
import { StateMachineGraphConverter } from '../../../domain/modeler/graph/converters/state-machine-graph.converter';
import { DagreLayoutService } from '../../../domain/modeler/graph/layout/dagre-layout.service';
import { STATE_NODE_TYPE, StateNode, StateNodeData, TRANSITION_EDGE_TYPE, TransitionEdge, TransitionEdgeData } from '../../../domain/modeler/graph/state-machine-graph';
import { DiagramDefinition, DiagramViewport } from '../../../domain/modeler/models/diagram-definition';
import { StateMachineDefinition } from '../../../domain/state-machine-definition';
import { DiagramSelectionService } from '../services/diagram-selection.service';
import { EdgeContextMenuService } from '../services/edge-context-menu.service';
import { StateEdit } from '../pages/state-properties-panel.component';
import { EdgeRoutingMenuComponent } from './edge-routing-menu.component';
import { activeEdgeRouting, DEFAULT_EDGE_ROUTING, EdgeRoutingChoice } from './edge-routing-options';
import { ElementPaletteComponent } from './element-palette.component';
import { newStateData, nextStateKey, PaletteStateNodeData } from './state-palette-items';
import { StateNodeComponent } from './state-node.component';
import { TransitionEdgeComponent } from './transition-edge.component';

/**
 * Draws one state machine, joining its topology to its saved arrangement.
 *
 * The two inputs are the two resources, kept apart exactly as the backend keeps them:
 * `machine` is what the states and transitions are, `layout` is where they sit. `layout` is optional
 * because a machine that has never been arranged is the normal starting point —
 * `GET /diagrams/{entityName}` answers 404 — and in that case {@link DagreLayoutService} places the graph.
 *
 * The model is rebuilt through `initializeModel(..., injector)` whenever either input changes, which is
 * ng-diagram's prescribed path for data arriving after construction; nothing here mutates nodes in place,
 * since the library's reactivity runs through the model adapter.
 *
 * {@link toLayout} and {@link toMachine} are the other direction — what the user has drawn, in the two
 * shapes the tab persists. Both read the *model*, not the inputs, because the model is where a drag, a
 * drop and a properties-panel edit land, and while the modeler is open the model is the authority on what
 * the machine contains.
 */
@Component({
  selector: 'pp-state-machine-canvas',
  standalone: true,
  imports: [NgDiagramComponent, ElementPaletteComponent, EdgeRoutingMenuComponent],
  providers: [provideNgDiagram(), EdgeContextMenuService],
  template: `
    @if (!readOnly) {
      <pp-element-palette />
    }
    <ng-diagram
      [model]="model"
      [config]="config"
      [nodeTemplateMap]="nodeTemplateMap"
      [edgeTemplateMap]="edgeTemplateMap"
      (selectionChanged)="onSelectionChanged($event)"
      (paletteItemDropped)="onPaletteItemDropped($event)"
    />

    @if (!readOnly && contextMenu.target(); as target) {
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
    /* The diagram takes whatever the palette rail leaves. The zero min-width is there because ng-diagram
       measures itself against this box, and a flex item's automatic minimum would otherwise let its
       content widen it. */
    ng-diagram {
      flex: 1;
      min-width: 0;
    }
  `,
})
export class StateMachineCanvasComponent implements OnChanges {
  @Input() machine?: StateMachineDefinition;
  @Input() layout?: DiagramDefinition;
  /**
   * Key of the state to mark as the one an object currently sits in. Set by a caller drawing the machine
   * of a particular row — the State Machine tab of a governed entity — and left unset by the modeler,
   * which draws the definition itself.
   */
  @Input() currentStateKey?: string;
  /**
   * Draws the machine without offering to change it: no palette, no edge context menu, and no node that
   * can be dragged or resized.
   *
   * A mode of this component rather than a viewer component of its own, because everything that makes the
   * diagram *appear* — joining the topology to the arrangement, falling back to an automatic layout,
   * resolving the three UML shapes — is identical either way, and a second component would be that logic
   * copied with nothing but the gestures removed.
   *
   * It disables gestures, not persistence: nothing here can save, and the only writer is the modeler tab's
   * own Save button. A read-only host simply does not render one.
   */
  @Input() readOnly = false;

  readonly nodeTemplateMap = new NgDiagramNodeTemplateMap([[STATE_NODE_TYPE, StateNodeComponent]]);
  readonly edgeTemplateMap = new NgDiagramEdgeTemplateMap([[TRANSITION_EDGE_TYPE, TransitionEdgeComponent]]);

  /**
   * Two deliberate departures from ng-diagram's defaults.
   *
   * `computeNodeId` is what a dropped node's id is minted by, so overriding it is what makes that id the
   * *state key* — the identity a layout row is keyed on and a transition names. Without it the palette
   * would create nodes under generated ids that would have to be reconciled with a key afterwards.
   *
   * `validateConnection` refuses new edges, because an edge the user draws carries no `Transition` behind
   * it: no trigger, no guards, nothing `toMachine` could save. Drawing transitions is the next gesture the
   * modeler needs and refusing it outright is more honest than letting one be drawn and then discarded.
   *
   * `defaultRouting` restates ng-diagram's own default rather than leaving it implicit, because the routing
   * menu has to tick the routing an edge is actually drawn with and most edges name none of their own.
   */
  readonly config: NgDiagramConfig = {
    computeNodeId: () => nextStateKey(this.model.getNodes().map((node) => node.id)),
    linking: { validateConnection: () => false },
    edgeRouting: { defaultRouting: DEFAULT_EDGE_ROUTING },
  };

  protected readonly contextMenu = inject(EdgeContextMenuService);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly dagreLayout = inject(DagreLayoutService);
  private readonly selection = inject(DiagramSelectionService);

  /** Empty until a machine arrives — an unloaded canvas shows nothing, not a placeholder graph. */
  model = initializeModel({ nodes: [], edges: [] }, this.injector);

  /** Where the open routing menu sits, as an offset inside this component's box. */
  private readonly menuPositionSignal = signal({ x: 0, y: 0 });
  protected readonly menuPosition = this.menuPositionSignal.asReadonly();

  constructor() {
    // The right-click that opened the menu reports viewport coordinates, and the menu is positioned inside
    // this box - so the offset is computed here, the one place that knows where the box is. Once per open,
    // in an effect, rather than from the template: a getBoundingClientRect() on every change detection is a
    // layout measurement the menu has no need of repeating.
    effect(() => {
      const target = this.contextMenu.target();
      if (!target) return;
      const bounds = this.host.nativeElement.getBoundingClientRect();
      this.menuPositionSignal.set({ x: target.clientX - bounds.left, y: target.clientY - bounds.top });
    });
  }

  ngOnChanges(): void {
    // Whatever changed, the graph is about to be rebuilt — so the edge an open routing menu was opened on
    // may not survive it. Closed before anything else, since both branches below replace the model.
    this.contextMenu.close();
    if (!this.machine) {
      this.model = initializeModel({ nodes: [], edges: [] }, this.injector);
      return;
    }
    const graph = StateMachineGraphConverter.toGraph(this.machine, this.layout, this.currentStateKey);
    this.model = initializeModel(
      {
        nodes: this.lockWhenReadOnly(this.dagreLayout.place(graph.nodes, graph.edges, graph.unplacedStateKeys)),
        edges: graph.edges,
        metadata: graph.metadata,
      },
      this.injector,
    );
    // A reload replaces the graph, so whatever was selected in the previous one no longer exists.
    this.selection.clear();
  }

  /**
   * The arrangement as it now stands, or `undefined` when there is no machine to arrange.
   *
   * The viewport is read from the model's metadata rather than tracked here, so a pan or zoom the user made
   * without touching a node is still saved.
   */
  toLayout(): DiagramDefinition | undefined {
    if (!this.machine) return undefined;
    const viewport = this.model.getMetadata().viewport;
    return StateMachineGraphConverter.toLayout(
      this.machine.entityName,
      this.model.getNodes() as StateNode[],
      this.model.getEdges() as TransitionEdge[],
      viewport ? new DiagramViewport({ x: viewport.x, y: viewport.y, scale: viewport.scale }) : undefined,
      this.layout,
    );
  }

  /**
   * The topology as it now stands — the machine that was loaded, with the states and transitions the
   * canvas currently draws. `undefined` when there is no machine, for the same reason {@link toLayout} is.
   */
  toMachine(): StateMachineDefinition | undefined {
    if (!this.machine) return undefined;
    return StateMachineGraphConverter.toMachine(this.machine, this.model.getNodes() as StateNode[], this.model.getEdges() as TransitionEdge[]);
  }

  /**
   * Applies an edit made in the properties panel.
   *
   * Written into the model rather than into the machine input, so the edit shows on the node at once and
   * is picked up by the next {@link toMachine} — and so nothing changes the inputs, which would rebuild
   * the whole model and undo it.
   *
   * A key change is a re-identification: the node id *is* the key. Only a state the machine has not seen
   * yet can be re-keyed — see `StatePropertiesPanelComponent` — so no edge and no layout row can be
   * pointing at the old one.
   */
  applyStateEdit({ previousKey, state, initial }: StateEdit): void {
    this.replaceStateNode(previousKey, { state, label: state.name || state.key, initial });
    this.selection.selectState(state, initial);
  }

  /**
   * Turns a dropped palette symbol into a real state.
   *
   * ng-diagram has already added the node by the time this runs, built by spreading the palette item's
   * `data` — which means every drop of the same symbol shares one placeholder `State`. So the data is
   * replaced wholesale with a state of this node's own, keyed by the id `computeNodeId` just minted.
   */
  protected onPaletteItemDropped({ node }: PaletteItemDroppedEvent): void {
    this.replaceStateNode(node.id, newStateData((node.data as PaletteStateNodeData).kind, node.id));
  }

  /**
   * Forwards a selection as the domain object behind it. Only single selections reach the panels: a box
   * selection of three states has no one subject to show, and clearing is the honest answer.
   */
  protected onSelectionChanged({ selectedNodes, selectedEdges }: SelectionChangedEvent): void {
    if (selectedNodes.length === 1 && selectedEdges.length === 0) {
      const data = selectedNodes[0].data as StateNodeData;
      this.selection.selectState(data.state, data.initial);
    } else if (selectedEdges.length === 1 && selectedNodes.length === 0) {
      this.selection.selectTransition((selectedEdges[0].data as TransitionEdgeData).transition);
    } else this.selection.clear();
  }

  /**
   * The routing the edge behind the open menu is drawn with, which is the item the menu ticks. An edge the
   * model no longer holds - deleted while its menu was open - reads as the default; the same gesture is
   * about to close the menu either way.
   */
  protected activeRouting(edgeId: string): EdgeRoutingChoice {
    return activeEdgeRouting(this.model.getEdges().find((edge) => edge.id === edgeId)?.routing);
  }

  /**
   * Draws one transition with the chosen routing.
   *
   * Written into the model, so it shows at once and is picked up by the next {@link toLayout} - which is
   * what persists it, as `EdgeLayout.routing`. Nothing else is touched: an edge carrying waypoints keeps
   * them and the new algorithm draws its path through them, exactly as the previous one did.
   */
  protected applyRouting(edgeId: string, routing: EdgeRoutingChoice): void {
    this.model.updateEdges((edges) => edges.map((edge) => (edge.id === edgeId ? { ...edge, routing } : edge)));
    this.contextMenu.close();
  }

  /**
   * Takes the drag and resize handles off every node in read-only mode.
   *
   * On the nodes rather than through a middleware, because ng-diagram 1.3 has no `readOnly` config — its
   * own recipe is a middleware that cancels model actions, which suppresses the *effect* of a gesture the
   * user was still invited to make. `draggable: false` is what stops the invitation. Selection is left
   * alone on purpose: clicking a state to read it is not a change, and the panels beside a read-only
   * canvas are worth keeping usable.
   */
  private lockWhenReadOnly(nodes: StateNode[]): StateNode[] {
    if (!this.readOnly) return nodes;
    return nodes.map((node) => ({ ...node, draggable: false, resizable: false, rotatable: false }));
  }

  /**
   * Re-keys and re-data-s one node, and — when the new data claims to be the initial state — takes that
   * claim off every other node, since a machine starts in exactly one state.
   *
   * Goes through the model adapter's own `updateNodes` rather than `NgDiagramModelService`: the adapter
   * notifies the renderer through the same signal either way, and this keeps the canvas working before
   * ng-diagram has measured itself, which is the state a unit test leaves it in.
   */
  private replaceStateNode(nodeId: string, data: StateNodeData): void {
    this.model.updateNodes((nodes) =>
      (nodes as StateNode[]).map((node) => {
        if (node.id === nodeId) return { ...node, id: data.state.key, data };
        return data.initial && node.data.initial ? { ...node, data: { ...node.data, initial: false } } : node;
      }),
    );
  }
}
