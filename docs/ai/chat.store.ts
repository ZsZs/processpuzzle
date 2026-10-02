import { Injectable, computed, inject, signal } from '@angular/core';
import { AssistantApiService } from './assistant-api.service';
import {
  ArtifactType,
  AssistantEvent,
  ChatMessage,
  DraftYaml,
  ValidationIssue,
} from './chat.models';

type Status = 'idle' | 'streaming' | 'applying' | 'error';

/** Provide at the AssistantPageComponent level: providers: [ChatStore] */
@Injectable()
export class ChatStore {
  private readonly api = inject(AssistantApiService);
  private abortController: AbortController | null = null;

  // ---- state ----
  readonly sessionId = signal(crypto.randomUUID());
  readonly messages = signal<ChatMessage[]>([]);
  readonly streamingText = signal('');
  readonly status = signal<Status>('idle');
  readonly error = signal<string | null>(null);
  readonly draft = signal<DraftYaml>({});
  readonly issues = signal<ValidationIssue[]>([]);
  /** Artifact types changed by the latest AI turn (drives the "changed" badges). */
  readonly changedArtifacts = signal<ReadonlySet<ArtifactType>>(new Set());
  private readonly draftHistory = signal<DraftYaml[]>([]);

  // ---- derived ----
  readonly isStreaming = computed(() => this.status() === 'streaming');
  readonly hasDraft = computed(() => Object.values(this.draft()).some((yaml) => !!yaml?.trim()));
  readonly hasBlockingIssues = computed(() => this.issues().some((issue) => issue.blocking));
  readonly canUndo = computed(() => this.draftHistory().length > 0 && !this.isStreaming());
  readonly canApply = computed(
    () => this.hasDraft() && !this.hasBlockingIssues() && this.status() === 'idle',
  );
  readonly hasUnappliedChanges = computed(() => this.hasDraft() && this.status() !== 'applying');

  // ---- actions ----
  async send(text: string): Promise<void> {
    const message = text.trim();
    if (!message || this.isStreaming()) return;

    this.appendMessage({ id: crypto.randomUUID(), role: 'user', text: message });
    this.error.set(null);
    this.streamingText.set('');
    this.changedArtifacts.set(new Set());
    this.draftHistory.update((history) => [...history, this.draft()]); // snapshot for undo
    this.status.set('streaming');

    this.abortController = new AbortController();
    let pendingOptions: ChatMessage['options'];

    try {
      for await (const event of this.api.stream(this.sessionId(), message, this.abortController.signal)) {
        pendingOptions = this.handleEvent(event) ?? pendingOptions;
      }
    } catch (err) {
      if ((err as Error).name !== 'AbortError') {
        this.error.set((err as Error).message ?? 'Unknown error');
        this.status.set('error');
      }
    } finally {
      this.commitStreamingText(pendingOptions);
      if (this.status() === 'streaming') this.status.set('idle');
      this.abortController = null;
    }
  }

  /** Clicking a suggestion chip simply sends its label as the next user message. */
  selectOption(optionId: string): void {
    const option = this.messages()
      .flatMap((m) => m.options ?? [])
      .find((o) => o.id === optionId);
    if (option) void this.send(option.label);
  }

  cancel(): void {
    this.abortController?.abort();
    this.status.set('idle');
  }

  async editDraft(type: ArtifactType, yaml: string): Promise<void> {
    this.draftHistory.update((history) => [...history, this.draft()]);
    const next = { ...this.draft(), [type]: yaml };
    this.draft.set(next);
    await this.syncDraft(next);
  }

  async undo(): Promise<void> {
    const history = this.draftHistory();
    if (!history.length) return;
    const previous = history[history.length - 1];
    this.draftHistory.set(history.slice(0, -1));
    this.draft.set(previous);
    this.changedArtifacts.set(new Set());
    await this.syncDraft(previous);
  }

  async apply(): Promise<boolean> {
    if (!this.canApply()) return false;
    this.status.set('applying');
    try {
      const result = await this.api.apply(this.sessionId());
      this.issues.set(result.issues);
      if (result.success) {
        this.draftHistory.set([]);
        this.changedArtifacts.set(new Set());
      }
      return result.success;
    } catch (err) {
      this.error.set((err as Error).message);
      return false;
    } finally {
      this.status.set('idle');
    }
  }

  reset(): void {
    this.cancel();
    this.sessionId.set(crypto.randomUUID());
    this.messages.set([]);
    this.streamingText.set('');
    this.draft.set({});
    this.draftHistory.set([]);
    this.issues.set([]);
    this.changedArtifacts.set(new Set());
    this.error.set(null);
  }

  // ---- internals ----
  /** Maps one server event onto signals. Returns options to attach to the assistant message. */
  private handleEvent(event: AssistantEvent): ChatMessage['options'] | void {
    switch (event.type) {
      case 'token':
        this.streamingText.update((text) => text + event.text);
        break;
      case 'clarification':
        this.streamingText.update((text) => (text ? text + '\n\n' : '') + event.questions.join('\n'));
        return event.options;
      case 'explanation':
        this.streamingText.update((text) => (text ? text + '\n\n' : '') + event.text);
        break;
      case 'draft_update':
        this.draft.update((draft) => ({ ...draft, [event.artifactType]: event.yaml }));
        this.changedArtifacts.update((set) => new Set(set).add(event.artifactType));
        break;
      case 'issues':
        this.issues.set(event.issues);
        break;
      case 'error':
        this.error.set(event.message);
        this.status.set('error');
        break;
      case 'done':
        break;
    }
  }

  private commitStreamingText(options?: ChatMessage['options']): void {
    const text = this.streamingText().trim();
    if (text || options?.length) {
      this.appendMessage({ id: crypto.randomUUID(), role: 'assistant', text, options });
    }
    this.streamingText.set('');
  }

  private appendMessage(message: ChatMessage): void {
    this.messages.update((messages) => [...messages, message]);
  }

  private async syncDraft(draft: DraftYaml): Promise<void> {
    try {
      this.issues.set(await this.api.replaceDraft(this.sessionId(), draft));
    } catch (err) {
      this.error.set((err as Error).message);
    }
  }
}
