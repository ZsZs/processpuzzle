export interface GuardDefinition {
  key: string;
  name: string;

  expression?: string; // optional DSL or script
  parameters?: Record<string, any>;
}
