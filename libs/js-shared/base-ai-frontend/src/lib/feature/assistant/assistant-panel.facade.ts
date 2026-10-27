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
   * Sends the designer's message and shows the answer as it arrives: the assistant's line appears with the first
   * piece and grows with each one, then settles on the whole reply. Ignored while a turn is running or when the text
   * is blank. A conversation the server no longer knows is started again on the next message.
   */
  async send(text: string): Promise<void> {
    const message = text.trim();
    if (!message || this.busy()) return;

    this.failure.set(undefined);
    this.busy.set(true);
    this.lines.update((lines) => [...lines, { role: 'designer', text: message }]);
    let answering = false;
    try {
      const turn = await this.assistant.turn(message, this.sessionId, (delta) => {
        this.lines.update((lines) => (answering ? withLastText(lines, lastText(lines) + delta) : [...lines, { role: 'assistant', text: delta }]));
        answering = true;
      });
      this.sessionId = turn.sessionId;
      this.lines.update((lines) => (answering ? withLastText(lines, turn.reply) : [...lines, { role: 'assistant', text: turn.reply }]));
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

function lastText(lines: readonly AssistantLine[]): string {
  return lines.at(-1)?.text ?? '';
}

function withLastText(lines: readonly AssistantLine[], text: string): readonly AssistantLine[] {
  const last = lines.at(-1);
  return last ? [...lines.slice(0, -1), { ...last, text }] : lines;
}
