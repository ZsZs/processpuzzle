import { StateDefinition } from '../../models/state-definition';
import { GraphNode } from '../graph-model/graph-node';

export class StateToNodeConverter {
  static convert(state: StateDefinition): GraphNode {
    return {
      id: state.key,
      type: 'state',
      label: state.name,
      data: state,
      position: undefined, // layout engine will fill this
    };
  }
}
