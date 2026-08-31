import { Component, inject, Injector, Input, OnChanges } from '@angular/core';
import { initializeModel, NgDiagramComponent, NgDiagramConfig, NgDiagramNodeTemplateMap, provideNgDiagram } from 'ng-diagram';
import { WorkflowLayoutService } from '../../../domain/modeler/graph/workflow-layout.service';
import { WORKFLOW_NODE_TYPE, WorkflowGraph, WorkflowNode } from '../../../domain/modeler/workflow-graph';
import { WorkflowElementNodeComponent } from './workflow-element-node.component';

/**
 * Draws one modeler perspective, read-only.
 *
 * Its only input is a converted {@link WorkflowGraph}, which is what makes it perspective-agnostic: the
 * Roles diagram, and the Tasks and Workflows diagrams to come, differ in their converter and share this
 * canvas. Everything that makes a diagram *appear* — placing the nodes, resolving the symbols, taking the
 * editing gestures away — is identical for all three.
 *
 * The model is rebuilt through `initializeModel(..., injector)` whenever the input changes, which is
 * ng-diagram's prescribed path for data arriving after construction; nothing here mutates nodes in place,
 * since the library's reactivity runs through the model adapter.
 *
 * Read-only in three places, and all three are needed. Nodes are marked undraggable, unresizable and
 * unrotatable, because ng-diagram 1.3 has no `readOnly` config and `draggable: false` is what stops the
 * invitation rather than merely undoing its effect. `validateConnection` refuses new edges, since an edge
 * drawn here would stand for a relation nothing would persist. Selection is deliberately left alone:
 * clicking an element to read it is not a change.
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

@Component({
  selector: 'pp-workflow-diagram',
  standalone: true,
  imports: [NgDiagramComponent],
  providers: [provideNgDiagram()],
  template: ` <ng-diagram [model]="model" [config]="config" [nodeTemplateMap]="nodeTemplateMap" /> `,
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

  readonly nodeTemplateMap = new NgDiagramNodeTemplateMap([[WORKFLOW_NODE_TYPE, WorkflowElementNodeComponent]]);

  /**
   * Refuses edges the user draws, and frames the whole diagram on init.
   *
   * The default edge template is kept: base-state wrote one of its own only so an edge could report a
   * right-click, and a read-only diagram has no menu to open.
   */
  readonly config: NgDiagramConfig = {
    linking: { validateConnection: REFUSE_CONNECTION },
    zoom: { zoomToFit: { onInit: true, padding: 40 } },
  };

  private readonly injector = inject(Injector);
  private readonly layout = inject(WorkflowLayoutService);

  /** Empty until a graph arrives — an unloaded canvas shows nothing, not a placeholder diagram. */
  model = initializeModel({ nodes: [], edges: [] }, this.injector);

  ngOnChanges(): void {
    if (!this.graph) {
      this.model = initializeModel({ nodes: [], edges: [] }, this.injector);
      return;
    }
    this.model = initializeModel({ nodes: this.lock(this.layout.place(this.graph.nodes, this.graph.edges)), edges: this.graph.edges }, this.injector);
  }

  /** Takes the drag, resize and rotate handles off every node. */
  private lock(nodes: WorkflowNode[]): WorkflowNode[] {
    return nodes.map((node) => ({ ...node, draggable: false, resizable: false, rotatable: false }));
  }
}
