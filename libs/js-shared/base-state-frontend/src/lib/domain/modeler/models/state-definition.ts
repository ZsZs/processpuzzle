export interface StateDefinition {
  key: string;
  name: string;
  description?: string;
  locked: boolean;
  terminal: boolean;
  metadata?: Record<string, any>;
}
