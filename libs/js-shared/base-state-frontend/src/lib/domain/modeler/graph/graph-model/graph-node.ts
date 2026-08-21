export interface GraphNode {
  id: string;
  type: 'state';
  label: string;

  data: any; // holds StateDefinition
  position?: { x: number; y: number };
}
