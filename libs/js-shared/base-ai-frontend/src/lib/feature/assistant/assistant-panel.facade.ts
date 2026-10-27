import { inject, Injectable, signal } from '@angular/core';
import { AssistantFailureKind, AssistantService, AssistantTurnError } from '../../domain/assistant/assistant.service';

/** One line of the conversation as the panel shows it. */
export interface AssistantLine {
  role: 'designer' | 'assistant';
  text: string;
}

/**
 * The state of one panel: the conversation so far, whether a turn is running, and why the last turn failed. Lives
 * with the panel component, so each panel is its own conversation.
 */
@Injectable()
export class AssistantPanelFacade {
  private readonly assistant = inject(AssistantService);

  readonly lines = signal<readonly AssistantLine[]>([]);
  readonly busy = signal(false);
  readonly failure = signal<AssistantFailureKind | undefined>(undefined);

  private sessionId: string | undefined;

  /**
   * Sends the designer's message and appends the answer. Ignored while a turn is running or when the text is blank.
   * A conversation the server no longer knows is started again on the next message.
   */
  async send(text: string): Promise<void> {
    const message = text.trim();
    if (!message || this.busy()) return;

    this.failure.set(undefined);
    this.busy.set(true);
    this.lines.update((lines) => [...lines, { role: 'designer', text: message }]);
    try {
      const turn = await this.assistant.turn(message, this.sessionId);
      this.sessionId = turn.sessionId;
      this.lines.update((lines) => [...lines, { role: 'assistant', text: turn.reply }]);
    } catch (error) {
      const kind = error instanceof AssistantTurnError ? error.kind : 'failed';
      if (kind === 'session-not-found') this.sessionId = undefined;
      this.failure.set(kind);
    } finally {
      this.busy.set(false);
    }
  }

  /** Forgets the conversation, so the next message opens a new one. */
  reset(): void {
    this.sessionId = undefined;
    this.lines.set([]);
    this.failure.set(undefined);
  }
}
