import { Component, computed, viewChild } from '@angular/core';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';

@Component({
  selector: 'base-apps-samples',
  standalone: true,
  imports: [SampleHostComponent],
  template: ` <pp-sample-host prefix="base-apps" groupName="appSample" ariaLabel="Application Sample" [tabs]="tabs" /> `,
})
export class SamplesComponent {
  private host = viewChild(SampleHostComponent);
  readonly tabs: SampleTab[] = [{ route: 'app-definition', label: 'App Definition' }];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
