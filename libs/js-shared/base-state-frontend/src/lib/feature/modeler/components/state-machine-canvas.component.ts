import { Component, inject, Injector, Input, OnChanges } from '@angular/core';
import { Edge, initializeModel, NgDiagramComponent, provideNgDiagram, SimpleNode } from 'ng-diagram';
import { GraphEdge } from '../../../domain/modeler/graph/graph-model/graph-edge';
import { GraphNode } from '../../../domain/modeler/graph/graph-model/graph-node';
import { DiagramSelectionService } from '../services/diagram-selection.service';

/**
 * Renders one state machine's graph with ng-diagram.
 *
 * The `nodes` / `edges` inputs are the source of truth, and the model is rebuilt from them whenever
 * they change — `initializeModel` re-run through the injector, which is what ng-diagram's own guide
 * prescribes for data that arrives after construction (a machine fetched from the backend, or the user
 * switching to another one). Nothing mutates `model.nodes` in place: ng-diagram's reactivity runs
 * through the model adapter.
 *
 * `label` is copied into `data` on the way in because that is where a node template reads it from,
 * while the `GraphNode.data` payload — the `State` or `Transition` the properties panels edit — is kept
 * alongside it rather than replaced.
 *
 * Edge ports are deliberately left unset, which makes the edges *floating*: they anchor to the node
 * borders that face each other rather than to a fixed side. A state machine's layout is computed
 * (`DagreLayoutService` / `ElkLayoutService`) rather than drawn by hand, so a hard-coded
 * right-to-left anchor would run edges backwards through their own nodes half the time. The anchors a
 * user chooses by dragging are a separate matter — they are what `EdgeLayout.sourcePort` /
 * `targetPort` persist, and applying them is the modeler's next step.
 */
@Component({
  selector: 'pp-state-machine-canvas',
  standalone: true,
  imports: [NgDiagramComponent],
  providers: [provideNgDiagram()],
  template: ` <ng-diagram [model]="model" /> `,
  styles: `
    :host {
      display: flex;
      height: 300px;
    }
  `,
})
export class StateMachineCanvasComponent implements OnChanges {
  @Input() nodes: GraphNode[] = [];
  @Input() edges: GraphEdge[] = [];

  private readonly injector = inject(Injector);
  private readonly selection = inject(DiagramSelectionService);

  /** Empty until the first input arrives — an unloaded canvas shows nothing, not a placeholder graph. */
  model = initializeModel({ nodes: [], edges: [] }, this.injector);

  ngOnChanges(): void {
    this.model = initializeModel({ nodes: this.nodes.map(toDiagramNode), edges: this.edges.map(toDiagramEdge) }, this.injector);
  }

  onNodeClick(node: GraphNode) {
    this.selection.selectNode(node);
  }

  onEdgeClick(edge: GraphEdge) {
    this.selection.selectEdge(edge);
  }
}

// region private helper functions
/**
 * A node the layout engine has not placed yet is drawn at the origin rather than dropped: `position` is
 * required by ng-diagram, and `StateToNodeConverter` deliberately leaves it unset for the layout engine
 * to fill in.
 */
function toDiagramNode(node: GraphNode): SimpleNode {
  return {
    id: node.id,
    position: node.position ?? { x: 0, y: 0 },
    data: { ...node.data, label: node.label },
  };
}

function toDiagramEdge(edge: GraphEdge): Edge {
  return {
    id: edge.id,
    source: edge.source,
    target: edge.target,
    data: { ...edge.data, label: edge.label },
  };
}
// endregion
