import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { TaskDefinition } from './task-definition';
import { TaskDefinitionService } from './task-definition.service';

/** The stock CRUD store; a task's children travel inside its own payload, so nothing beyond the stock CRUD is needed. */
export const TaskDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<TaskDefinition>(TaskDefinition, () => inject(TaskDefinitionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('TaskDefinition'),
);
