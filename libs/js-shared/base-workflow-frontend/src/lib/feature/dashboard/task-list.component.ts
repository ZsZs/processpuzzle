import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TASK_DASHBOARD_I18N_SCOPE } from '../../base-workflow.i18n';
import { DashboardTask } from '../../domain/dashboard/dashboard-task';
import { WorkflowDashboardStore } from '../../domain/dashboard/workflow-dashboard.store';
import { TaskInstanceStatus } from '../../domain/execution/workflow-instance';
import { TaskStatusBadgeComponent } from './task-status-badge.component';

/**
 * The queue: the rows of whichever scope is showing, and the two things a row can do — open itself, or be
 * claimed.
 *
 * Each row says what the design's screen 1 asked it to: the task's name, its status, the run it belongs to,
 * and — when `BLOCKED` — the `blockedReason` inline. That last one is the cheapest useful field on the
 * screen: it is already on the object, so a blocked task explains itself with no second call, and putting it
 * behind a hover would hide the only thing the user needs.
 *
 * **Claim sits on the row, not only in the detail pane.** The Team queue is a list of things to pick up, and
 * making the user open each one to take it turns one gesture into three.
 *
 * Two sibling buttons rather than a claim control nested in the row's button: interactive content inside a
 * `<button>` is invalid, and it is what forces the `stopPropagation` dance that makes "claim" occasionally
 * also mean "open". As siblings each one does one thing.
 */
@Component({
  selector: 'pp-task-list',
  standalone: true,
  imports: [TranslocoPipe, TaskStatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="list" data-testid="task-list">
      @if (store.isLoading() && rows().length === 0) {
        <p class="list__status" data-testid="task-list-loading">…</p>
      } @else if (store.loadError()) {
        <p class="list__status list__status--error" role="alert" data-testid="task-list-error">{{ errorKey | transloco }}</p>
      } @else {
        @for (row of rows(); track row.instanceId + '/' + row.task.id) {
          <div class="row" [class.row--selected]="isSelected(row)">
            <button
              type="button"
              class="row__open"
              [attr.aria-current]="isSelected(row) ? 'true' : null"
              [attr.data-testid]="'task-row-' + row.task.id"
              (click)="store.select(row.instanceId, row.task.id)"
            >
              <span class="row__head">
                <span class="row__name">{{ row.task.name || row.taskDefinitionId }}</span>
                <pp-task-status-badge [status]="row.task.status" />
              </span>
              <span class="row__workflow">{{ row.workflowName }}</span>
              @if (isBlocked(row) && row.task.blockedReason) {
                <span class="row__blocked" [attr.data-testid]="'task-blocked-' + row.task.id">{{ row.task.blockedReason }}</span>
              }
            </button>
            @if (isClaimable(row)) {
              <button type="button" class="row__claim" [disabled]="store.isActing()" [attr.data-testid]="'claim-task-' + row.task.id" (click)="claim(row)">
                {{ claimKey | transloco }}
              </button>
            }
          </div>
        } @empty {
          <p class="list__status" data-testid="task-list-empty">{{ emptyKey() | transloco }}</p>
        }
      }
    </div>
  `,
  styles: `
    .list {
      display: flex;
      flex-direction: column;
      gap: 4px;
      min-width: 0;
    }
    .row {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      padding: 4px;
      border: 1px solid transparent;
      border-radius: 4px;
      background-color: #ffffff;
    }
    .row:hover {
      background-color: #f5f7fa;
    }
    .row--selected {
      border-color: var(--pp-color-dark-blue, rgb(24, 111, 206));
      background-color: #f0f5fd;
    }
    .row__open {
      display: flex;
      flex-direction: column;
      align-items: stretch;
      gap: 2px;
      width: 100%;
      padding: 4px 6px;
      border: 0;
      background: none;
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .row__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    .row__name {
      font-size: 13px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .row__workflow {
      font-size: 11px;
      color: #888888;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .row__blocked {
      font-size: 11px;
      color: #b3261e;
    }
    .row__claim {
      margin: 2px 0 4px 6px;
      padding: 2px 10px;
      border: 1px solid var(--pp-color-dark-blue, rgb(24, 111, 206));
      border-radius: 4px;
      background-color: var(--pp-button-secondary-bg, #ffffff);
      color: var(--pp-button-secondary-text, rgb(24, 111, 206));
      font: inherit;
      font-size: 11px;
      cursor: pointer;
    }
    .row__claim:disabled {
      opacity: 0.5;
      cursor: default;
    }
    .list__status {
      margin: 0;
      padding: 8px 10px;
      font-size: 12px;
      color: #999999;
    }
    .list__status--error {
      color: #b3261e;
    }
  `,
})
export class TaskListComponent {
  protected readonly store = inject(WorkflowDashboardStore);

  protected readonly claimKey = `${TASK_DASHBOARD_I18N_SCOPE}.claim`;
  protected readonly errorKey = `${TASK_DASHBOARD_I18N_SCOPE}.error`;

  protected readonly rows = this.store.visibleTasks;

  /** A different sentence per scope: "nothing assigned to you" and "nothing to claim" are different facts. */
  protected readonly emptyKey = computed(() => `${TASK_DASHBOARD_I18N_SCOPE}.empty.${this.store.scope()}`);

  protected isSelected(row: DashboardTask): boolean {
    return this.store.selectedInstanceId() === row.instanceId && this.store.selectedTaskId() === row.task.id;
  }

  protected isBlocked(row: DashboardTask): boolean {
    return row.task.status === TaskInstanceStatus.BLOCKED;
  }

  /** The unassigned active rows. The Team queue is already only those; the other two scopes may hold one. */
  protected isClaimable(row: DashboardTask): boolean {
    return row.task.status === TaskInstanceStatus.ACTIVE && !row.task.assignedTo;
  }

  // The whole row, which already carries the `taskDefinitionId` the verbs address a task by — see
  // `ActionTarget`.
  protected claim(row: DashboardTask): void {
    void this.store.claim(row);
  }
}
