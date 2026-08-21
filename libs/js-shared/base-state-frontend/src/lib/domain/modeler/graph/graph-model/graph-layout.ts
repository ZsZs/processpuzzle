import { GraphNode } from './graph-node';
import { GraphEdge } from './graph-edge';

export interface GraphLayout {
  nodes: GraphNode[];
  edges: GraphEdge[];
}
