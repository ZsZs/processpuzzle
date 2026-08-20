import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { StateMachineDefinition } from './state-machine-definition';
import { StateMachineDefinitionService } from './state-machine-definition.service';

/**
 * The stock CRUD store. Unlike `AppDefinitionStore` there is no extra feature to add: a state machine
 * definition has no publish step, its `version` being an optimistic lock rather than a lifecycle.
 */
export const StateMachineDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<StateMachineDefinition>(StateMachineDefinition, () => inject(StateMachineDefinitionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('StateMachineDefinition'),
);
