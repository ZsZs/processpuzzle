import { signalStore } from '@ngrx/signals';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore, BaseFormNavigatorStore } from '@processpuzzle/base-entity';
import { TestEntityService } from './test-entity.service';
import { TestEntity } from './test-entity';

export const TestEntityStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<TestEntity>(TestEntity, TestEntityService),
  BaseFormNavigatorStore<TestEntity>(TestEntity),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
