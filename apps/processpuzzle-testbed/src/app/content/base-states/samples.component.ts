import { Component, computed, viewChild } from '@angular/core';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';

/**
 * Host of the `base-state` samples. The screens themselves come from `BASE_STATE_ROUTES`, mounted as this
 * route's children in `app.routes.ts` — the same arrangement the `base-app` samples use, so what is
 * demonstrated here is the library's own authoring branch rather than a copy of it.
 */
@Component({
  selector: 'base-states-samples',
  standalone: true,
  imports: [SampleHostComponent],
  template: ` <pp-sample-host prefix="base-states" groupName="stateSample" ariaLabel="State Machine Sample" [tabs]="tabs" /> `,
})
export class SamplesComponent {
  private host = viewChild(SampleHostComponent);
  readonly tabs: SampleTab[] = [{ route: 'state-machine-definition', label: 'State Machine Definition' }];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
