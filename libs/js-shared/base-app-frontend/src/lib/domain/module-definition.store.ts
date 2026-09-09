import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { ModuleDefinition } from './module-definition';
import { ModuleDefinitionService } from './module-definition.service';

/**
 * Plain generic CRUD — a module has no `publish` of its own. Publishing is an `AppDefinition` operation:
 * an app is what a user navigates to, and its status is what decides whether a mount is live. Giving a
 * module a second, independent lifecycle would make "which version of what is live" a question with two
 * answers.
 */
export const ModuleDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<ModuleDefinition>(ModuleDefinition, () => inject(ModuleDefinitionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('ModuleDefinition'),
);
