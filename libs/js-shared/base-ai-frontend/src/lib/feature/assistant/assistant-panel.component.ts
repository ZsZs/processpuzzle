import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { provideTranslocoScope, TranslocoPipe } from '@jsverse/transloco';
import { MarkdownComponent } from 'ngx-markdown';
import { ASSISTANT_I18N_SCOPE, BASE_AI_TRANSLOCO_SCOPE } from '../../base-ai.i18n';
import { AssistantPanelFacade } from './assistant-panel.facade';

/**
 * The design assistant as a side panel: a conversation with the assistant, where it drafts definitions for the
 * organization the designer is working in. A page hosts it beside its designers; the organization is the one the
 * configured AI root names.
 *
 * What the assistant drafts is shown by the designers themselves; this panel only says what it answered. Nothing it
 * writes is published here.
 */
@Component({
  selector: 'pp-assistant-panel',
  imports: [FormsModule, MatButton, MarkdownComponent, TranslocoPipe],
  // The alias is spelled out for the reason base-ai.i18n gives: transloco would otherwise camel-case the scope.
  providers: [AssistantPanelFacade, provideTranslocoScope({ scope: BASE_AI_TRANSLOCO_SCOPE, alias: BASE_AI_TRANSLOCO_SCOPE })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="pp-assistant" [attr.aria-label]="scope + '.title' | transloco">
      <h3 class="pp-assistant__title">{{ scope + '.title' | transloco }}</h3>

      <ol class="pp-assistant__lines" aria-live="polite">
        @for (line of facade.lines(); track $index) {
          <li class="pp-assistant__line pp-assistant__line--{{ line.role }}">
            <span class="material-symbols-outlined pp-assistant__icon" aria-hidden="true">{{ line.role === 'designer' ? 'person' : 'smart_toy' }}</span>
            <span class="pp-assistant__speaker">{{ scope + '.' + line.role | transloco }}</span>
            <markdown class="pp-assistant__text" [data]="line.text"></markdown>
          </li>
        } @empty {
          <li class="pp-assistant__hint">{{ scope + '.empty' | transloco }}</li>
        }
      </ol>

      @if (facade.failure(); as failure) {
        <p class="pp-assistant__failure" role="alert">{{ scope + '.failures.' + failure | transloco }}</p>
      }

      <form class="pp-assistant__form" (ngSubmit)="send()">
        <textarea
          class="pp-assistant__input"
          name="message"
          rows="3"
          maxlength="4000"
          [attr.aria-label]="scope + '.input' | transloco"
          [placeholder]="scope + '.placeholder' | transloco"
          [(ngModel)]="draft"
          [disabled]="facade.busy()"
          (keydown.enter)="onEnter($event)"
        ></textarea>
        <div class="pp-assistant__actions">
          <button matButton="outlined" type="button" [disabled]="facade.busy()" (click)="facade.reset()">
            {{ scope + '.new' | transloco }}
          </button>
          <button matButton="filled" type="submit" [disabled]="facade.busy() || !draft.trim()">
            {{ scope + (facade.busy() ? '.working' : '.send') | transloco }}
          </button>
        </div>
      </form>
    </section>
  `,
  styles: [
    `
      .pp-assistant {
        display: flex;
        flex-direction: column;
        gap: 1rem;
        width: 100%;
        box-sizing: border-box;
      }

      /* The conversation sits in a frame of its own and scrolls inside it, so the message box stays in reach. */
      .pp-assistant__lines {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
        min-height: 8rem;
        max-height: 60vh;
        margin: 0;
        padding: 0.75rem;
        box-sizing: border-box;
        overflow-y: auto;
        list-style: none;
        border: 1px solid var(--mat-sys-outline-variant);
        border-radius: 8px;
      }

      /* An icon at the start of each message, the speaker's name kept for screen readers only. */
      .pp-assistant__line {
        position: relative;
        display: grid;
        grid-template-columns: auto 1fr;
        column-gap: 0.5rem;
        align-items: start;
      }

      .pp-assistant__icon {
        font-size: 1.25rem;
        color: var(--mat-sys-primary);
      }

      .pp-assistant__speaker {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip: rect(0 0 0 0);
        white-space: nowrap;
      }

      .pp-assistant__text {
        min-width: 0;
        overflow-wrap: anywhere;
      }

      .pp-assistant__text :first-child {
        margin-top: 0;
      }

      .pp-assistant__form {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        width: 100%;
      }

      .pp-assistant__input {
        width: 100%;
        box-sizing: border-box;
        resize: vertical;
        font: inherit;
        padding: 0.5rem;
        border-radius: 4px;
      }

      .pp-assistant__actions {
        display: flex;
        justify-content: flex-end;
        gap: 0.5rem;
      }
    `,
  ],
})
export class AssistantPanelComponent {
  protected readonly facade = inject(AssistantPanelFacade);
  protected readonly scope = ASSISTANT_I18N_SCOPE;
  protected draft = '';

  protected async send(): Promise<void> {
    const text = this.draft;
    this.draft = '';
    await this.facade.send(text);
  }

  /** Enter sends the message; Shift+Enter starts a new line, and Enter while an IME composes text is left alone. */
  protected onEnter(event: Event): void {
    const keyboard = event as KeyboardEvent;
    if (keyboard.shiftKey || keyboard.isComposing) {
      return;
    }
    event.preventDefault();
    if (!this.facade.busy() && this.draft.trim()) {
      void this.send();
    }
  }
}
