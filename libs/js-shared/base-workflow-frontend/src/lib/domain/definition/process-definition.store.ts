import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { ProcessDefinition } from './process-definition';
import { ProcessDefinitionService } from './process-definition.service';

/**
 * The stock CRUD store. There is no extra feature to add: a process definition has no publish step —
 * its `version` is an optimistic lock rather than a lifecycle — and its execution side is a resource
 * of its own, `Process Instance`, with a store of its own.
 */
export const ProcessDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<ProcessDefinition>(ProcessDefinition, () => inject(ProcessDefinitionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('ProcessDefinition'),
);
