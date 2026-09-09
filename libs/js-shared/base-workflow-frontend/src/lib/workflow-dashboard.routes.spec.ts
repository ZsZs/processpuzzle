import { provideTranslocoScope, TRANSLOCO_SCOPE } from '@jsverse/transloco';
import { describe, expect, it } from 'vitest';
import { BASE_WORKFLOW_ROUTES } from './base-workflow.routes';
import { BASE_WORKFLOW_TRANSLOCO_SCOPE, TASK_DASHBOARD_I18N_SCOPE } from './base-workflow.i18n';
import { WorkflowDashboardComponent } from './feature/dashboard/workflow-dashboard.component';
import { WORKFLOW_DASHBOARD_PATH, WORKFLOW_DASHBOARD_ROUTES } from './workflow-dashboard.routes';

describe('WORKFLOW_DASHBOARD_ROUTES', () => {
  const [dashboardRoute] = WORKFLOW_DASHBOARD_ROUTES;

  it('mounts the dashboard on the segment its host links to', () => {
    expect(WORKFLOW_DASHBOARD_ROUTES).toHaveLength(1);
    expect(dashboardRoute.path).toBe(WORKFLOW_DASHBOARD_PATH);
    expect(dashboardRoute.component).toBe(WorkflowDashboardComponent);
  });

  /**
   * A branch of its own rather than a seventh entry in `BASE_WORKFLOW_ROUTES`. Those six are authoring
   * surfaces and `DESIGN_ROUTES` spreads all six into the Workflow Designer on the strength of that;
   * adding an operations screen there would mount it inside the designer, reachable by URL and absent from
   * the tab bar.
   */
  it('is not one of the six authoring branches', () => {
    expect(BASE_WORKFLOW_ROUTES.map((route) => route.path)).not.toContain(WORKFLOW_DASHBOARD_PATH);
  });

  // No `entityName` and no BaseEntityContainerComponent: this is not a generated list and form over one
  // aggregate, so it contributes no breadcrumb level and binds no ACTIVE_ENTITY_FACADE.
  it('declares no entity name, because it is not a generated screen', () => {
    expect(dashboardRoute.data?.['entityName']).toBeUndefined();
  });

  it('carries a page title and a resolvable menu title', () => {
    expect(dashboardRoute.title).toBeTruthy();
    expect(dashboardRoute.data?.['menuTitle']).toBe(`${TASK_DASHBOARD_I18N_SCOPE}.title`);
    expect(dashboardRoute.data?.['icon']).toBeTruthy();
  });

  /**
   * Only `base_workflow`, unlike the authoring branches which need `base_entity` beside it: nothing here
   * renders a generic tab bar or toolbar. The alias is spelled out because transloco camel-cases a default
   * one, which would turn `base_workflow` into `baseWorkflow` and miss every key below it.
   */
  it('registers its own transloco scope with the alias spelled out', () => {
    const providers = (dashboardRoute.providers?.flat() ?? []) as Array<{ provide?: unknown; useValue?: unknown }>;
    const scopes = providers.filter((provider) => provider.provide === TRANSLOCO_SCOPE).map((provider) => provider.useValue);

    expect(scopes).toEqual([{ scope: BASE_WORKFLOW_TRANSLOCO_SCOPE, alias: BASE_WORKFLOW_TRANSLOCO_SCOPE }]);
    expect(providers).toEqual(provideTranslocoScope({ scope: BASE_WORKFLOW_TRANSLOCO_SCOPE, alias: BASE_WORKFLOW_TRANSLOCO_SCOPE }).flat());
  });
});
