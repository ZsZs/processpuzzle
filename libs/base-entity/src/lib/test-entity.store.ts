import { signalStore } from '@ngrx/signals';
import { BaseEntityStore } from './base-entity.store';
import { TestEntityService } from './test-entity.service';
import { TestEntity } from './test-entity';
import { BaseFormNavigatorStore } from './base-form-navigator/base-form-navigator.store';
import { BaseEntityTabsStore } from './base-tabs/base-entity-tabs.store';
import { BaseEntityContainerStore } from './base-entity-container.store';

export const TestEntityStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<TestEntity>(TestEntity, TestEntityService),
  BaseFormNavigatorStore('TestEntity'),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
