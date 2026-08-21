import { Injectable } from '@angular/core';
import ELK from 'elkjs/lib/elk.bundled.js';
import { GraphLayout } from '../graph-model/graph-layout';

@Injectable({ providedIn: 'root' })
export class ElkLayoutService {
  private elk = new ELK();

  async layout(graph: GraphLayout): Promise<GraphLayout> {
    const elkGraph = {
      id: 'root',
      layoutOptions: {
        'elk.algorithm': 'layered',
        'elk.direction': 'RIGHT',
        'elk.layered.spacing.nodeNodeBetweenLayers': '50',
        'elk.spacing.nodeNode': '30',
      },
      children: graph.nodes.map((n) => ({
        id: n.id,
        width: 150,
        height: 60,
      })),
      edges: graph.edges.map((e) => ({
        id: e.id,
        sources: [e.source],
        targets: [e.target],
      })),
    };

    const result = await this.elk.layout(elkGraph);

    const nodes = graph.nodes.map((n) => {
      const layoutNode = result.children?.find((c) => c.id === n.id);
      return {
        ...n,
        position: {
          x: layoutNode?.x ?? 0,
          y: layoutNode?.y ?? 0,
        },
      };
    });

    return { nodes, edges: graph.edges };
  }
}
