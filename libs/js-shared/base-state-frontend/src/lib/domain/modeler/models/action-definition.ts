export interface ActionDefinition {
  key: string;
  name: string;

  type?: string; // optional, backend may add later
  parameters?: Record<string, any>;
}
