import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { EventDefinition } from './event-definition';
import { EventDefinitionService } from './event-definition.service';

/**
 * The stock CRUD store. Like every `BaseEntityStore` it lists the catalog from its own `onInit`, which is
 * also what lets a workflow's `eventType` picker offer the catalog without loading it itself.
 */
export const EventDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<EventDefinition>(EventDefinition, () => inject(EventDefinitionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('EventDefinition'),
);
