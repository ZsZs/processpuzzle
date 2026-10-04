export type ArtifactType = 'ENTITY' | 'STATE' | 'WORKFLOW' | 'APP';

export interface ClarificationOption {
  id: string;
  label: string;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  /** Clickable answers the AI offered (rendered as chips). */
  options?: ClarificationOption[];
}

export interface ValidationIssue {
  artifactType: ArtifactType;
  path: string;
  message: string;
  blocking: boolean;
}

/** One YAML document per artifact type. */
export type DraftYaml = Partial<Record<ArtifactType, string>>;

/** Events streamed by the backend (SSE `data:` payload, JSON). */
export type AssistantEvent =
  | { type: 'token'; text: string }
  | { type: 'clarification'; questions: string[]; options: ClarificationOption[] }
  | { type: 'draft_update'; artifactType: ArtifactType; yaml: string }
  | { type: 'issues'; issues: ValidationIssue[] }
  | { type: 'explanation'; text: string }
  | { type: 'done' }
  | { type: 'error'; message: string };

export interface ApplyResult {
  success: boolean;
  issues: ValidationIssue[];
}
