import { InjectionToken, Signal } from '@angular/core';

/**
 * Declares on an attribute that the platform's AI can write its value from a description, which puts a button beside the
 * field. `from` names the attribute that holds the description; the button is enabled once it has text.
 */
export interface AiAssistConfig {
  /** The assist task, such as `rule.expression`. */
  task: string;
  /** The attribute holding the user's description. */
  from: string;
  /** The attributes whose current values the task needs besides the description, such as a rule's `context`. */
  parameters?: string[];
}

/** What the user asks a task for: the description, the task's other form values, and what the field holds now. */
export interface AiAssistRequest {
  text: string;
  parameters?: Record<string, string>;
  current?: string;
}

/** One thing still wrong with a candidate. */
export interface AiAssistIssue {
  severity: 'ERROR' | 'WARNING';
  path: string;
  text: string;
}

/** The best candidate a task produced, and what is still wrong with it. `candidate` is empty when no reply was readable. */
export interface AiAssistResult {
  candidate: string;
  explanation: string;
  assumptions: string[];
  issues: AiAssistIssue[];
  rounds: number;
  model: string;
}

/**
 * What the AI library offers the forms. base-entity only knows this seam: an application that wants the buttons provides
 * an implementation (base-ai's `BASE_AI_ASSIST_PROVIDERS`), and without one no button is shown.
 */
export interface AiAssistClient {
  /** Whether the task can be used now: the organization has an AI subscription and the user may edit its target. */
  isAvailable(task: string): Signal<boolean>;

  /** Asks the task for a candidate. Rejects when the request fails; the failure is the caller's to report. */
  run(task: string, request: AiAssistRequest): Promise<AiAssistResult>;
}

export const AI_ASSIST_CLIENT = new InjectionToken<AiAssistClient>('AI_ASSIST_CLIENT');
