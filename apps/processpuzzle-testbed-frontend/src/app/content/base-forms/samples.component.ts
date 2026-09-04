import { Component, computed, viewChild } from '@angular/core';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';

@Component({
  selector: 'base-entity-samples',
  standalone: true,
  imports: [SampleHostComponent],
  template: `
    <pp-sample-host prefix="base-entity" groupName="fontStyle" ariaLabel="Font Style" desc2Key="samples_desc_1" [tabs]="tabs">
      <img sample-header src="https://github.com/ZsZs/processpuzzle/blob/develop/docs/base-entity-sample_entities.png?raw=true" width="600px" alt="Sample Entities" />
    </pp-sample-host>
  `,
})
export class SamplesComponent {
  private readonly host = viewChild(SampleHostComponent);
  readonly tabs: SampleTab[] = [
    { route: 'test-entity', label: 'Test Entity' },
    { route: 'test-entity-component', label: 'Test Entity Komponente' },
    { route: 'related-entity', label: 'Related Entity' },
    { route: 'trunk-data', label: 'Stamm Data' },
    { route: 'dynamic-entity', label: 'Dynamic Entity' },
  ];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
