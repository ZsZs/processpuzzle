import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { ProcessInstance } from './process-instance';
import { ProcessInstanceService } from './process-instance.service';

/**
 * The stock CRUD store, unchanged even though the screens above it are read-only: what makes them
 * read-only is the descriptor (`isAbstract`) and the attributes (`disabled`), not a narrower store.
 * Keeping the standard feature set is what lets a later action surface — assign, complete, skip —
 * reload a row through the same store the list already reads from.
 */
export const ProcessInstanceStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<ProcessInstance>(ProcessInstance, () => inject(ProcessInstanceService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('ProcessInstance'),
);
