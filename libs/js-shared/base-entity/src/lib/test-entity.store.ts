import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { inject } from '@angular/core';
import { BaseEntityStore } from './base-entity-store/base-entity.store';
import { TestEntityService } from './base-entity-service/test-entity.service';
import { TestEntity } from './test-entity';
import { BaseEntityTabsStore } from './base-tabs/base-entity-tabs.store';
import { BaseEntityContainerStore } from './base-entity-container.store';

export const TestEntityStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<TestEntity>(TestEntity, () => inject(TestEntityService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('TestEntity'),
);
