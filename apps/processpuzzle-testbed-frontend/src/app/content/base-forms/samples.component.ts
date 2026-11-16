import { Component, computed, DestroyRef, inject, viewChild } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { WorkingContextService, WorkingContextStatusbarComponent } from '@processpuzzle/base-entity';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';
import {
  AGGREGATE_ROOT_PATH,
  COMPONENT_ONE_PATH,
  COMPONENT_TWO_PATH,
  CONTEXT_OBJECT_BASE_PATH,
  DEMO_CONTEXT_NAME,
  DEMO_ROOT_NAME,
} from './context-object/context-object.routes';

@Component({
  selector: 'base-entity-samples',
  standalone: true,
  imports: [SampleHostComponent, WorkingContextStatusbarComponent],
  template: `
    <pp-sample-host prefix="base-entity" groupName="fontStyle" ariaLabel="Font Style" desc2Key="samples_desc_1" [tabs]="tabs">
      <img sample-header src="https://github.com/ZsZs/processpuzzle/blob/develop/docs/base-entity-sample_entities.png?raw=true" width="600px" alt="Sample Entities" />
      <pp-working-context-statusbar sample-status />
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
    {
      route: AGGREGATE_ROOT_PATH,
      label: 'Aggregate Root',
      description:
        'The Context Object sample. A context object is an entity you pick once and then work inside. Select exactly one row below and press "Set as context object"; the status bar then shows it, and the two embedded component lists are about that object.',
    },
    {
      route: COMPONENT_ONE_PATH,
      label: 'Embedded Component One',
      description: 'The first kind of part the context object embeds. It is an ordinary list; with no context object selected it asks for one first. Add or open a row to edit that part alone.',
    },
    {
      route: COMPONENT_TWO_PATH,
      label: 'Embedded Component Two',
      description: 'The second kind of part the context object embeds, listed and edited the same way.',
    },
  ];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');

  constructor() {
    // The context exists while the samples page does: the testbed's application declares none of its own.
    const unregister = inject(WorkingContextService).register([
      {
        name: DEMO_CONTEXT_NAME,
        entityName: DEMO_ROOT_NAME,
        basePath: CONTEXT_OBJECT_BASE_PATH,
        picker: { searchFields: ['name', 'description'], recent: 3 },
        display: { label: '{{name}}' },
      },
    ]);
    inject(DestroyRef).onDestroy(unregister);
  }
}
