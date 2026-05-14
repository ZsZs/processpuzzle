import { signalStore } from '@ngrx/signals';
import { inject } from '@angular/core';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { TestEntityComponentService } from './test-entity-component.service';
import { TestEntityComponent } from './test-entity-component';

export const TestEntityComponentStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<TestEntityComponent>(TestEntityComponent, () => inject(TestEntityComponentService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
