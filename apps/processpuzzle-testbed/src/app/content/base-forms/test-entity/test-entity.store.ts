import { signalStore } from '@ngrx/signals';
import { inject } from '@angular/core';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { TestEntityService } from './test-entity.service';
import { TestEntity } from './test-entity';

export const TestEntityStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<TestEntity>(TestEntity, () => inject(TestEntityService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
