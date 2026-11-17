import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, ElementRef, inject, input, signal, untracked, viewChild } from '@angular/core';
import { provideTranslocoScope, TranslocoPipe } from '@jsverse/transloco';
import { BASE_CHAT_TRANSLOCO_SCOPE } from '../../base-chat.i18n';
import { CHAT_ACTIVE_SUBJECT, CHAT_IDENTITY } from '../../domain/chat-identity';
import { ChatDefinitionLookup } from '../../domain/chat-definition.lookup';
import { failureKindOf } from '../../domain/chat.service';
import { type TopicFailure, topicFailureKey, topicFailureOf } from '../topic-failure';
import { ChatStore, PendingMessage } from '../../domain/chat.store';
import { ChatMessage, ChatSubjectRef, ChatSubjectType, unreadOf } from '../../domain/chat.types';

type PanelStatus = 'idle' | 'loading' | 'ready' | 'none' | 'error';

/** How close to the bottom the log has to be for a new message to scroll it along. */
const STICK_THRESHOLD_PX = 48;

/**
 * The `chat-panel` widget: the conversation of one subject, with a composer.
 *
 * **Which subject.** The `subjectId` input — an entity instance — or, when none is given, the subject the host
 * offers through {@link CHAT_ACTIVE_SUBJECT}, in practice the active working context. `definitionKey` narrows to
 * one chat definition, and is what a "Start a chat" button opens when the subject has no conversation yet.
 *
 * **What it does for you.** It is live while it is on screen (the store holds one shared stream), marks the
 * conversation read as soon as it is shown and for every message that arrives meanwhile, and sends optimistically:
 * a message shows at once, and a failed one stays with a retry that cannot post it twice. A conversation the user
 * may read but not write shows no composer.
 *
 * Message text is rendered as text — never as markup.
 */
@Component({
  selector: 'pp-chat-panel',
  standalone: true,
  imports: [DatePipe, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideTranslocoScope({ scope: BASE_CHAT_TRANSLOCO_SCOPE, alias: BASE_CHAT_TRANSLOCO_SCOPE })],
  template: `
    <section class="pp-chat" [attr.aria-label]="'base_chat.panel.title' | transloco">
      @if (topicMode() && status() !== 'error') {
        <div class="pp-chat__topics">
          @if (topics().length > 0) {
            <select class="pp-chat__select" data-testid="topic-select" [attr.aria-label]="'base_chat.topics.select' | transloco" (change)="pickTopic($any($event.target).value)">
              @for (topic of topics(); track topic.id) {
                <option [value]="topic.id" [selected]="topic.id === shownId()">
                  {{ topic.title }}{{ topic.closed ? ' (' + ('base_chat.topics.closed' | transloco) + ')' : '' }}{{ topic.unread > 0 ? ' [' + topic.unread + ']' : '' }}
                </option>
              }
            </select>
          }
          <form class="pp-chat__new" (submit)="createTopic($event)">
            <input
              class="pp-chat__input pp-chat__input--title"
              data-testid="topic-title"
              [attr.aria-label]="'base_chat.topics.new_label' | transloco"
              [placeholder]="'base_chat.topics.new_placeholder' | transloco"
              [value]="topicTitle()"
              (input)="topicTitle.set($any($event.target).value)"
            />
            <button type="submit" class="pp-chat__button pp-chat__button--primary" data-testid="topic-create" [disabled]="!canCreateTopic()">
              {{ (starting() ? 'base_chat.topics.creating' : 'base_chat.topics.create') | transloco }}
            </button>
          </form>
          @if (startFailure(); as failure) {
            <p class="pp-chat__hint" role="alert" data-testid="topic-create-failed">
              {{ topicFailureText(failure) | transloco }}
            </p>
          }
        </div>
      }
      @if (connectionLost()) {
        <p class="pp-chat__status" role="status" data-testid="chat-reconnecting">{{ 'base_chat.panel.reconnecting' | transloco }}</p>
      }
      @switch (status()) {
        @case ('idle') {
          <p class="pp-chat__hint" data-testid="chat-no-subject">{{ 'base_chat.panel.no_subject' | transloco }}</p>
        }
        @case ('loading') {
          <p class="pp-chat__hint" role="status" data-testid="chat-loading">{{ 'base_chat.panel.loading' | transloco }}</p>
        }
        @case ('error') {
          <p class="pp-chat__hint" role="alert" data-testid="chat-error">{{ 'base_chat.panel.error' | transloco }}</p>
          <button type="button" class="pp-chat__button" data-testid="chat-reload" (click)="reload()">{{ 'base_chat.panel.reload' | transloco }}</button>
        }
        @case ('none') {
          @if (topicMode()) {
            <p class="pp-chat__hint" data-testid="topics-empty">{{ 'base_chat.topics.empty' | transloco }}</p>
          } @else if (definitionKey()) {
            <button type="button" class="pp-chat__button pp-chat__button--primary" data-testid="chat-start" [disabled]="starting()" (click)="start()">
              {{ (starting() ? 'base_chat.panel.starting' : 'base_chat.panel.start') | transloco }}
            </button>
            @if (startFailure(); as failure) {
              <p class="pp-chat__hint" role="alert" data-testid="chat-start-failed">
                {{ (failure === 'forbidden' ? 'base_chat.panel.start_forbidden' : 'base_chat.panel.start_failed') | transloco }}
              </p>
            }
          } @else {
            <p class="pp-chat__hint" data-testid="chat-no-chat">{{ 'base_chat.panel.no_chat' | transloco }}</p>
          }
        }
        @case ('ready') {
          @if (view(); as shown) {
            @if (shown.conversation.title && !topicMode()) {
              <h3 class="pp-chat__title" data-testid="chat-title">{{ shown.conversation.title }}</h3>
            }
            <ol #log class="pp-chat__log" role="log" aria-live="polite" aria-relevant="additions" data-testid="chat-log" (scroll)="onScroll()">
              @if (shown.hasMore) {
                <li class="pp-chat__more">
                  <button type="button" class="pp-chat__button" data-testid="chat-load-older" [disabled]="shown.loading" (click)="loadOlder()">
                    {{ 'base_chat.panel.load_earlier' | transloco }}
                  </button>
                </li>
              }
              @if (shown.messages.length === 0 && shown.pending.length === 0) {
                <li class="pp-chat__hint" data-testid="chat-empty">{{ 'base_chat.panel.empty' | transloco }}</li>
              }
              @for (message of shown.messages; track message.seq) {
                <li class="pp-chat__message" [class.pp-chat__message--own]="isOwn(message)" data-testid="chat-message">
                  <span class="pp-chat__author">{{ message.authorName || message.authorId }}</span>
                  <time class="pp-chat__time" [attr.datetime]="message.createdAt">{{ message.createdAt | date: 'short' }}</time>
                  <p class="pp-chat__body">{{ message.body }}</p>
                </li>
              }
              @for (pending of shown.pending; track pending.clientMessageId) {
                <li class="pp-chat__message pp-chat__message--own pp-chat__message--pending" [class.pp-chat__message--failed]="pending.status === 'failed'" data-testid="chat-pending">
                  <p class="pp-chat__body">{{ pending.body }}</p>
                  @if (pending.status === 'sending') {
                    <span class="pp-chat__note">{{ 'base_chat.panel.sending' | transloco }}</span>
                  } @else {
                    <span class="pp-chat__note pp-chat__note--error" role="alert" data-testid="chat-failed">{{ failureText(pending) | transloco }}</span>
                    <button type="button" class="pp-chat__link" data-testid="chat-retry" (click)="retry(pending)">{{ 'base_chat.panel.retry' | transloco }}</button>
                    <button type="button" class="pp-chat__link" data-testid="chat-discard" (click)="discard(pending)">{{ 'base_chat.panel.discard' | transloco }}</button>
                  }
                </li>
              }
            </ol>
            @if (shown.conversation.permissions.canWrite) {
              <form class="pp-chat__composer" (submit)="submit($event)">
                <textarea
                  class="pp-chat__input"
                  rows="2"
                  data-testid="chat-input"
                  [attr.aria-label]="'base_chat.panel.placeholder' | transloco"
                  [placeholder]="'base_chat.panel.placeholder' | transloco"
                  [value]="draft()"
                  (input)="draft.set($any($event.target).value)"
                  (keydown.enter)="onEnter($event)"
                ></textarea>
                <button type="submit" class="pp-chat__button pp-chat__button--primary" data-testid="chat-send" [disabled]="!canSend()">
                  {{ 'base_chat.panel.send' | transloco }}
                </button>
              </form>
            } @else {
              <p class="pp-chat__hint" data-testid="chat-read-only">{{ 'base_chat.panel.read_only' | transloco }}</p>
            }
          }
        }
      }
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .pp-chat {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        height: 100%;
        min-height: 12rem;
      }
      .pp-chat__title {
        margin: 0;
        font-size: 1rem;
      }
      .pp-chat__topics {
        display: flex;
        flex-direction: column;
        gap: 0.4rem;
      }
      .pp-chat__select {
        font: inherit;
        padding: 0.35rem 0.5rem;
      }
      .pp-chat__new {
        display: flex;
        gap: 0.5rem;
      }
      .pp-chat__log {
        flex: 1 1 auto;
        list-style: none;
        margin: 0;
        padding: 0.25rem;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        max-height: 28rem;
      }
      .pp-chat__message {
        max-width: 85%;
        align-self: flex-start;
        padding: 0.4rem 0.6rem;
        border-radius: 0.5rem;
        background: var(--pp-surface-card, var(--pp-color-light-blue, #e8f1fa));
      }
      .pp-chat__message--own {
        align-self: flex-end;
        background: var(--pp-button-secondary-bg, #ffffff);
        border: 1px solid var(--pp-button-primary-bg, #186fce);
      }
      .pp-chat__message--pending {
        opacity: 0.7;
      }
      .pp-chat__message--failed {
        opacity: 1;
        border-style: dashed;
      }
      .pp-chat__author {
        font-weight: 600;
        margin-right: 0.5rem;
      }
      .pp-chat__time,
      .pp-chat__note {
        font-size: 0.75rem;
        opacity: 0.75;
      }
      .pp-chat__note--error {
        color: var(--mat-sys-error, #b3261e);
        opacity: 1;
      }
      .pp-chat__body {
        margin: 0.15rem 0 0;
        white-space: pre-wrap;
        overflow-wrap: anywhere;
      }
      .pp-chat__hint,
      .pp-chat__status {
        margin: 0;
        opacity: 0.8;
      }
      .pp-chat__more {
        text-align: center;
      }
      .pp-chat__composer {
        display: flex;
        gap: 0.5rem;
        align-items: flex-end;
      }
      .pp-chat__input {
        flex: 1 1 auto;
        resize: vertical;
        font: inherit;
        padding: 0.4rem 0.5rem;
      }
      .pp-chat__button {
        font: inherit;
        padding: 0.4rem 0.9rem;
        border-radius: 0.25rem;
        border: 1px solid var(--pp-button-primary-bg, #186fce);
        background: var(--pp-button-secondary-bg, #ffffff);
        color: var(--pp-button-secondary-text, #186fce);
        cursor: pointer;
      }
      .pp-chat__button--primary {
        background: var(--pp-button-primary-bg, #186fce);
        color: var(--pp-button-primary-text, #ffffff);
      }
      .pp-chat__button:disabled {
        opacity: 0.5;
        cursor: default;
      }
      .pp-chat__link {
        font: inherit;
        font-size: 0.75rem;
        background: none;
        border: none;
        padding: 0 0.25rem;
        color: var(--pp-button-secondary-text, #186fce);
        text-decoration: underline;
        cursor: pointer;
      }
    `,
  ],
})
export class ChatPanelComponent {
  /** The entity instance to chat about; the active working context when left empty. */
  readonly subjectId = input<string | undefined>(undefined);
  /** What kind of subject `subjectId` is; an entity instance when left empty. */
  readonly subjectType = input<ChatSubjectType | undefined>(undefined);
  /** The chat definition to show, and to start from when the subject has no conversation. */
  readonly definitionKey = input<string | undefined>(undefined);
  /**
   * One conversation to show, by id — a topic picked from the topics list. The conversation has to be one the store
   * already knows (`listForSubject` or `startConversation` put it there); `subjectId` and `definitionKey` are then
   * not used to look for it.
   */
  readonly conversationId = input<string | undefined>(undefined);

  private readonly store = inject(ChatStore);
  private readonly identity = inject(CHAT_IDENTITY);
  private readonly activeSubject = inject(CHAT_ACTIVE_SUBJECT, { optional: true });
  private readonly definitions = inject(ChatDefinitionLookup);
  private readonly log = viewChild<ElementRef<HTMLElement>>('log');

  protected readonly status = signal<PanelStatus>('idle');
  protected readonly starting = signal(false);
  protected readonly startFailure = signal<TopicFailure | undefined>(undefined);
  protected readonly draft = signal('');
  protected readonly shownId = signal<string | undefined>(undefined);
  /** The definition holds several titled topics per record: the panel offers them in a drop-down, one at a time. */
  protected readonly topicMode = signal(false);
  protected readonly topicTitle = signal('');
  private readonly topicIds = signal<string[]>([]);

  protected readonly subject = computed<ChatSubjectRef | undefined>(() => {
    const explicit = this.subjectId()?.trim();
    return explicit ? { subjectType: this.subjectType() ?? 'ENTITY', subjectId: explicit } : this.activeSubject?.();
  });
  protected readonly view = computed(() => {
    const id = this.shownId();
    return id ? this.store.viewOf(id) : undefined;
  });
  protected readonly connectionLost = computed(() => this.status() === 'ready' && this.store.connection() === 'reconnecting');
  protected readonly canSend = computed(() => this.draft().trim().length > 0);
  protected readonly topics = computed(() =>
    this.topicIds().flatMap((id) => {
      const shown = this.store.viewOf(id);
      if (!shown) return [];
      return [{ id, title: shown.conversation.title || id, unread: unreadOf(shown.conversation), closed: !shown.conversation.permissions.canWrite }];
    }),
  );
  protected readonly canCreateTopic = computed(() => this.topicTitle().trim().length > 0 && !this.starting());

  private loadSerial = 0;
  private viewing: string | undefined;
  private stickToBottom = true;
  private newestShown = 0;

  constructor() {
    this.store.connect();
    inject(DestroyRef).onDestroy(() => {
      this.stopViewing();
      this.store.release();
    });
    effect(() => {
      const subject = this.subject();
      const definitionKey = this.definitionKey();
      const conversationId = this.conversationId();
      untracked(() => void this.load(subject, definitionKey, conversationId));
    });
    effect(() => {
      const shown = this.view();
      const newest = (shown?.messages.at(-1)?.seq ?? 0) + (shown?.pending.length ?? 0) * 0.5;
      untracked(() => this.followNewest(newest));
    });
  }

  protected isOwn(message: ChatMessage): boolean {
    const userId = this.identity.userId();
    return !!userId && message.authorId === userId;
  }

  protected failureText(pending: PendingMessage): string {
    switch (pending.failure) {
      case 'forbidden':
        return 'base_chat.panel.failed_forbidden';
      case 'conflict':
        return 'base_chat.panel.failed_conflict';
      case 'rate-limited':
        return 'base_chat.panel.failed_rate_limited';
      default:
        return 'base_chat.panel.failed';
    }
  }

  protected reload(): void {
    void this.load(this.subject(), this.definitionKey(), this.conversationId());
  }

  protected async start(): Promise<void> {
    const subject = this.subject();
    const definitionKey = this.definitionKey();
    if (!subject || !definitionKey || this.starting()) return;
    this.starting.set(true);
    this.startFailure.set(undefined);
    try {
      const id = await this.store.startConversation(definitionKey, subject.subjectId);
      this.show(id);
    } catch (error) {
      this.startFailure.set(failureKindOf(error) === 'forbidden' ? 'forbidden' : 'other');
    } finally {
      this.starting.set(false);
    }
  }

  protected topicFailureText(failure: TopicFailure): string {
    return topicFailureKey(failure);
  }

  protected pickTopic(id: string): void {
    void this.openTopic(id);
  }

  protected async createTopic(event: Event): Promise<void> {
    event.preventDefault();
    const subject = this.subject();
    const definitionKey = this.definitionKey();
    const title = this.topicTitle().trim();
    if (!subject || !definitionKey || !title || this.starting()) return;
    this.starting.set(true);
    this.startFailure.set(undefined);
    try {
      const id = await this.store.startConversation(definitionKey, subject.subjectId, title);
      this.topicIds.update((known) => (known.includes(id) ? known : [...known, id]));
      this.topicTitle.set('');
      this.show(id);
    } catch (error) {
      this.startFailure.set(topicFailureOf(error));
    } finally {
      this.starting.set(false);
    }
  }

  protected submit(event: Event): void {
    event.preventDefault();
    this.sendDraft();
  }

  protected onEnter(event: Event): void {
    // Enter sends; Shift+Enter is a line break.
    if ((event as KeyboardEvent).shiftKey) return;
    event.preventDefault();
    this.sendDraft();
  }

  protected loadOlder(): void {
    const id = this.shownId();
    if (id) void this.store.loadOlder(id).catch(() => undefined);
  }

  protected retry(pending: PendingMessage): void {
    const id = this.shownId();
    if (id) void this.store.retry(id, pending.clientMessageId);
  }

  protected discard(pending: PendingMessage): void {
    const id = this.shownId();
    if (id) this.store.discard(id, pending.clientMessageId);
  }

  protected onScroll(): void {
    const log = this.log()?.nativeElement;
    if (log) this.stickToBottom = log.scrollHeight - log.scrollTop - log.clientHeight <= STICK_THRESHOLD_PX;
  }

  private sendDraft(): void {
    const id = this.shownId();
    const text = this.draft().trim();
    if (!id || !text) return;
    this.draft.set('');
    this.stickToBottom = true;
    void this.store.send(id, text);
  }

  private async load(subject: ChatSubjectRef | undefined, definitionKey: string | undefined, conversationId: string | undefined): Promise<void> {
    const serial = ++this.loadSerial;
    this.stopViewing();
    this.shownId.set(undefined);
    this.startFailure.set(undefined);
    this.topicMode.set(false);
    this.topicIds.set([]);
    if (conversationId) {
      await this.loadKnown(serial, conversationId);
      return;
    }
    if (!subject) {
      this.status.set('idle');
      return;
    }
    this.status.set('loading');
    try {
      if (definitionKey && (await this.definitions.hasTopics(definitionKey))) {
        await this.loadTopics(serial, subject, definitionKey);
        return;
      }
      const id = await this.store.openForSubject(subject, definitionKey);
      if (serial !== this.loadSerial) return; // the subject changed while this was loading
      if (id) this.show(id);
      else this.status.set('none');
    } catch {
      if (serial === this.loadSerial) this.status.set('error');
    }
  }

  /** A topics definition: lists the record's topics and shows the newest, so exactly one is ever on screen. */
  private async loadTopics(serial: number, subject: ChatSubjectRef, definitionKey: string): Promise<void> {
    const ids = await this.store.listForSubject(subject, definitionKey);
    if (serial !== this.loadSerial) return;
    this.topicMode.set(true);
    this.topicIds.set(ids);
    const newest = ids.at(-1);
    if (newest) await this.openTopic(newest, serial);
    else this.status.set('none');
  }

  private async openTopic(id: string, serial = this.loadSerial): Promise<void> {
    this.stopViewing();
    this.shownId.set(undefined);
    this.status.set('loading');
    try {
      await this.store.loadHistory(id);
      if (serial === this.loadSerial) this.show(id);
    } catch {
      if (serial === this.loadSerial) this.status.set('error');
    }
  }

  /** Shows a conversation the store already knows, whose subject and definition the caller has settled. */
  private async loadKnown(serial: number, conversationId: string): Promise<void> {
    this.status.set('loading');
    try {
      await this.store.loadHistory(conversationId);
      if (serial === this.loadSerial) this.show(conversationId);
    } catch {
      if (serial === this.loadSerial) this.status.set('error');
    }
  }

  private show(conversationId: string): void {
    this.stopViewing();
    this.shownId.set(conversationId);
    this.status.set('ready');
    this.viewing = conversationId;
    this.store.setViewing(conversationId, true);
    this.stickToBottom = true;
  }

  private stopViewing(): void {
    if (this.viewing) this.store.setViewing(this.viewing, false);
    this.viewing = undefined;
  }

  /** Scrolls the log to its end when something newer arrived — unless the reader had scrolled up. */
  private followNewest(newest: number): void {
    if (newest === this.newestShown) return;
    this.newestShown = newest;
    if (!this.stickToBottom) return;
    queueMicrotask(() => {
      const log = this.log()?.nativeElement;
      if (log) log.scrollTop = log.scrollHeight;
    });
  }
}
