import { Component, computed, viewChild } from '@angular/core';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';

/**
 * Host of the `base-release` samples. The library has no screens yet: when release components are added to
 * `BASE_RELEASE_ROUTES` (mounted as this route's children in `app.routes.ts`), list each one in `tabs`.
 */
@Component({
  selector: 'base-release-samples',
  standalone: true,
  imports: [SampleHostComponent],
  template: ` <pp-sample-host prefix="base-release" groupName="releaseSample" ariaLabel="Release Sample" [tabs]="tabs" /> `,
})
export class SamplesComponent {
  private readonly host = viewChild(SampleHostComponent);
  readonly tabs: SampleTab[] = [];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
