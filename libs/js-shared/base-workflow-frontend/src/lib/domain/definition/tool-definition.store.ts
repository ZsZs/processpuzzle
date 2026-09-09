import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { ToolDefinition } from './tool-definition';
import { ToolDefinitionService } from './tool-definition.service';

/** The stock CRUD store; a tool has no lifecycle beyond its optimistic-lock `version`. */
export const ToolDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<ToolDefinition>(ToolDefinition, () => inject(ToolDefinitionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('ToolDefinition'),
);
