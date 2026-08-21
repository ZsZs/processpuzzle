import { StateMachine } from '../../models/state-machine';
import { GraphLayout } from '../graph-model/graph-layout';
import { StateToNodeConverter } from './state-to-node.converter';
import { TransitionToEdgeConverter } from './transition-to-edge.converter';

export class StateMachineToGraphConverter {
  static convert(sm: StateMachine): GraphLayout {
    const nodes = sm.states.map(StateToNodeConverter.convert);
    const edges = sm.transitions.map(TransitionToEdgeConverter.convert);

    return { nodes, edges };
  }
}
