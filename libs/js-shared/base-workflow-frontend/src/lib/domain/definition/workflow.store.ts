import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { Workflow } from './workflow';
import { WorkflowService } from './workflow.service';

/**
 * The stock CRUD store. There is no extra feature to add: a workflow definition has no publish step —
 * its `version` is an optimistic lock rather than a lifecycle — and its execution side is a resource
 * of its own, `Workflow Instance`, with a store of its own.
 */
export const WorkflowStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<Workflow>(Workflow, () => inject(WorkflowService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('Workflow'),
);
