import { Component, computed, viewChild } from '@angular/core';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';

/**
 * Host of the `base-calculation` samples. The screens themselves come from `BASE_CALCULATION_ROUTES` and
 * `CALCULATION_RUNS_ROUTES`, mounted as this route's children in `app.routes.ts`, so what is demonstrated here is
 * the library's own authoring branch and its runs screen.
 */
@Component({
  selector: 'base-calculation-samples',
  standalone: true,
  imports: [SampleHostComponent],
  template: ` <pp-sample-host prefix="base-calculation" groupName="calculationSample" ariaLabel="Calculation Sample" [tabs]="tabs" /> `,
})
export class SamplesComponent {
  private readonly host = viewChild(SampleHostComponent);
  readonly tabs: SampleTab[] = [
    { route: 'calculation-definition', label: 'Calculation Definition' },
    { route: 'calculation-runs', label: 'Calculation Runs' },
  ];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
