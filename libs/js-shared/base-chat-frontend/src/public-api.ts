/*
 * Public API Surface of @processpuzzle/base-chat
 */
export { BASE_CHAT_TRANSLATION_SOURCE, BASE_CHAT_TRANSLOCO_SCOPE, CHAT_DEFINITION_I18N_SCOPE, ENTITY_CHAT_I18N_KEY } from './lib/base-chat.i18n';
export { BASE_CHAT_ENTITY_FACADES, BASE_CHAT_FACADE_PROVIDERS, provideChatWidgets } from './lib/base-chat.providers';
export { BASE_CHAT_ROUTES } from './lib/base-chat.routes';
export { ChatDefinition, ChatLifecycleKind, ChatMultiplicity, ChatSubjectKind } from './lib/domain/chat-definition';
export { CHAT_DEFINITION_ENTITY_NAME, createChatDefinitionDescriptor } from './lib/domain/chat-definition.descriptors';
export { ChatDefinitionMapper, type ChatDefinitionDto } from './lib/domain/chat-definition.mapper';
export { ChatDefinitionService } from './lib/domain/chat-definition.service';
export { ChatDefinitionStore } from './lib/domain/chat-definition.store';
export { ChatDefinitionFacade } from './lib/feature/definition/chat-definition.facade';
export { ENTITY_CHAT_TAB, ENTITY_CHAT_TAB_SEGMENT, EntityChatTabContributor, provideEntityChatTab } from './lib/feature/entity/entity-chat-tab.contributor';
export { EntityChatTabComponent } from './lib/feature/entity/entity-chat-tab.component';

export { CHAT_ACTIVE_SUBJECT, CHAT_FETCH, CHAT_IDENTITY, type ChatFetch, type ChatIdentity } from './lib/domain/chat-identity';
export { ChatService, failureKindOf } from './lib/domain/chat.service';
export { ChatStore, type ConversationView, type PendingMessage } from './lib/domain/chat.store';
export { ChatStreamService, type ChatStreamClient, type ChatStreamEvent, type ChatStreamState } from './lib/domain/chat-stream.service';
export {
  newClientMessageId,
  unreadOf,
  type ChatFailureKind,
  type ChatMessage,
  type ChatSubjectRef,
  type ChatSubjectType,
  type Conversation,
  type ConversationPermissions,
  type ConversationStatus,
  type MessageKind,
  type PostedMessage,
  type ReadState,
} from './lib/domain/chat.types';

export { provideChatWorkingContextSubject } from './lib/feature/context/chat-working-context';
export { ChatPanelComponent } from './lib/feature/panel/chat-panel.component';
export { CHAT_PANEL_WIDGET, CHAT_PANEL_WIDGET_REGISTRATION, provideChatPanelWidget } from './lib/feature/panel/chat-panel.widget';
export { ChatTopicsComponent } from './lib/feature/topics/chat-topics.component';
export { CHAT_TOPICS_WIDGET, CHAT_TOPICS_WIDGET_REGISTRATION, provideChatTopicsWidget } from './lib/feature/topics/chat-topics.widget';
export { ChatUnreadBadgeComponent } from './lib/feature/unread/chat-unread-badge.component';
export { CHAT_UNREAD_BADGE_WIDGET, CHAT_UNREAD_BADGE_WIDGET_REGISTRATION, provideChatUnreadBadgeWidget } from './lib/feature/unread/chat-unread-badge.widget';
