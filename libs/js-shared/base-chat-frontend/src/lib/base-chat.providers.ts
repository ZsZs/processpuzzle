import type { Provider } from '@angular/core';
import type { BaseEntityFacadeRegistry } from '@processpuzzle/base-entity';
import { CHAT_DEFINITION_ENTITY_NAME } from './domain/chat-entity-names';
import { ChatDefinitionFacade } from './feature/definition/chat-definition.facade';
import { provideChatPanelWidget } from './feature/panel/chat-panel.widget';
import { provideChatTopicsWidget } from './feature/topics/chat-topics.widget';
import { provideChatUnreadBadgeWidget } from './feature/unread/chat-unread-badge.widget';

/**
 * Registers the three chat widgets — `chat-panel`, `chat-topics` and `chat-unread-badge` — with the widget registry, so a designer
 * can place them and a run-time shell can render them. Spread into the application's providers.
 *
 * What the widgets need from the application is supplied separately, where it is composed: the identity
 * (`CHAT_IDENTITY`) and, optionally, the active working context (`provideChatWorkingContextSubject()`).
 */
export function provideChatWidgets(): Provider[] {
  return [...provideChatPanelWidget(), ...provideChatTopicsWidget(), ...provideChatUnreadBadgeWidget()];
}

/** The facade of the chat definitions, to be spread into the application's `providers`. */
export const BASE_CHAT_FACADE_PROVIDERS: Provider[] = [ChatDefinitionFacade];

/** The same facade keyed by entity name, to be spread into the application's `BASE_ENTITY_FACADE_REGISTRY` value. */
export const BASE_CHAT_ENTITY_FACADES: BaseEntityFacadeRegistry = {
  [CHAT_DEFINITION_ENTITY_NAME]: ChatDefinitionFacade,
};
