import { Component, computed, viewChild } from '@angular/core';
import { WORKFLOW_DASHBOARD_PATH } from '@processpuzzle/base-workflow';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';

/**
 * Host of the `base-workflow` samples. The screens themselves come from `WORKFLOW_DASHBOARD_ROUTES` and
 * `BASE_WORKFLOW_ROUTES`, mounted as this route's children in `app.routes.ts` — the same arrangement the
 * `base-state` and `base-app` samples use, so what is demonstrated here is the library's own branches
 * rather than a copy of them.
 *
 * **My Tasks first, and it is the default**, so `/base-workflow/samples` lands on the dashboard: it is the
 * screen an end user of a workflow application actually works from, where the other five are what a designer
 * authors beforehand. The order after it is the order a tenant fills the catalog in — the workflow, then the
 * definitions it composes, then the runs those produce, which are read-only (every field disabled and Save
 * dead, as the screens themselves make plain).
 *
 * Not all seven branches get a toggle: the four catalog aggregates are reachable from the workflow's own
 * form and from `/design/workflows`, and seven toggles on one bar reads as a menu rather than a set of
 * samples.
 */
@Component({
  selector: 'base-workflows-samples',
  standalone: true,
  imports: [SampleHostComponent],
  template: ` <pp-sample-host prefix="base-workflows" groupName="workflowSample" ariaLabel="Workflow Sample" [tabs]="tabs" /> `,
})
export class SamplesComponent {
  private readonly host = viewChild(SampleHostComponent);
  readonly tabs: SampleTab[] = [
    { route: WORKFLOW_DASHBOARD_PATH, label: 'My Tasks' },
    { route: 'workflow', label: 'Workflow' },
    { route: 'tool-definition', label: 'Tool Definition' },
    { route: 'workflow-instance', label: 'Workflow Instance' },
  ];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
