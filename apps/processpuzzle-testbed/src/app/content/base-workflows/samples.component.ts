import { Component, computed, viewChild } from '@angular/core';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';

/**
 * Host of the `base-workflow` samples. The screens themselves come from `BASE_WORKFLOW_ROUTES`, mounted as
 * this route's children in `app.routes.ts` — the same arrangement the `base-state` and `base-app` samples
 * use, so what is demonstrated here is the library's own branches rather than a copy of them.
 *
 * Three toggles because base-workflow has three routable aggregates: the workflow a tenant authors, the
 * tools its steps call, and the runs it produces. The last is read-only, which the screens themselves make
 * plain — every field is disabled and Save is dead.
 */
@Component({
  selector: 'base-workflows-samples',
  standalone: true,
  imports: [SampleHostComponent],
  template: ` <pp-sample-host prefix="base-workflows" groupName="workflowSample" ariaLabel="Workflow Sample" [tabs]="tabs" /> `,
})
export class SamplesComponent {
  private host = viewChild(SampleHostComponent);
  readonly tabs: SampleTab[] = [
    { route: 'workflow', label: 'Workflow' },
    { route: 'tool-definition', label: 'Tool Definition' },
    { route: 'workflow-instance', label: 'Workflow Instance' },
  ];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
