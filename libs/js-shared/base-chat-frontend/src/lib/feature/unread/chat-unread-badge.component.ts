import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input } from '@angular/core';
import { provideTranslocoScope, TranslocoPipe } from '@jsverse/transloco';
import { BASE_CHAT_TRANSLOCO_SCOPE } from '../../base-chat.i18n';
import { ChatStore } from '../../domain/chat.store';

/**
 * The `chat-unread-badge` widget: how many unread messages there are across everything the user follows, as a
 * small pill that is simply absent when there are none — place it next to a menu entry or an icon.
 *
 * It keeps the live stream open while it is on screen, so the number follows what others write; a number above
 * `max` shows as `max+`.
 */
@Component({
  selector: 'pp-chat-unread-badge',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideTranslocoScope({ scope: BASE_CHAT_TRANSLOCO_SCOPE, alias: BASE_CHAT_TRANSLOCO_SCOPE })],
  template: `
    @if (count() > 0) {
      <span class="pp-chat-badge" role="status" data-testid="chat-unread-badge" [attr.aria-label]="'base_chat.badge.label' | transloco: { count: count() }">{{ label() }}</span>
    }
  `,
  styles: [
    `
      :host {
        display: inline-block;
      }
      .pp-chat-badge {
        display: inline-block;
        min-width: 1.25rem;
        padding: 0 0.35rem;
        border-radius: 0.75rem;
        text-align: center;
        font-size: 0.75rem;
        font-weight: 600;
        line-height: 1.25rem;
        background: var(--pp-button-primary-bg, #186fce);
        color: var(--pp-button-primary-text, #ffffff);
      }
    `,
  ],
})
export class ChatUnreadBadgeComponent {
  /** The largest number shown; more shows as `max+`. */
  readonly max = input(99);

  private readonly store = inject(ChatStore);

  protected readonly count = this.store.totalUnread;
  protected readonly label = computed(() => (this.count() > this.max() ? `${this.max()}+` : String(this.count())));

  constructor() {
    this.store.connect();
    inject(DestroyRef).onDestroy(() => this.store.release());
  }
}
