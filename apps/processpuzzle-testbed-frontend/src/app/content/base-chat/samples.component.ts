import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { ChatPanelComponent, ChatSubjectType, ChatTopicsComponent } from '@processpuzzle/base-chat';
import { ChatDefinitionOption, ChatSampleOptions, RecordOption } from './chat-sample-options';

/**
 * The `chat-panel` on a record the visitor names. The chat definition is picked from the ones there are; when it is
 * about an entity type, the record field offers that type's records — the id, and the attribute that titles the
 * record beside it — and still takes an id typed or pasted. With the record left empty the panel follows the active
 * working context instead, which is how it behaves when a designer places it on an order screen.
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
            {{ t('definition_label') }}
            @if (definitions().length > 0) {
              <select data-testid="chat-sample-definition" [value]="definitionKey()" (change)="pickDefinition($any($event.target).value)">
                @for (definition of definitions(); track definition.key) {
                  <option [value]="definition.key" [selected]="definition.key === definitionKey()">{{ definition.name }} ({{ definition.key }})</option>
                }
              </select>
            } @else {
              <input data-testid="chat-sample-definition" [value]="definitionKey()" (input)="definitionKey.set($any($event.target).value.trim())" />
            }
          </label>
          <label>
            {{ t('record_label') }}
            <input data-testid="chat-sample-record" list="chat-sample-records" [value]="subjectId()" (input)="subjectId.set($any($event.target).value.trim())" />
            <datalist id="chat-sample-records">
              @for (record of records(); track record.id) {
                <option [value]="record.id" [attr.label]="optionLabel(record)">{{ optionLabel(record) }}</option>
              }
            </datalist>
          </label>
        </div>
        <pp-chat-panel class="panel" [subjectId]="subjectId() || undefined" [subjectType]="subjectType()" [definitionKey]="definitionKey() || undefined" />
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
    .fields input,
    .fields select {
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
  private readonly options = inject(ChatSampleOptions);

  protected readonly subjectId = signal('');
  protected readonly definitionKey = signal('order-discussion');
  protected readonly definitions = signal<ChatDefinitionOption[]>([]);
  protected readonly records = signal<RecordOption[]>([]);

  private readonly definition = computed(() => this.definitions().find((candidate) => candidate.key === this.definitionKey()));
  /** What the record is, as the picked definition says; an entity instance while that is not known. */
  protected readonly subjectType = computed<ChatSubjectType | undefined>(() => (this.definition()?.subjectType === 'WORKFLOW_INSTANCE' ? 'WORKFLOW_INSTANCE' : undefined));

  private recordSerial = 0;

  constructor() {
    void this.options.definitions().then((found) => this.definitions.set(found));
    effect(() => {
      const definition = this.definition();
      untracked(() => void this.loadRecords(definition));
    });
  }

  protected pickDefinition(key: string): void {
    this.definitionKey.set(key);
    // The record belonged to the previous definition's subject type.
    this.subjectId.set('');
  }

  protected optionLabel(record: RecordOption): string {
    return record.label ? `${record.label} — ${record.id}` : record.id;
  }

  private async loadRecords(definition: ChatDefinitionOption | undefined): Promise<void> {
    const serial = ++this.recordSerial;
    if (definition?.subjectType !== 'ENTITY') {
      this.records.set([]);
      return;
    }
    const found = await this.options.records(definition.subjectDefinition);
    if (serial === this.recordSerial) this.records.set(found);
  }
}
