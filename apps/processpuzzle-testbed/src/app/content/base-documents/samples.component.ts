import { Component, computed, viewChild } from '@angular/core';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';

/**
 * The live document screen, mounted here the same way `base-apps` mounts `App Definition`: this tab's route
 * declares `BASE_DOCUMENT_ROUTES` as static children and this component renders them through its outlet.
 *
 * Static children rather than `loadChildren` is what makes the entity addressable by the generated e2e
 * suites. `EntityRouteRegistry` derives a descriptor's base path by walking the router's configuration, and a
 * `loadChildren` branch that has never been entered is not in it — which is why `/design/document`, real as
 * it is, leaves `Document` in the registry with no route at all and the suites fall back to guessing
 * `/base-entity/samples/document`. Mounting the same routes under a statically declared branch reports the
 * path, so `[Document] CRUD` / `LIST` / `RELATIONSHIP` address the screen the application actually has.
 *
 * `/design/document` stays exactly as it was — the design section is where a user reaches documents; this is
 * the testbed's own copy of the screen, next to every other feature's sample.
 */
@Component({
  selector: 'base-documents-samples',
  standalone: true,
  imports: [SampleHostComponent],
  template: ` <pp-sample-host prefix="base-documents" groupName="documentSample" ariaLabel="Document Sample" [tabs]="tabs" /> `,
})
export class SamplesComponent {
  private host = viewChild(SampleHostComponent);
  readonly tabs: SampleTab[] = [{ route: 'document', label: 'Document' }];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
