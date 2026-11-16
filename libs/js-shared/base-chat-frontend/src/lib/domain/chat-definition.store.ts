import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { ChatDefinition } from './chat-definition';
import { ChatDefinitionService } from './chat-definition.service';

/** The stock CRUD store for the chat definitions. */
export const ChatDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<ChatDefinition>(ChatDefinition, () => inject(ChatDefinitionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('ChatDefinition'),
);
