export interface GraphEdge {
  id: string;
  type: 'transition';
  source: string;
  target: string;

  label?: string; // triggerKey or name
  data: any; // holds TransitionDefinition
}
