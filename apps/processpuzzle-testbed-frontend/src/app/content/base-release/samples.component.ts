import { Component, computed, viewChild } from '@angular/core';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';

/**
 * Host of the `base-release` samples: one tab per screen of `BASE_RELEASE_ROUTES`, mounted as this route's
 * children in `app.routes.ts`. The Release panel acts on the testbed organization, through base-release-backend.
 */
@Component({
  selector: 'base-release-samples',
  standalone: true,
  imports: [SampleHostComponent],
  template: ` <pp-sample-host prefix="base-release" groupName="releaseSample" ariaLabel="Release Sample" [tabs]="tabs" /> `,
})
export class SamplesComponent {
  private readonly host = viewChild(SampleHostComponent);
  readonly tabs: SampleTab[] = [{ route: 'release', label: 'Release' }];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
