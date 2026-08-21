import { Injectable } from '@angular/core';
import { GraphLayout } from '../graph-model/graph-layout';
import dagre from '@dagrejs/dagre';

@Injectable({ providedIn: 'root' })
export class DagreLayoutService {
  layout(graph: GraphLayout): GraphLayout {
    const g = new dagre.graphlib.Graph();
    g.setGraph({ rankdir: 'LR', nodesep: 30, ranksep: 50 });
    g.setDefaultEdgeLabel(() => ({}));

    graph.nodes.forEach((node) => {
      g.setNode(node.id, { width: 150, height: 60 });
    });

    graph.edges.forEach((edge) => {
      g.setEdge(edge.source, edge.target);
    });

    dagre.layout(g);

    const nodes = graph.nodes.map((n) => {
      const pos = g.node(n.id);
      return {
        ...n,
        position: { x: pos.x, y: pos.y },
      };
    });

    return { nodes, edges: graph.edges };
  }
}
