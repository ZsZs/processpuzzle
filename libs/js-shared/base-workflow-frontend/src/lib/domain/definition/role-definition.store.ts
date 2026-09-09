import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { RoleDefinition } from './role-definition';
import { RoleDefinitionService } from './role-definition.service';

/** The stock CRUD store; a role has no lifecycle beyond its optimistic-lock `version`. */
export const RoleDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<RoleDefinition>(RoleDefinition, () => inject(RoleDefinitionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('RoleDefinition'),
);
