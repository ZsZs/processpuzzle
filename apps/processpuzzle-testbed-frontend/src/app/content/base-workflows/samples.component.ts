import { Component, computed, viewChild } from '@angular/core';
import { WORKFLOW_DASHBOARD_PATH } from '@processpuzzle/base-workflow';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';
import { ORDER_NAME, ORDER_PATH } from '../base-rules/rule-sample.routes';

/**
 * Where the Fulfillment Invoice tab points: base-document's `document` branch, whose segment is fixed by
 * `snakeCaseName('Document')`. The list holds every document of the tenant; the invoice is the one with
 * slug `fulfillment-invoice`, the artifact's `artifactTypeId`.
 */
export const FULFILLMENT_INVOICE_PATH = 'document';

/**
 * Host of the `base-workflow` samples. The screens themselves come from `WORKFLOW_DASHBOARD_ROUTES` and
 * `BASE_WORKFLOW_ROUTES`, mounted as this route's children in `app.routes.ts` — the same arrangement the
 * `base-state` and `base-app` samples use, so what is demonstrated here is the library's own branches
 * rather than a copy of them.
 *
 * **My Tasks first, and it is the default**, so `/base-workflow/samples` lands on the dashboard: it is the
 * screen an end user of a workflow application actually works from, where the other five are what a designer
 * authors beforehand. After it come the run's own screens — Order, the read-only Workflow Instance (every
 * field disabled and Save dead, as the screen makes plain), Fulfillment Invoice — and the definitions last.
 *
 * Not all seven branches get a toggle: the four catalog aggregates are reachable from the workflow's own
 * form and from `/design/workflows`, and seven toggles on one bar reads as a menu rather than a set of
 * samples.
 *
 * **The run is playable from this page.** Next to the workflow's own screens sit the two artifacts the
 * seeded order-fulfillment workflow works on, in the order a run touches them: create an `Order` (its
 * `OrderCreatedEvent` starts an instance on its own), watch the `Workflow Instance`, work its tasks under
 * My Tasks, and open the `Fulfillment Invoice` — a base-document document whose `order` port takes the
 * order being invoiced. Neither artifact is base-workflow's: `Order` is a metadata-only entity resolved at
 * run-time (see `rule-sample.routes.ts`) and the invoice is base-document's `document` branch, both mounted
 * as children of this route in `app.routes.ts`.
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
    { route: ORDER_PATH, label: ORDER_NAME },
    { route: 'workflow-instance', label: 'Workflow Instance' },
    { route: FULFILLMENT_INVOICE_PATH, label: 'Fulfillment Invoice' },
    { route: 'workflow', label: 'Workflow' },
    { route: 'tool-definition', label: 'Tool Definition' },
  ];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
