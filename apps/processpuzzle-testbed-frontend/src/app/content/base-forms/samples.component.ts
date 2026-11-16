import { Component, computed, inject, viewChild } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { WorkingContextService } from '@processpuzzle/base-entity';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';
import { COMPONENT_ONE_PATH, COMPONENT_TWO_PATH, DEMO_ROOT_PATH } from './context-object/context-object.routes';

/** What stands for "the context object, whichever it is": the guard of the aggregate's route resolves it, or asks for a choice. */
const UNKNOWN = 'unknown';

@Component({
  selector: 'base-entity-samples',
  standalone: true,
  imports: [SampleHostComponent],
  template: `
    <pp-sample-host prefix="base-entity" groupName="fontStyle" ariaLabel="Font Style" desc2Key="samples_desc_1" [tabs]="tabs()">
      <img sample-header src="https://github.com/ZsZs/processpuzzle/blob/develop/docs/base-entity-sample_entities.png?raw=true" width="600px" alt="Sample Entities" />
    </pp-sample-host>
  `,
})
export class SamplesComponent {
  private readonly host = viewChild(SampleHostComponent);
  private readonly workingContext = inject(WorkingContextService);
  /** The part buttons name the context object when the URL has one, and `unknown` otherwise — the aggregate's route does the rest. */
  readonly tabs = computed<SampleTab[]>(() => {
    const id = this.workingContext.current(DEMO_ROOT_PATH)?.id ?? UNKNOWN;
    return [
      { route: 'test-entity', label: 'Test Entity' },
      { route: 'test-entity-component', label: 'Test Entity Komponente' },
      { route: 'related-entity', label: 'Related Entity' },
      { route: 'trunk-data', label: 'Stamm Data' },
      { route: 'dynamic-entity', label: 'Dynamic Entity' },
      {
        route: DEMO_ROOT_PATH,
        link: [DEMO_ROOT_PATH, 'list'],
        label: 'Aggregate Root',
        description:
          'The Context Object sample. A context object is an entity you pick once and then work inside. Select exactly one row below and press "Set as context object"; the status bar then shows it, and the embedded component lists are about that object.',
      },
      {
        route: COMPONENT_ONE_PATH,
        link: [DEMO_ROOT_PATH, id, COMPONENT_ONE_PATH, 'list'],
        label: 'Embedded Component One',
        description: 'The first kind of part the context object embeds, in the ordinary list. With no context object yet it asks for one first, then continues here.',
      },
      {
        route: COMPONENT_TWO_PATH,
        link: [DEMO_ROOT_PATH, id, COMPONENT_TWO_PATH, 'list'],
        label: 'Embedded Component Two',
        description: 'The second kind of part the context object embeds, listed and edited the same way.',
      },
    ];
  });
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
