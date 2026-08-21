import { StateDefinition } from './state-definition';
import { TransitionDefinition } from './transition-definition';

export interface StateMachine {
  entityName: string;
  name: string;

  stateAttributeKey: string;
  initialStateKey: string;

  states: StateDefinition[];
  transitions: TransitionDefinition[];

  description?: string;
  orgKey?: string;

  createdAt?: string;
  updatedAt?: string;
  version?: number;

  metadata?: Record<string, any>;
}
