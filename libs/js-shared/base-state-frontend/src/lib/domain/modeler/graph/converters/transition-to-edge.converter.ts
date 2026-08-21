import { TransitionDefinition } from '../../models/transition-definition';
import { GraphEdge } from '../graph-model/graph-edge';

export class TransitionToEdgeConverter {
  static convert(transition: TransitionDefinition): GraphEdge {
    return {
      id: transition.key,
      type: 'transition',
      source: transition.sourceStateKey,
      target: transition.targetStateKey,
      label: transition.triggerKey ?? transition.name,
      data: transition,
    };
  }
}
