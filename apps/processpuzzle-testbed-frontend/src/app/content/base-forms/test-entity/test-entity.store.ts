import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { inject } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { TestEntityService } from './test-entity.service';
import { TestEntity } from './test-entity';

export const TestEntityStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<TestEntity>(TestEntity, () => inject(TestEntityService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('TestEntity'),
);
