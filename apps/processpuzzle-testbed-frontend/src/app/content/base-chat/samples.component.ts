import { Component, signal } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { ChatPanelComponent, ChatTopicsComponent } from '@processpuzzle/base-chat';

/**
 * The `chat-panel` on a record the visitor names: paste the id of an order — from the address of its form — and talk
 * about it. With the field left empty the panel follows the active working context instead, which is how it behaves
 * when a designer places it on an order screen.
 */
@Component({
  selector: 'base-chat-samples',
  standalone: true,
  imports: [TranslocoDirective, ChatPanelComponent, ChatTopicsComponent],
  template: `
    <ng-container *transloco="let t; prefix: 'base-chat'">
      <section class="samples">
        <p>{{ t('samples_desc_1') }}</p>
        <p>{{ t('samples_desc_2') }}</p>
        <div class="fields">
          <label>
            {{ t('record_label') }}
            <input data-testid="chat-sample-record" [value]="subjectId()" (input)="subjectId.set($any($event.target).value.trim())" />
          </label>
          <label>
            {{ t('definition_label') }}
            <input data-testid="chat-sample-definition" [value]="definitionKey()" (input)="definitionKey.set($any($event.target).value.trim())" />
          </label>
        </div>
        <pp-chat-panel class="panel" [subjectId]="subjectId() || undefined" [definitionKey]="definitionKey() || undefined" />
        <h3>{{ t('topics_heading') }}</h3>
        <p>{{ t('topics_desc') }}</p>
        <pp-chat-topics class="panel" [subjectId]="subjectId() || undefined" definitionKey="order-topics" />
      </section>
    </ng-container>
  `,
  styles: `
    .samples {
      padding: 16px;
      max-width: 900px;
    }
    .fields {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin: 12px 0;
    }
    .fields label {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .fields input {
      min-width: 22rem;
      padding: 6px 8px;
      font: inherit;
    }
    .panel {
      max-width: 40rem;
    }
  `,
})
export class SamplesComponent {
  protected readonly subjectId = signal('');
  protected readonly definitionKey = signal('order-discussion');
}
