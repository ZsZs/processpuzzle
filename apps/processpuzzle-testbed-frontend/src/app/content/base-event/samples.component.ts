import { Component, computed, viewChild } from '@angular/core';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';

/**
 * Host of the `base-event` samples. The screens themselves come from `BASE_EVENT_ROUTES`, mounted as this
 * route's children in `app.routes.ts`, so what is demonstrated here is the library's own authoring branch.
 */
@Component({
  selector: 'base-event-samples',
  standalone: true,
  imports: [SampleHostComponent],
  template: ` <pp-sample-host prefix="base-event" groupName="eventSample" ariaLabel="Event Catalog Sample" [tabs]="tabs" /> `,
})
export class SamplesComponent {
  private readonly host = viewChild(SampleHostComponent);
  readonly tabs: SampleTab[] = [{ route: 'event-definition', label: 'Event Definition' }];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
