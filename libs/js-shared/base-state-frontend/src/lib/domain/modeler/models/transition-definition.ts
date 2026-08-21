export interface TransitionDefinition {
  key: string;
  name: string;

  sourceStateKey: string;
  targetStateKey: string;

  triggerKey: string;

  actions: ActionDefinition[];
  guards: GuardDefinition[];

  metadata?: Record<string, any>;
}
