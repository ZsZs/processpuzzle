import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GraphNode } from '../../../domain/modeler/graph/graph-model/graph-node';
import { GraphEdge } from '../../../domain/modeler/graph/graph-model/graph-edge';
import { DiagramSelectionService } from '../services/diagram-selection.service';
import { initializeModel, NgDiagramComponent, provideNgDiagram } from 'ng-diagram';

@Component({
  selector: 'pp-state-machine-canvas',
  standalone: true,
  imports: [CommonModule, NgDiagramComponent],
  providers: [provideNgDiagram()],
  template: `
    <ng-diagram [model]="model" />
    <!--
    <div class="canvas">
      <div *ngFor="let node of nodes" [id]="node.id" class="canvas-node" [style.left.px]="node.position?.x" [style.top.px]="node.position?.y" (click)="onNodeClick(node)">
        <pp-state-node [node]="node"></pp-state-node>
      </div>

      <div *ngFor="let edge of edges" [id]="edge.id" class="canvas-edge" (click)="onEdgeClick(edge)">
        <pp-transition-edge [edge]="edge"></pp-transition-edge>
      </div>
    </div>
    -->
  `,
  styles: `
    :host {
      display: flex;
      height: 300px;
    }
  `,
})
export class StateMachineCanvasComponent {
  @Input() nodes: GraphNode[] = [];
  @Input() edges: GraphEdge[] = [];
  model = initializeModel({
    nodes: [
      { id: '1', position: { x: 100, y: 150 }, data: { label: 'Node 1' } },
      { id: '2', position: { x: 400, y: 150 }, data: { label: 'Node 2' } },
    ],
    edges: [
      {
        id: '1',
        source: '1',
        sourcePort: 'port-right',
        targetPort: 'port-left',
        target: '2',
        data: {},
      },
    ],
  });

  constructor(private selection: DiagramSelectionService) {}

  onNodeClick(node: GraphNode) {
    this.selection.selectNode(node);
  }

  onEdgeClick(edge: GraphEdge) {
    this.selection.selectEdge(edge);
  }
}
