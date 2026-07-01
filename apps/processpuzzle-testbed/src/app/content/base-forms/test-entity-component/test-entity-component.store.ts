import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { inject } from '@angular/core';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { TestEntityComponentService } from './test-entity-component.service';
import { TestEntityComponent } from './test-entity-component';

export const TestEntityComponentStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<TestEntityComponent>(TestEntityComponent, () => inject(TestEntityComponentService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('TestEntityComponent'),
);
