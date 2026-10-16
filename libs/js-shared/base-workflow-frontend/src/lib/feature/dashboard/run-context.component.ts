import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { TASK_DASHBOARD_I18N_SCOPE } from '../../base-workflow.i18n';
import { WorkflowDashboardStore } from '../../domain/dashboard/workflow-dashboard.store';

/**
 * The open run's context variables, key beside value — what the tasks, steps and rules of the run read.
 *
 * Flattened by the store (`selectedContext`): the parameters an event carried land in the context namespaced
 * under the event's id, so they read here as `order-created.orderNumber`, and a bound artifact's record id as
 * the artifact's object name, `new_order`. Read-only; nothing on the dashboard writes the context but a task
 * completion. Renders nothing without an open run, since the board above already asks for one.
 */
@Component({
  selector: 'pp-run-context',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.selectedInstance()) {
      <section class="context" data-testid="run-context">
        <h5 class="context__heading">{{ titleKey | transloco }}</h5>
        @if (store.selectedContext().length > 0) {
          <dl class="context__list">
            @for (variable of store.selectedContext(); track variable.key) {
              <dt class="context__key" [attr.data-testid]="'context-key-' + variable.key">{{ variable.key }}</dt>
              <dd class="context__value" [attr.data-testid]="'context-value-' + variable.key">{{ variable.value }}</dd>
            }
          </dl>
        } @else {
          <p class="context__empty" data-testid="run-context-none">{{ noneKey | transloco }}</p>
        }
      </section>
    }
  `,
  styles: `
    .context {
      display: flex;
      flex-direction: column;
      gap: 6px;
      min-width: 0;
    }
    .context__heading {
      margin: 0;
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.04em;
      color: #666666;
    }
    /* Two columns, key and value, so a long dotted key does not push its value onto the next line. */
    .context__list {
      display: grid;
      grid-template-columns: minmax(120px, max-content) minmax(0, 1fr);
      gap: 2px 16px;
      margin: 0;
      font-size: 12px;
    }
    .context__key {
      color: #777777;
      font-family: monospace;
    }
    .context__value {
      margin: 0;
      overflow-wrap: anywhere;
    }
    .context__empty {
      margin: 0;
      font-size: 12px;
      color: #999999;
    }
  `,
})
export class RunContextComponent {
  protected readonly store = inject(WorkflowDashboardStore);

  protected readonly titleKey = `${TASK_DASHBOARD_I18N_SCOPE}.context`;
  protected readonly noneKey = `${TASK_DASHBOARD_I18N_SCOPE}.context_none`;
}
