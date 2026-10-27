import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';
import { ASSISTANT_I18N_SCOPE } from '../../base-ai.i18n';
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
  imports: [FormsModule, MatButton, TranslocoPipe],
  providers: [AssistantPanelFacade],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="pp-assistant" [attr.aria-label]="scope + '.title' | transloco">
      <h3 class="pp-assistant__title">{{ scope + '.title' | transloco }}</h3>

      <ol class="pp-assistant__lines" aria-live="polite">
        @for (line of facade.lines(); track $index) {
          <li class="pp-assistant__line pp-assistant__line--{{ line.role }}">
            <span class="pp-assistant__speaker">{{ scope + '.' + line.role | transloco }}</span>
            <p>{{ line.text }}</p>
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
}
