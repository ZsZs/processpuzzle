import { Component, inject, OnInit } from '@angular/core';
import { ElkLayoutService } from '../../../domain/modeler/graph/layout/elk-layout.service';
import { StateMachineToGraphConverter } from '../../../domain/modeler/graph/converters/state-machine-to-graph.converter';
import { GraphNode } from '../../../domain/modeler/graph/graph-model/graph-node';
import { GraphEdge } from '../../../domain/modeler/graph/graph-model/graph-edge';
import { StateMachineCanvasComponent } from '../components/state-machine-canvas.component';
import { StateMachineToolbarComponent } from '../components/state-machine-toolbar.component';
import { StatePropertiesPanelComponent } from '../pages/state-properties-panel.component';
import { TransitionPropertiesPanelComponent } from '../pages/transition-properties-panel.component';
import { DiagramSelectionService } from '../services/diagram-selection.service';

@Component({
  selector: 'pp-state-machine-editor-page',
  standalone: true,
  imports: [StateMachineCanvasComponent, StateMachineToolbarComponent, StatePropertiesPanelComponent, TransitionPropertiesPanelComponent],
  template: `
    <pp-state-machine-toolbar (addState)="onAddState()" (addTransition)="onAddTransition()"> </pp-state-machine-toolbar>

    <div class="editor-layout">
      <div class="canvas-pane">
        <pp-state-machine-canvas [nodes]="nodes" [edges]="edges"> </pp-state-machine-canvas>
      </div>

      <div class="properties-pane">
        <pp-state-properties-panel *ngIf="selection.selectedNode" [state]="selection.selectedNode.data"> </pp-state-properties-panel>

        <pp-transition-properties-panel *ngIf="selection.selectedEdge" [transition]="selection.selectedEdge.data"> </pp-transition-properties-panel>
      </div>
    </div>
  `,
  styles: [
    `
      .editor-layout {
        display: grid;
        grid-template-columns: 2fr 1fr;
        height: 100%;
      }
      .canvas-pane {
        border-right: 1px solid #ccc;
      }
      .properties-pane {
        padding: 8px;
      }
    `,
  ],
})
export class StateMachineEditorPage implements OnInit {
  nodes: GraphNode[] = [];
  edges: GraphEdge[] = [];
  private readonly store = inject(StateMachineDefinitionStore);

  constructor(
    private elk: ElkLayoutService,
    public selection: DiagramSelectionService,
  ) {}

  async ngOnInit() {
    // Load from existing store
    const sm = await this.store.getStateMachineByEntityName('order');

    const graph = StateMachineToGraphConverter.convert(sm);
    const layouted = await this.elk.layout(graph);

    this.nodes = layouted.nodes;
    this.edges = layouted.edges;
  }

  onAddState() {
    // TODO: use EditorCommandService + UndoRedoService
  }

  onAddTransition() {
    // TODO: use EditorCommandService + UndoRedoService
  }
}
