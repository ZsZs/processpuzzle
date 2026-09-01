import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TASK_DASHBOARD_I18N_SCOPE } from '../../base-workflow.i18n';
import { UNSTATED_REFUSAL } from '../../domain/dashboard/workflow-dashboard.store';
import { PropertyMap } from '../../domain/property-map';

/** One row of the context editor, before it becomes a key/value pair. */
interface ContextRow {
  key: string;
  value: string;
}

/**
 * How the open task is finished: the context it contributes, and the two verbs that end it.
 *
 * **The context is a key/value editor rather than a free-text note**, and that is a correction of the
 * original sketch rather than a shortcut. `CompleteTaskRequest.context` is merged into the workflow context
 * before the postcondition rule runs, and a rule reads *named* variables — `reviewScore`, `approved`. A
 * single `note` field would produce one key no rule ever references, which looks like a working form and
 * contributes nothing. Named pairs are the smallest thing that can actually satisfy a postcondition, and
 * they are exactly the shape the contract declares (`additionalProperties`), so nothing is invented here.
 *
 * It is still **generic**, which is the open question this cannot close: `TaskDefinition` does not declare
 * what a task's completion is expected to contribute, so the dashboard cannot label the fields or validate
 * them, and the user has to know the key names. Fixing that is a contract change — a declarative field on
 * the task or its steps naming the expected keys and their types (open-questions #1) — after which this
 * component renders from that metadata instead of from blank rows. Worth doing before the number of task
 * types grows, or the alternative is one bespoke form per task.
 *
 * **A refused completion is rendered inline, not as a toast.** `accepted: false` comes back 200 with the
 * task still `ACTIVE`: the user has to change something and resubmit, so the reason belongs beside the
 * fields they are about to change and has to survive being read twice.
 *
 * **Skip is not a peer of Complete.** The contract calls it a manager override, so it is behind
 * {@link CompletionFormComponent.canSkip} and asks for a reason — an override with no record of why is the
 * kind of thing that is only ever discovered later.
 */
@Component({
  selector: 'pp-completion-form',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="form">
      @if (postconditionDetail(); as detail) {
        <p class="form__refusal" role="alert" data-testid="completion-refusal">
          {{ detail === unstatedRefusal ? (unstatedKey | transloco) : detail }}
        </p>
      }

      <fieldset class="form__context">
        <legend class="form__legend">{{ noteKey | transloco }}</legend>
        @for (row of rows(); track $index; let index = $index) {
          <div class="form__row">
            <input
              class="form__key"
              type="text"
              [value]="row.key"
              [disabled]="isBusy()"
              [attr.data-testid]="'context-key-' + index"
              [attr.aria-label]="(noteKey | transloco) + ' — key ' + (index + 1)"
              (input)="setKey(index, $event)"
            />
            <input
              class="form__value"
              type="text"
              [value]="row.value"
              [disabled]="isBusy()"
              [attr.data-testid]="'context-value-' + index"
              [attr.aria-label]="(noteKey | transloco) + ' — value ' + (index + 1)"
              (input)="setValue(index, $event)"
            />
          </div>
        }
      </fieldset>

      <div class="form__actions">
        @if (canSkip()) {
          <input
            class="form__reason"
            type="text"
            [value]="skipReason()"
            [disabled]="isBusy()"
            [placeholder]="skipReasonKey | transloco"
            [attr.aria-label]="skipReasonKey | transloco"
            data-testid="skip-reason"
            (input)="setSkipReason($event)"
          />
          <button type="button" class="form__button" [disabled]="isBusy()" data-testid="skip-task" (click)="requestSkip()">
            {{ skipKey | transloco }}
          </button>
        }
        <button type="button" class="form__button form__button--primary" [disabled]="isBusy()" data-testid="complete-task" (click)="requestComplete()">
          {{ completeKey | transloco }}
        </button>
      </div>
    </div>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .form__refusal {
      margin: 0;
      padding: 6px 10px;
      border-left: 3px solid #b3261e;
      background-color: #fdecea;
      color: #7d1912;
      font-size: 12px;
    }
    .form__context {
      display: flex;
      flex-direction: column;
      gap: 6px;
      margin: 0;
      padding: 0;
      border: 0;
    }
    .form__legend {
      padding: 0;
      font-size: 12px;
      color: #666666;
    }
    /* Key narrower than value: a context key is an identifier, a value can be a sentence. */
    .form__row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
      gap: 6px;
    }
    .form__key,
    .form__value,
    .form__reason {
      min-width: 0;
      padding: 4px 6px;
      border: 1px solid #cccccc;
      border-radius: 4px;
      font: inherit;
      font-size: 12px;
    }
    .form__actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
    }
    .form__reason {
      flex: 1 1 160px;
    }
    /* Plain buttons, styled through the brand tokens when the host loaded them: this library declares no
       @angular/material peer dependency, the same reason the modeler saves with a bare <button>. */
    .form__button {
      padding: 5px 14px;
      border: 1px solid var(--pp-color-dark-blue, rgb(24, 111, 206));
      border-radius: 4px;
      background-color: var(--pp-button-secondary-bg, #ffffff);
      color: var(--pp-button-secondary-text, rgb(24, 111, 206));
      font: inherit;
      font-size: 13px;
      cursor: pointer;
    }
    .form__button--primary {
      background-color: var(--pp-button-primary-bg, rgb(24, 111, 206));
      color: var(--pp-button-primary-text, #eeeeee);
    }
    .form__button:disabled {
      opacity: 0.5;
      cursor: default;
    }
  `,
})
export class CompletionFormComponent {
  /** Whether a verb is in flight — both buttons and every field are disabled while one is. */
  readonly isBusy = input<boolean>(false);

  /** Whether this session may override — see the class comment on why Skip is gated. */
  readonly canSkip = input<boolean>(false);

  /**
   * Why the last attempt was refused, or {@link UNSTATED_REFUSAL} when the server refused without saying.
   * Cleared by the host when a different task is selected.
   */
  readonly postconditionDetail = input<string | undefined>(undefined);

  /** The context pairs the user filled in, empty rows dropped. */
  readonly completeRequested = output<PropertyMap | undefined>();
  readonly skipRequested = output<string | undefined>();

  protected readonly noteKey = `${TASK_DASHBOARD_I18N_SCOPE}.note`;
  protected readonly completeKey = `${TASK_DASHBOARD_I18N_SCOPE}.complete`;
  protected readonly skipKey = `${TASK_DASHBOARD_I18N_SCOPE}.skip`;
  protected readonly skipReasonKey = `${TASK_DASHBOARD_I18N_SCOPE}.skip_reason`;
  protected readonly unstatedKey = `${TASK_DASHBOARD_I18N_SCOPE}.unstated_refusal`;
  protected readonly unstatedRefusal = UNSTATED_REFUSAL;

  private readonly editedRows = signal<ContextRow[]>([{ key: '', value: '' }]);
  protected readonly skipReason = signal<string>('');

  /**
   * The rows to render: whatever has been typed, plus one blank row at the end.
   *
   * A trailing blank rather than an "Add" button, so contributing a second pair costs no click and the
   * common case — one pair, or none — shows exactly one row.
   */
  protected readonly rows = computed<ContextRow[]>(() => {
    const rows = this.editedRows();
    const last = rows[rows.length - 1];
    return last && (last.key !== '' || last.value !== '') ? [...rows, { key: '', value: '' }] : rows;
  });

  protected setKey(index: number, event: Event): void {
    this.patchRow(index, { key: inputValue(event) });
  }

  protected setValue(index: number, event: Event): void {
    this.patchRow(index, { value: inputValue(event) });
  }

  protected setSkipReason(event: Event): void {
    this.skipReason.set(inputValue(event));
  }

  protected requestComplete(): void {
    this.completeRequested.emit(this.context());
  }

  protected requestSkip(): void {
    const reason = this.skipReason().trim();
    this.skipRequested.emit(reason || undefined);
  }

  /**
   * The pairs, with unnamed rows dropped and `undefined` rather than `{}` when nothing was filled in — a
   * task whose postcondition needs no input is completed with no body at all rather than with an empty map,
   * which would be a merge of nothing into the workflow context.
   */
  private context(): PropertyMap | undefined {
    const entries = this.rows()
      .map((row) => [row.key.trim(), row.value] as const)
      .filter(([key]) => key !== '');
    return entries.length > 0 ? Object.fromEntries(entries) : undefined;
  }

  private patchRow(index: number, patch: Partial<ContextRow>): void {
    const rows = [...this.rows()];
    rows[index] = { ...rows[index], ...patch };
    this.editedRows.set(rows);
  }
}

function inputValue(event: Event): string {
  return (event.target as HTMLInputElement).value;
}
