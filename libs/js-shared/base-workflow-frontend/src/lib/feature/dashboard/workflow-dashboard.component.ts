import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TASK_DASHBOARD_I18N_SCOPE } from '../../base-workflow.i18n';
import { INBOX_SCOPES, InboxScope } from '../../domain/dashboard/dashboard-task';
import { WorkflowDashboardStore } from '../../domain/dashboard/workflow-dashboard.store';
import { ProcessBoardComponent } from './process-board.component';
import { TaskDetailComponent } from './task-detail.component';
import { TaskListComponent } from './task-list.component';

/**
 * The workflow task dashboard: the screen a user does their work from, as opposed to the generated Workflow
 * Instance screens, which display a run without driving it.
 *
 * This is where the four verbs the library's README listed as having "no front-end surface yet" get one:
 * `/assign`, `/complete` and `/skip` are reachable from here. `POST /instances` — starting a run — is not,
 * because starting a workflow is not a task somebody was assigned; it belongs to whatever triggers the
 * process.
 *
 * **Three scopes, one selection.** The toggle switches which queue fills the left pane; the task open in the
 * right pane survives the switch, because a user who was working on something and glanced at the team queue
 * has not stopped working on it.
 *
 * | Scope | Left pane | Answers |
 * | --- | --- | --- |
 * | My tasks | queue of `assignedTo == me` | what am I supposed to be doing |
 * | Team | queue of unassigned `ACTIVE` tasks the session's roles may perform | what could I pick up |
 * | Process | one run's board, grouped by status | where is this run stuck |
 *
 * The Process scope lays out differently on purpose: a four-column board squeezed into a third of the width
 * is four unreadable columns, so it takes the full row and the task detail moves below it. The other two are
 * a list beside a detail — the standard two-pane inbox.
 *
 * The run picker is a plain `<select>`, like every other control on this screen: the library declares no
 * `@angular/material` peer dependency, which is why the modeler's toolbar is bare checkboxes and its Save is
 * a bare `<button>`.
 */
@Component({
  selector: 'pp-workflow-dashboard',
  standalone: true,
  imports: [TranslocoPipe, TaskListComponent, TaskDetailComponent, ProcessBoardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="dashboard">
      <div class="dashboard__toolbar">
        <div class="dashboard__scopes" role="tablist" [attr.aria-label]="titleKey | transloco">
          @for (scope of scopes; track scope) {
            <button
              type="button"
              role="tab"
              class="scope"
              [class.scope--active]="store.scope() === scope"
              [attr.aria-selected]="store.scope() === scope"
              [attr.data-testid]="'dashboard-scope-' + scope"
              (click)="store.setScope(scope)"
            >
              {{ scopeKey(scope) | transloco }}
              <span class="scope__count">{{ countOf(scope) }}</span>
            </button>
          }
        </div>

        @if (isProcess()) {
          <label class="dashboard__run">
            {{ runKey | transloco }}
            <select class="dashboard__select" [value]="store.selectedInstanceId() ?? ''" data-testid="dashboard-run" (change)="pickRun($event)">
              <option value="">—</option>
              @for (instance of store.selectableInstances(); track instance.id) {
                <option [value]="instance.id">{{ instance.workflowName || instance.workflowId }} · {{ instance.id }}</option>
              }
            </select>
          </label>
        }
      </div>

      <div class="dashboard__body" [class.dashboard__body--stacked]="isProcess()">
        @if (isProcess()) {
          <pp-process-board />
        } @else {
          <pp-task-list />
        }
        <pp-task-detail />
      </div>
    </div>
  `,
  styles: `
    /* The same white card surface the modeler tab draws on — same white, same radius, same padding. */
    .dashboard {
      display: flex;
      flex-direction: column;
      gap: 12px;
      background-color: #ffffff;
      border-radius: 6px;
      padding: 16px 20px 24px;
    }
    .dashboard__toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px 16px;
    }
    /* A segmented control built from three buttons: mat-button-toggle would need the Material peer
       dependency this library does not declare. */
    .dashboard__scopes {
      display: flex;
      border: 1px solid #dddddd;
      border-radius: 4px;
      overflow: hidden;
    }
    .scope {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 5px 14px;
      border: 0;
      border-right: 1px solid #dddddd;
      background-color: #ffffff;
      font: inherit;
      font-size: 13px;
      cursor: pointer;
    }
    .scope:last-child {
      border-right: 0;
    }
    .scope--active {
      background-color: var(--pp-button-primary-bg, rgb(24, 111, 206));
      color: var(--pp-button-primary-text, #eeeeee);
    }
    .scope__count {
      font-size: 11px;
      opacity: 0.75;
    }
    .dashboard__run {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: #666666;
    }
    .dashboard__select {
      max-width: 320px;
      padding: 3px 6px;
      border: 1px solid #cccccc;
      border-radius: 4px;
      font: inherit;
      font-size: 12px;
    }
    /* Queue beside detail. The queue gets a fixed band rather than a fraction, so a long task name widens
       the row's ellipsis instead of the whole pane. */
    .dashboard__body {
      display: grid;
      grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
      gap: 20px;
      align-items: start;
    }
    /* Process scope: the board is four columns wide, so it takes the row and the detail moves below it. */
    .dashboard__body--stacked {
      grid-template-columns: minmax(0, 1fr);
    }
    @media (max-width: 720px) {
      .dashboard__body {
        grid-template-columns: minmax(0, 1fr);
      }
    }
  `,
})
export class WorkflowDashboardComponent {
  protected readonly store = inject(WorkflowDashboardStore);

  protected readonly scopes = INBOX_SCOPES;
  protected readonly titleKey = `${TASK_DASHBOARD_I18N_SCOPE}.title`;
  protected readonly runKey = `${TASK_DASHBOARD_I18N_SCOPE}.run`;

  protected readonly isProcess = computed(() => this.store.scope() === 'process');

  protected scopeKey(scope: InboxScope): string {
    return `${TASK_DASHBOARD_I18N_SCOPE}.scopes.${scope}`;
  }

  /**
   * The badge on each toggle. Counted for every scope rather than only the open one, which is the point: a
   * user glances at "Team 3" and decides whether to look, instead of switching to find out.
   */
  protected countOf(scope: InboxScope): number {
    switch (scope) {
      case 'mine':
        return this.store.myTasks().length;
      case 'team':
        return this.store.teamTasks().length;
      default:
        return this.store.processTasks().length;
    }
  }

  protected pickRun(event: Event): void {
    const instanceId = (event.target as HTMLSelectElement).value;
    this.store.selectInstance(instanceId || undefined);
  }
}
