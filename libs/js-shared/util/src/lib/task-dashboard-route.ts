import { InjectionToken } from '@angular/core';

/**
 * Absolute path of the workflow task dashboard — where `TaskDashboardButtonComponent` leads.
 *
 * A token rather than base-workflow's `WORKFLOW_DASHBOARD_PATH`, for two reasons. The button is a widget, and
 * the widget library does not depend on base-workflow. And that constant is only the dashboard's own
 * *segment*: where the host mounts the branch that carries it — `/base-workflow/samples` in the testbed, the
 * root in an application built around its workflows, `/{orgKey}` in a shell whose tenant is a path segment —
 * is the host's to say, the same way `DESIGN_ROUTE_PREFIX` says where the designer is.
 *
 * Defaults to the dashboard mounted at the root. Provide it with a leading slash and no trailing one.
 */
export const TASK_DASHBOARD_ROUTE = new InjectionToken<string>('TASK_DASHBOARD_ROUTE', {
  providedIn: 'root',
  factory: () => '/workflow-dashboard',
});
