import { Injectable } from '@angular/core';
import { GraphNode } from '../../../domain/modeler/graph/graph-model/graph-node';
import { GraphEdge } from '../../../domain/modeler/graph/graph-model/graph-edge';

@Injectable({ providedIn: 'root' })
export class DiagramSelectionService {
  selectedNode?: GraphNode;
  selectedEdge?: GraphEdge;

  selectNode(node: GraphNode) {
    this.selectedNode = node;
    this.selectedEdge = undefined;
  }

  selectEdge(edge: GraphEdge) {
    this.selectedEdge = edge;
    this.selectedNode = undefined;
  }

  clear() {
    this.selectedNode = undefined;
    this.selectedEdge = undefined;
  }
}
