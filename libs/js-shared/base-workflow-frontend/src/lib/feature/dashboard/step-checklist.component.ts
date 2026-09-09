import { ChangeDetectionStrategy, Component, computed, input, linkedSignal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TASK_DASHBOARD_I18N_SCOPE } from '../../base-workflow.i18n';
import { StepDefinition, TaskStepType } from '../../domain/definition/task-definition';
import { StepResult } from '../../domain/execution/workflow-instance';

/**
 * How to carry out the open task: its `TaskDefinition.steps`, joined with the `StepResult`s the run has
 * recorded so far.
 *
 * The two step types are rendered differently because they *are* different, and collapsing them would
 * misstate who does the work:
 *
 * - **`USER_STEP`** — guidance the engine does not enforce. A checkbox and its description. The checkbox is
 *   **client-side only**: the contract has no endpoint to mark one step done, so ticking it is a progress
 *   aid that lives until the task is switched, not something that round-trips. Whether it should persist is
 *   open (open-questions #5); nothing here depends on the answer.
 * - **`SERVICE_STEP`** — a call the engine makes through the step's tool. A passive status row, not a
 *   checkbox, because the user does not do it. It shows what happened: waiting, done, or failed with
 *   `StepResult.error` spelled out, which is the one thing on this screen a user cannot work around and
 *   therefore has to be able to read.
 *
 * A tool call that failed is shown as prominently as the contract allows and no more: `toolResponse` is an
 * open map, so it is offered as the raw text it is rather than parsed into a summary that would be wrong for
 * the next tool.
 */
@Component({
  selector: 'pp-step-checklist',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (steps().length === 0) {
      <p class="steps__empty" data-testid="steps-empty">{{ emptyKey | transloco }}</p>
    } @else {
      <ol class="steps">
        @for (step of steps(); track step.id; let index = $index) {
          <li class="step" [class.step--service]="isService(step)" [attr.data-testid]="'step-' + step.id">
            <span class="step__marker">
              @if (isService(step)) {
                <span class="step__icon" [attr.data-state]="serviceState(step.id)" aria-hidden="true">{{ serviceGlyph(step.id) }}</span>
              } @else {
                <input
                  type="checkbox"
                  [checked]="checkedIds().has(step.id)"
                  [attr.data-testid]="'step-check-' + step.id"
                  [attr.aria-label]="step.name"
                  [disabled]="readOnly()"
                  (change)="toggle(step.id)"
                />
              }
            </span>
            <span class="step__body">
              <span class="step__title">
                <span class="step__name" [class.step__name--done]="!isService(step) && checkedIds().has(step.id)">{{ step.name || index + 1 }}</span>
                <span class="step__type">{{ (isService(step) ? serviceKey : userKey) | transloco }}</span>
              </span>
              @if (step.description) {
                <span class="step__description">{{ step.description }}</span>
              }
              @if (resultFor(step.id); as result) {
                @if (result.error) {
                  <span class="step__error" role="alert" [attr.data-testid]="'step-error-' + step.id">{{ result.error }}</span>
                } @else if (result.toolResponse; as response) {
                  <span class="step__response" [attr.data-testid]="'step-response-' + step.id">{{ responseText(response) }}</span>
                }
              }
            </span>
          </li>
        }
      </ol>
    }
  `,
  styles: `
    .steps {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .step {
      display: flex;
      align-items: flex-start;
      gap: 8px;
    }
    .step__marker {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 18px;
      padding-top: 1px;
    }
    /* A glyph rather than an <img>: the three service-step outcomes are states, not modelled elements, so
       they have no symbol in assets/modeler and inventing three would be three more files to keep. */
    .step__icon {
      font-size: 13px;
      line-height: 1;
    }
    .step__icon[data-state='waiting'] {
      color: #999999;
    }
    .step__icon[data-state='done'] {
      color: #1b7f5f;
    }
    .step__icon[data-state='failed'] {
      color: #b3261e;
    }
    .step__body {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;
    }
    .step__title {
      display: flex;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 8px;
    }
    .step__name {
      font-size: 13px;
    }
    .step__name--done {
      color: #888888;
      text-decoration: line-through;
    }
    .step__type {
      font-size: 11px;
      color: #999999;
    }
    .step__description {
      font-size: 12px;
      color: #666666;
    }
    .step__error {
      font-size: 12px;
      color: #b3261e;
    }
    .step__response {
      font-size: 11px;
      color: #777777;
      overflow-wrap: anywhere;
    }
    .steps__empty {
      margin: 0;
      font-size: 12px;
      color: #999999;
    }
  `,
})
export class StepChecklistComponent {
  readonly steps = input.required<StepDefinition[]>();
  readonly stepResults = input<StepResult[]>([]);

  /** True while the task is not the session's to work on — the checkboxes are then a read-only preview. */
  readonly readOnly = input<boolean>(false);

  protected readonly userKey = `${TASK_DASHBOARD_I18N_SCOPE}.step_user`;
  protected readonly serviceKey = `${TASK_DASHBOARD_I18N_SCOPE}.step_service`;
  protected readonly emptyKey = `${TASK_DASHBOARD_I18N_SCOPE}.steps_none`;

  /**
   * Which user steps have been ticked, locally.
   *
   * `linkedSignal` rather than `signal`, for the "derived default, then independently writable" shape: the
   * set is recomputed from `steps()`, so selecting a different task clears it, and writable in between so a
   * tick sticks. A plain signal would carry one task's ticks onto the next task's steps.
   */
  protected readonly checkedIds = linkedSignal<StepDefinition[], Set<string>>({
    source: this.steps,
    computation: () => new Set<string>(),
  });

  private readonly resultsByStepId = computed(() => new Map(this.stepResults().map((result) => [result.stepId, result])));

  protected isService(step: StepDefinition): boolean {
    return step.stepType === TaskStepType.SERVICE_STEP;
  }

  protected resultFor(stepId: string): StepResult | undefined {
    return this.resultsByStepId().get(stepId);
  }

  /** Waiting, done or failed — a `StepResult` exists only once the engine has called the tool. */
  protected serviceState(stepId: string): 'waiting' | 'done' | 'failed' {
    const result = this.resultFor(stepId);
    if (!result) return 'waiting';
    return result.error ? 'failed' : 'done';
  }

  protected serviceGlyph(stepId: string): string {
    return SERVICE_GLYPHS[this.serviceState(stepId)];
  }

  /** The tool's answer as the open map it is — see the class comment on why it is not summarized. */
  protected responseText(response: Record<string, string>): string {
    return Object.entries(response)
      .map(([key, value]) => `${key}: ${value}`)
      .join(' · ');
  }

  protected toggle(stepId: string): void {
    if (this.readOnly()) return;
    const next = new Set(this.checkedIds());
    if (next.has(stepId)) next.delete(stepId);
    else next.add(stepId);
    this.checkedIds.set(next);
  }
}

const SERVICE_GLYPHS: Record<'waiting' | 'done' | 'failed', string> = { waiting: '○', done: '●', failed: '▲' };
