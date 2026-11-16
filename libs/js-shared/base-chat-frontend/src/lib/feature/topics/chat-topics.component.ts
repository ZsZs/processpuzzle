import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { provideTranslocoScope, TranslocoPipe } from '@jsverse/transloco';
import { BASE_CHAT_TRANSLOCO_SCOPE } from '../../base-chat.i18n';
import { CHAT_ACTIVE_SUBJECT } from '../../domain/chat-identity';
import { failureKindOf } from '../../domain/chat.service';
import { ChatStore } from '../../domain/chat.store';
import { ChatSubjectRef, ChatSubjectType, unreadOf } from '../../domain/chat.types';
import { ChatPanelComponent } from '../panel/chat-panel.component';

type TopicsStatus = 'idle' | 'loading' | 'ready' | 'error';

/**
 * The `chat-topics` widget: the titled topics of one record's `MANY` chat, a form to start another, and the selected
 * topic's conversation below — the `chat-panel`, shown by conversation id.
 *
 * **Which record.** The `subjectId` input — an entity instance — or the subject the host offers through
 * {@link CHAT_ACTIVE_SUBJECT}. `definitionKey` names the chat definition, which has to be one with several
 * conversations per record; it is also what a new topic is started from.
 *
 * **Who may start a topic** is the definition's create roles, which only the server knows: the form is offered to
 * everybody and a refusal is said in words, as the panel's "Start a chat" is.
 *
 * Topics another user starts show after the next reload; the unread count of a topic already known is live.
 */
@Component({
  selector: 'pp-chat-topics',
  standalone: true,
  imports: [TranslocoPipe, ChatPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideTranslocoScope({ scope: BASE_CHAT_TRANSLOCO_SCOPE, alias: BASE_CHAT_TRANSLOCO_SCOPE })],
  template: `
    <section class="pp-topics" [attr.aria-label]="'base_chat.topics.title' | transloco">
      @switch (status()) {
        @case ('idle') {
          <p class="pp-topics__hint" data-testid="topics-no-subject">{{ 'base_chat.topics.no_subject' | transloco }}</p>
        }
        @case ('loading') {
          <p class="pp-topics__hint" role="status" data-testid="topics-loading">{{ 'base_chat.topics.loading' | transloco }}</p>
        }
        @case ('error') {
          <p class="pp-topics__hint" role="alert" data-testid="topics-error">{{ 'base_chat.topics.error' | transloco }}</p>
          <button type="button" class="pp-topics__button" data-testid="topics-reload" (click)="reload()">{{ 'base_chat.topics.reload' | transloco }}</button>
        }
        @case ('ready') {
          @if (topics().length === 0) {
            <p class="pp-topics__hint" data-testid="topics-empty">{{ 'base_chat.topics.empty' | transloco }}</p>
          }
          <ul class="pp-topics__list">
            @for (topic of topics(); track topic.id) {
              <li>
                <button
                  type="button"
                  class="pp-topics__topic"
                  [class.pp-topics__topic--selected]="topic.id === selectedId()"
                  data-testid="topic"
                  [attr.aria-label]="('base_chat.topics.select' | transloco) + ': ' + topic.title"
                  [attr.aria-current]="topic.id === selectedId() ? 'true' : null"
                  (click)="select(topic.id)"
                >
                  <span class="pp-topics__name">{{ topic.title }}</span>
                  @if (topic.closed) {
                    <span class="pp-topics__note" data-testid="topic-closed">{{ 'base_chat.topics.closed' | transloco }}</span>
                  }
                  @if (topic.unread > 0) {
                    <span class="pp-topics__unread" data-testid="topic-unread" [attr.aria-label]="topic.unread + ' ' + ('base_chat.topics.unread' | transloco)">{{ topic.unread }}</span>
                  }
                </button>
              </li>
            }
          </ul>
          @if (definitionKey()) {
            <form class="pp-topics__new" (submit)="create($event)">
              <input
                class="pp-topics__input"
                data-testid="topic-title"
                [attr.aria-label]="'base_chat.topics.new_label' | transloco"
                [placeholder]="'base_chat.topics.new_placeholder' | transloco"
                [value]="title()"
                (input)="title.set($any($event.target).value)"
              />
              <button type="submit" class="pp-topics__button pp-topics__button--primary" data-testid="topic-create" [disabled]="!canCreate()">
                {{ (creating() ? 'base_chat.topics.creating' : 'base_chat.topics.create') | transloco }}
              </button>
            </form>
            @if (createFailure(); as failure) {
              <p class="pp-topics__hint" role="alert" data-testid="topic-create-failed">
                {{ (failure === 'forbidden' ? 'base_chat.topics.create_forbidden' : 'base_chat.topics.create_failed') | transloco }}
              </p>
            }
          }
          @if (selectedId(); as id) {
            <pp-chat-panel class="pp-topics__panel" [conversationId]="id" />
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
      .pp-topics {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      .pp-topics__hint {
        margin: 0;
        opacity: 0.75;
      }
      .pp-topics__list {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }
      .pp-topics__topic {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        width: 100%;
        font: inherit;
        text-align: left;
        padding: 0.4rem 0.6rem;
        border: 1px solid transparent;
        border-radius: 4px;
        background: rgba(0, 0, 0, 0.04);
        cursor: pointer;
      }
      .pp-topics__topic--selected {
        border-color: var(--pp-button-primary-bg, #186fce);
      }
      .pp-topics__name {
        flex: 1;
      }
      .pp-topics__note {
        font-size: 0.75rem;
        opacity: 0.7;
      }
      .pp-topics__unread {
        min-width: 1.4rem;
        padding: 0 0.4rem;
        border-radius: 999px;
        text-align: center;
        font-size: 0.75rem;
        background: var(--pp-button-primary-bg, #186fce);
        color: var(--pp-button-primary-text, #ffffff);
      }
      .pp-topics__new {
        display: flex;
        gap: 0.5rem;
      }
      .pp-topics__input {
        flex: 1;
        font: inherit;
        padding: 0.35rem 0.5rem;
      }
      .pp-topics__button {
        font: inherit;
        padding: 0.35rem 0.9rem;
        border: none;
        border-radius: 4px;
        background: var(--pp-button-secondary-bg, #eeeeee);
        color: var(--pp-button-secondary-text, #186fce);
        cursor: pointer;
      }
      .pp-topics__button--primary {
        background: var(--pp-button-primary-bg, #186fce);
        color: var(--pp-button-primary-text, #ffffff);
      }
      .pp-topics__button:disabled {
        opacity: 0.5;
        cursor: default;
      }
      .pp-topics__panel {
        min-height: 16rem;
      }
    `,
  ],
})
export class ChatTopicsComponent {
  /** The entity instance whose topics are listed; the active working context when left empty. */
  readonly subjectId = input<string | undefined>(undefined);
  /** What kind of subject `subjectId` is; an entity instance when left empty. */
  readonly subjectType = input<ChatSubjectType | undefined>(undefined);
  /** The chat definition (one with several conversations per record); also what a new topic is started from. */
  readonly definitionKey = input<string | undefined>(undefined);

  private readonly store = inject(ChatStore);
  private readonly activeSubject = inject(CHAT_ACTIVE_SUBJECT, { optional: true });

  protected readonly status = signal<TopicsStatus>('idle');
  protected readonly title = signal('');
  protected readonly creating = signal(false);
  protected readonly createFailure = signal<'forbidden' | 'other' | undefined>(undefined);
  protected readonly selectedId = signal<string | undefined>(undefined);
  private readonly ids = signal<string[]>([]);

  protected readonly subject = computed<ChatSubjectRef | undefined>(() => {
    const explicit = this.subjectId()?.trim();
    return explicit ? { subjectType: this.subjectType() ?? 'ENTITY', subjectId: explicit } : this.activeSubject?.();
  });
  protected readonly topics = computed(() =>
    this.ids().flatMap((id) => {
      const view = this.store.viewOf(id);
      if (!view) return [];
      const conversation = view.conversation;
      return [{ id, title: conversation.title || id, unread: unreadOf(conversation), closed: !conversation.permissions.canWrite }];
    }),
  );
  protected readonly canCreate = computed(() => this.title().trim().length > 0 && !this.creating());

  private loadSerial = 0;

  constructor() {
    this.store.connect();
    effect(() => {
      const subject = this.subject();
      const definitionKey = this.definitionKey();
      untracked(() => void this.load(subject, definitionKey));
    });
  }

  protected reload(): void {
    void this.load(this.subject(), this.definitionKey());
  }

  protected select(id: string): void {
    this.selectedId.set(id);
  }

  protected async create(event: Event): Promise<void> {
    event.preventDefault();
    const subject = this.subject();
    const definitionKey = this.definitionKey();
    const title = this.title().trim();
    if (!subject || !definitionKey || !title || this.creating()) return;
    this.creating.set(true);
    this.createFailure.set(undefined);
    try {
      const id = await this.store.startConversation(definitionKey, subject.subjectId, title);
      this.ids.update((known) => (known.includes(id) ? known : [...known, id]));
      this.title.set('');
      this.selectedId.set(id);
    } catch (error) {
      this.createFailure.set(failureKindOf(error) === 'forbidden' ? 'forbidden' : 'other');
    } finally {
      this.creating.set(false);
    }
  }

  private async load(subject: ChatSubjectRef | undefined, definitionKey: string | undefined): Promise<void> {
    const serial = ++this.loadSerial;
    this.selectedId.set(undefined);
    this.createFailure.set(undefined);
    this.ids.set([]);
    if (!subject) {
      this.status.set('idle');
      return;
    }
    this.status.set('loading');
    try {
      const ids = await this.store.listForSubject(subject, definitionKey);
      if (serial !== this.loadSerial) return; // the subject changed while this was loading
      this.ids.set(ids);
      this.status.set('ready');
    } catch {
      if (serial === this.loadSerial) this.status.set('error');
    }
  }
}
