import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { WorkflowInstance } from './workflow-instance';
import { WorkflowInstanceService } from './workflow-instance.service';

/**
 * The stock CRUD store, unchanged even though the screens above it are read-only: what makes them
 * read-only is the descriptor (`isAbstract`) and the attributes (`disabled`), not a narrower store.
 * Keeping the standard feature set is what lets a later action surface — assign, complete, skip —
 * reload a row through the same store the list already reads from.
 */
export const WorkflowInstanceStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<WorkflowInstance>(WorkflowInstance, () => inject(WorkflowInstanceService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('WorkflowInstance'),
);
