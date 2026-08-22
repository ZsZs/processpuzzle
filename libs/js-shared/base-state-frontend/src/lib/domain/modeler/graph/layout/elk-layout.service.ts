import ELK from 'elkjs/lib/elk.bundled.js';
import { Injectable } from '@angular/core';
import { StateNode, TransitionEdge } from '../state-machine-graph';

/**
 * The same job as {@link DagreLayoutService}, through ELK's layered algorithm, which routes a dense graph
 * more legibly. Not what the modeler uses today: it is asynchronous, which would make every caller of the
 * conversion chain async, and Dagre's layout is good enough for a flat state machine.
 *
 * Kept, rather than deleted, because it is the engine to switch to once machines get large enough for edge
 * crossings to matter — at which point this is a one-line change in the canvas, not a rewrite.
 */
@Injectable({ providedIn: 'root' })
export class ElkLayoutService {
  private elk = new ELK();

  /** Lays out the whole graph. Unlike Dagre's `place`, this does not preserve existing positions. */
  async layout(nodes: StateNode[], edges: TransitionEdge[]): Promise<StateNode[]> {
    const nodeIds = new Set(nodes.map((node) => node.id));
    const result = await this.elk.layout({
      id: 'root',
      layoutOptions: {
        'elk.algorithm': 'layered',
        'elk.direction': 'RIGHT',
        'elk.layered.spacing.nodeNodeBetweenLayers': '50',
        'elk.spacing.nodeNode': '30',
      },
      children: nodes.map((node) => ({ id: node.id, width: node.size?.width ?? 150, height: node.size?.height ?? 60 })),
      // Both ends have to be nodes of this graph, or ELK rejects the edge — a transition may name a state
      // the machine no longer declares.
      edges: edges.filter((edge) => nodeIds.has(edge.source) && nodeIds.has(edge.target)).map((edge) => ({ id: edge.id, sources: [edge.source], targets: [edge.target] })),
    });

    return nodes.map((node) => {
      const placed = result.children?.find((child) => child.id === node.id);
      // ELK reports the top-left corner, which is what ng-diagram positions by — no centre correction here.
      return { ...node, position: { x: placed?.x ?? 0, y: placed?.y ?? 0 } };
    });
  }
}
