import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TASK_DASHBOARD_I18N_SCOPE } from '../../base-workflow.i18n';
import { DashboardTask } from '../../domain/dashboard/dashboard-task';
import { WorkflowDashboardStore } from '../../domain/dashboard/workflow-dashboard.store';
import { TaskInstanceStatus } from '../../domain/execution/workflow-instance';
import { TaskStatusBadgeComponent } from './task-status-badge.component';

/**
 * One run, grouped by status: the process owner's view rather than a single performer's.
 *
 * A header strip naming the run, then four columns — `PENDING`, `ACTIVE`, `BLOCKED`, `COMPLETED` — with
 * `SKIPPED` folded into the last of them. One endpoint, no cross-referencing: the whole board comes out of
 * the instance the queue already loaded, which is what makes it the cheapest useful overview.
 *
 * **What this deliberately does not draw is sequence.** It shows *what* is stuck, not *where* in the flow it
 * sits relative to its siblings, or whether a `parallel` group is half finished. That is structural
 * information and the modeler already draws it from `WorkflowTaskAssignment.dependsOn` / `joinType` /
 * `parallel`. A second graph view here would be two layouts of one model to keep in step, and the better
 * move if the board turns out to be insufficient is to layer live status onto the modeler's existing nodes —
 * not to grow a diagram in this component.
 *
 * Column headings carry the raw status for the same reason the badges do: nothing in this workspace
 * translates an enum value, and a translated heading above untranslated cards would read as two fields.
 */
@Component({
  selector: 'pp-process-board',
  standalone: true,
  imports: [DatePipe, TranslocoPipe, TaskStatusBadgeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.selectedInstance(); as instance) {
      <div class="board" data-testid="process-board">
        <header class="board__header">
          <div>
            <div class="board__title" data-testid="board-workflow">{{ instance.workflowName || instance.workflowId }}</div>
            <div class="board__subtitle">
              <span data-testid="board-entity">{{ instance.entityId || '—' }}</span>
              @if (instance.startedAt) {
                <span data-testid="board-started">{{ instance.startedAt | date: 'short' }}</span>
              }
            </div>
          </div>
          <span class="board__status" [attr.data-testid]="'board-status-' + (instance.status ?? 'unknown')">{{ instance.status }}</span>
        </header>

        <div class="board__columns">
          @for (column of store.columns(); track column.status) {
            <section class="column" [attr.data-testid]="'board-column-' + column.status">
              <h5 class="column__heading">
                {{ column.status }}
                <span class="column__count">{{ column.rows.length }}</span>
              </h5>
              @for (row of column.rows; track row.task.id) {
                <!-- A card opens the task in the pane below, so the board is a way in rather than a
                     read-only report: the owner who spots the blocked task is usually the one acting on it. -->
                <button type="button" class="card" [class.card--selected]="isSelected(row)" [attr.data-testid]="'board-card-' + row.task.id" (click)="store.select(row.instanceId, row.task.id)">
                  <span class="card__name">{{ row.task.name || row.taskDefinitionId }}</span>
                  @switch (row.task.status) {
                    @case (blocked) {
                      <span class="card__detail card__detail--danger">{{ row.task.blockedReason || '—' }}</span>
                    }
                    @case (completed) {
                      <span class="card__detail">{{ row.task.completedAt ? (row.task.completedAt | date: 'short') : '—' }}</span>
                    }
                    @case (skipped) {
                      <!-- The one status with no column of its own, so the card says which it is. -->
                      <pp-task-status-badge [status]="row.task.status" />
                    }
                    @default {
                      <span class="card__detail">{{ row.task.assignedTo || (unassignedKey | transloco) }}</span>
                    }
                  }
                </button>
              } @empty {
                <span class="column__empty">—</span>
              }
            </section>
          }
        </div>
      </div>
    } @else {
      <p class="board__prompt" data-testid="board-no-run">{{ noRunKey | transloco }}</p>
    }
  `,
  styles: `
    .board {
      display: flex;
      flex-direction: column;
      gap: 10px;
      min-width: 0;
    }
    .board__header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
    }
    .board__title {
      font-size: 14px;
      font-weight: 600;
    }
    .board__subtitle {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 16px;
      font-size: 12px;
      color: #888888;
    }
    .board__status {
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.04em;
      color: #555555;
    }
    /* Four equal columns that wrap rather than scroll: the board sits above the task detail, so a narrow
       viewport should stack the columns instead of hiding two of them off-screen. */
    .board__columns {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
      gap: 8px;
      align-items: start;
    }
    .column {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 8px;
      border-radius: 4px;
      background-color: #f6f7f9;
    }
    .column__heading {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 6px;
      margin: 0;
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.04em;
      color: #666666;
    }
    .column__count {
      color: #999999;
    }
    .column__empty {
      font-size: 12px;
      color: #bbbbbb;
    }
    .card {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 2px;
      width: 100%;
      padding: 6px 8px;
      border: 1px solid transparent;
      border-radius: 4px;
      background-color: #ffffff;
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .card--selected {
      border-color: var(--pp-color-dark-blue, rgb(24, 111, 206));
    }
    .card__name {
      font-size: 12px;
      overflow-wrap: anywhere;
    }
    .card__detail {
      font-size: 11px;
      color: #888888;
      overflow-wrap: anywhere;
    }
    .card__detail--danger {
      color: #b3261e;
    }
    .board__prompt {
      margin: 0;
      font-size: 12px;
      color: #999999;
    }
  `,
})
export class ProcessBoardComponent {
  protected readonly store = inject(WorkflowDashboardStore);

  protected readonly unassignedKey = `${TASK_DASHBOARD_I18N_SCOPE}.unassigned`;
  protected readonly noRunKey = `${TASK_DASHBOARD_I18N_SCOPE}.run_none`;

  // `@switch` compares against values, so the enum members are fields rather than reachable as a type.
  protected readonly blocked = TaskInstanceStatus.BLOCKED;
  protected readonly completed = TaskInstanceStatus.COMPLETED;
  protected readonly skipped = TaskInstanceStatus.SKIPPED;

  protected isSelected(row: DashboardTask): boolean {
    return this.store.selectedTaskId() === row.task.id;
  }
}
