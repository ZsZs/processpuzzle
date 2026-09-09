import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { BASE_WORKFLOW_TRANSLOCO_SCOPE, TASK_DASHBOARD_I18N_SCOPE } from './base-workflow.i18n';
import { WorkflowDashboardComponent } from './feature/dashboard/workflow-dashboard.component';

/** The segment the dashboard mounts at, named once because the host's tab link has to agree with it. */
export const WORKFLOW_DASHBOARD_PATH = 'workflow-dashboard';

/**
 * The task dashboard, as a branch of its own rather than a seventh entry in {@link BASE_WORKFLOW_ROUTES}.
 *
 * Because it is not the same kind of screen. Those six branches are *authoring* surfaces — a routable
 * aggregate each, mounted on `BaseEntityContainerComponent`, and `DESIGN_ROUTES` spreads all six into the
 * Workflow Designer on the strength of that. The dashboard is an *operations* surface: it drives runs that
 * already exist. Adding it there would mount an operational screen inside the designer, reachable by URL and
 * absent from the tab bar — a route nothing links to is a route nobody maintains.
 *
 * So a host mounts it deliberately, beside the authoring branches where it wants both:
 *
 * ```ts
 * children: [...WORKFLOW_DASHBOARD_ROUTES, ...BASE_WORKFLOW_ROUTES]
 * ```
 *
 * **No `entityName` in `data` and no `BaseEntityContainerComponent`**, unlike every branch of
 * `BASE_WORKFLOW_ROUTES`: this screen is not a generated list and form over one aggregate, so it has no
 * breadcrumb level to contribute and no facade to bind to `ACTIVE_ENTITY_FACADE`. It reads five stores and
 * renders its own layout. The segment is therefore free of the `snakeCaseName(entityName)` constraint the
 * others carry — `BaseFormNavigatorSingletonStore` builds no URL into it.
 *
 * Only the `base_workflow` scope is registered, not `base_entity` beside it: nothing here renders a generic
 * tab bar or toolbar, which is the reason the authoring branches need both. Both aliases spelled out, as
 * everywhere in this workspace — transloco camel-cases a default alias and `base_workflow` would silently
 * become `baseWorkflow`, missing every key below it.
 */
export const WORKFLOW_DASHBOARD_ROUTES: Routes = [
  {
    path: WORKFLOW_DASHBOARD_PATH,
    title: 'ProcessPuzzle - My Tasks',
    // `menuTitle` names the screen's own title key rather than a `workflow.*` one like the six authoring
    // branches, because those six name keys the bundle does not actually contain — a sidenav mounting them
    // renders the raw key. This one resolves.
    data: { icon: 'checklist', menuTitle: `${TASK_DASHBOARD_I18N_SCOPE}.title` },
    component: WorkflowDashboardComponent,
    providers: [provideTranslocoScope({ scope: BASE_WORKFLOW_TRANSLOCO_SCOPE, alias: BASE_WORKFLOW_TRANSLOCO_SCOPE })],
  },
];
