import { signalStore } from '@ngrx/signals';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore, BaseFormNavigatorStore } from '@processpuzzle/base-entity';
import { TestEntityComponentService } from './test-entity-component.service';
import { TestEntityComponent } from './test-entity-component';

export const TestEntityComponentStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<TestEntityComponent>(TestEntityComponent, TestEntityComponentService),
  BaseFormNavigatorStore<TestEntityComponent>(TestEntityComponent),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
