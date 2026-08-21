import { Component, inject, Injector, Input, OnChanges } from '@angular/core';
import { initializeModel, NgDiagramComponent, NgDiagramNodeTemplateMap, provideNgDiagram, SelectionChangedEvent } from 'ng-diagram';
import { StateMachineGraphConverter } from '../../../domain/modeler/graph/converters/state-machine-graph.converter';
import { DagreLayoutService } from '../../../domain/modeler/graph/layout/dagre-layout.service';
import { STATE_NODE_TYPE, StateNode, StateNodeData, TransitionEdge, TransitionEdgeData } from '../../../domain/modeler/graph/state-machine-graph';
import { DiagramDefinition, DiagramViewport } from '../../../domain/modeler/models/diagram-definition';
import { StateMachineDefinition } from '../../../domain/state-machine-definition';
import { DiagramSelectionService } from '../services/diagram-selection.service';
import { StateNodeComponent } from './state-node.component';

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
 * {@link toLayout} is the other direction — what the user has arranged, in the shape
 * `DiagramDefinitionStore.saveLayout` persists. It reads the *model*, not the inputs, because the model is
 * where a drag lands.
 */
@Component({
  selector: 'pp-state-machine-canvas',
  standalone: true,
  imports: [NgDiagramComponent],
  providers: [provideNgDiagram()],
  template: ` <ng-diagram [model]="model" [nodeTemplateMap]="nodeTemplateMap" (selectionChanged)="onSelectionChanged($event)" /> `,
  styles: `
    :host {
      display: flex;
      height: 100%;
      min-height: 300px;
    }
  `,
})
export class StateMachineCanvasComponent implements OnChanges {
  @Input() machine?: StateMachineDefinition;
  @Input() layout?: DiagramDefinition;

  readonly nodeTemplateMap = new NgDiagramNodeTemplateMap([[STATE_NODE_TYPE, StateNodeComponent]]);

  private readonly injector = inject(Injector);
  private readonly dagreLayout = inject(DagreLayoutService);
  private readonly selection = inject(DiagramSelectionService);

  /** Empty until a machine arrives — an unloaded canvas shows nothing, not a placeholder graph. */
  model = initializeModel({ nodes: [], edges: [] }, this.injector);

  ngOnChanges(): void {
    if (!this.machine) {
      this.model = initializeModel({ nodes: [], edges: [] }, this.injector);
      return;
    }
    const graph = StateMachineGraphConverter.toGraph(this.machine, this.layout);
    this.model = initializeModel(
      {
        nodes: this.dagreLayout.place(graph.nodes, graph.edges, graph.unplacedStateKeys),
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
   * Forwards a selection as the domain object behind it. Only single selections reach the panels: a box
   * selection of three states has no one subject to show, and clearing is the honest answer.
   */
  protected onSelectionChanged({ selectedNodes, selectedEdges }: SelectionChangedEvent): void {
    if (selectedNodes.length === 1 && selectedEdges.length === 0) {
      this.selection.selectState((selectedNodes[0].data as StateNodeData).state);
    } else if (selectedEdges.length === 1 && selectedNodes.length === 0) {
      this.selection.selectTransition((selectedEdges[0].data as TransitionEdgeData).transition);
    } else this.selection.clear();
  }
}
