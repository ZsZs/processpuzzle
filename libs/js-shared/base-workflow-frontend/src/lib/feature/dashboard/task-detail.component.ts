import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TASK_DASHBOARD_I18N_SCOPE } from '../../base-workflow.i18n';
import { WorkflowDashboardStore } from '../../domain/dashboard/workflow-dashboard.store';
import { TaskInstanceStatus } from '../../domain/execution/workflow-instance';
import { PropertyMap } from '../../domain/property-map';
import { ArtifactPanelComponent } from './artifact-panel.component';
import { CompletionFormComponent } from './completion-form.component';
import { StepChecklistComponent } from './step-checklist.component';
import { TaskStatusBadgeComponent } from './task-status-badge.component';

/**
 * The task workspace: the three stacked sections of the design's screen 2 — how to do the task, what it
 * touches, and how to finish it — plus the one state that precedes all three.
 *
 * **A task nobody holds is locked.** Until it is claimed the pane shows the task's name and a Claim button
 * and nothing else: the steps and the completion form are for whoever owns the work, and offering a form
 * whose submission the backend would refuse is worse than offering nothing. Once it is claimed by someone
 * else the sections are shown read-only rather than hidden, because seeing what a colleague is doing is not
 * the same as being invited to do it.
 *
 * The completion form appears only for an `ACTIVE` task that is the session's own. A `PENDING` task has not
 * been activated by the engine, and a `COMPLETED` or `SKIPPED` one is finished — in both cases `complete`
 * answers `409`, so the button is absent rather than present and doomed.
 */
@Component({
  selector: 'pp-task-detail',
  standalone: true,
  imports: [TranslocoPipe, TaskStatusBadgeComponent, StepChecklistComponent, ArtifactPanelComponent, CompletionFormComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="detail" data-testid="task-detail">
      @if (store.selectedRow(); as row) {
        <header class="detail__header">
          <h4 class="detail__name" data-testid="task-detail-name">{{ row.task.name || row.taskDefinitionId }}</h4>
          <pp-task-status-badge [status]="row.task.status" />
        </header>
        <p class="detail__meta">
          <span data-testid="task-detail-workflow">{{ row.workflowName }}</span>
          @if (row.task.assignedTo) {
            <span data-testid="task-detail-assignee">{{ assignedToKey | transloco }}: {{ row.task.assignedTo }}</span>
          } @else {
            <span data-testid="task-detail-unassigned">{{ unassignedKey | transloco }}</span>
          }
        </p>

        @if (store.actionError(); as error) {
          <p class="detail__error" role="alert" data-testid="task-detail-error">{{ error }}</p>
        }

        @if (!row.task.assignedTo) {
          <div class="detail__locked" data-testid="task-detail-locked">
            <p class="detail__locked-text">{{ lockedKey | transloco }}</p>
            <button type="button" class="detail__claim" [disabled]="store.isActing()" data-testid="task-detail-claim" (click)="claim()">
              {{ claimKey | transloco }}
            </button>
          </div>
        } @else {
          <section class="detail__section">
            <h5 class="detail__section-heading">{{ stepsKey | transloco }}</h5>
            <pp-step-checklist [steps]="store.selectedDefinition()?.steps ?? []" [stepResults]="row.task.stepResults" [readOnly]="!store.isMine()" />
          </section>

          <section class="detail__section">
            <pp-artifact-panel [inputs]="store.selectedInputs()" [outputs]="store.selectedOutputs()" />
          </section>

          @if (canComplete()) {
            <section class="detail__section">
              <pp-completion-form
                [isBusy]="store.isActing()"
                [canSkip]="store.canSkip()"
                [postconditionDetail]="store.postconditionDetail()"
                (completeRequested)="complete($event)"
                (skipRequested)="skip($event)"
              />
            </section>
          }
        }
      } @else {
        <p class="detail__empty" data-testid="task-detail-empty">{{ selectPromptKey | transloco }}</p>
      }
    </div>
  `,
  styles: `
    .detail {
      display: flex;
      flex-direction: column;
      gap: 12px;
      min-width: 0;
    }
    .detail__header {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .detail__name {
      margin: 0;
      font-size: 15px;
    }
    /* The run and the assignee on one muted line: context for the sections below, not content of its own. */
    .detail__meta {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 16px;
      margin: 0;
      font-size: 12px;
      color: #777777;
    }
    .detail__section {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding-top: 10px;
      border-top: 1px solid #eeeeee;
    }
    .detail__section-heading {
      margin: 0;
      font-size: 12px;
      font-weight: 600;
      color: #666666;
    }
    .detail__locked {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 8px;
      padding: 12px;
      border: 1px dashed #cccccc;
      border-radius: 4px;
      background-color: #fafafa;
    }
    .detail__locked-text {
      margin: 0;
      font-size: 12px;
      color: #666666;
    }
    .detail__claim {
      padding: 5px 14px;
      border: 1px solid var(--pp-color-dark-blue, rgb(24, 111, 206));
      border-radius: 4px;
      background-color: var(--pp-button-primary-bg, rgb(24, 111, 206));
      color: var(--pp-button-primary-text, #eeeeee);
      font: inherit;
      font-size: 13px;
      cursor: pointer;
    }
    .detail__claim:disabled {
      opacity: 0.5;
      cursor: default;
    }
    .detail__error {
      margin: 0;
      padding: 6px 10px;
      border-left: 3px solid #b3261e;
      background-color: #fdecea;
      color: #7d1912;
      font-size: 12px;
    }
    .detail__empty {
      margin: 0;
      font-size: 12px;
      color: #999999;
    }
  `,
})
export class TaskDetailComponent {
  protected readonly store = inject(WorkflowDashboardStore);

  protected readonly stepsKey = `${TASK_DASHBOARD_I18N_SCOPE}.steps`;
  protected readonly claimKey = `${TASK_DASHBOARD_I18N_SCOPE}.claim`;
  protected readonly lockedKey = `${TASK_DASHBOARD_I18N_SCOPE}.locked`;
  protected readonly selectPromptKey = `${TASK_DASHBOARD_I18N_SCOPE}.select_prompt`;
  protected readonly assignedToKey = `${TASK_DASHBOARD_I18N_SCOPE}.assigned_to`;
  protected readonly unassignedKey = `${TASK_DASHBOARD_I18N_SCOPE}.unassigned`;

  /** Only an `ACTIVE` task the session holds can be completed — see the class comment. */
  protected canComplete(): boolean {
    return this.store.isMine() && this.store.selectedTask()?.status === TaskInstanceStatus.ACTIVE;
  }

  // Each verb is handed the open row itself, which satisfies `ActionTarget` — the store then addresses
  // the task by its `taskDefinitionId`, which is what the endpoints resolve it through.
  protected claim(): void {
    const row = this.store.selectedRow();
    if (row) void this.store.claim(row);
  }

  protected complete(context: PropertyMap | undefined): void {
    const row = this.store.selectedRow();
    if (row) void this.store.complete(row, context);
  }

  protected skip(reason: string | undefined): void {
    const row = this.store.selectedRow();
    if (row) void this.store.skip(row, reason);
  }
}
