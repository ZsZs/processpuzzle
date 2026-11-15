/*
 * Public API Surface of @processpuzzle/base-chat
 */
export { BASE_CHAT_TRANSLOCO_SCOPE } from './lib/base-chat.i18n';
export { provideChatWidgets } from './lib/base-chat.providers';

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
export { ChatUnreadBadgeComponent } from './lib/feature/unread/chat-unread-badge.component';
export { CHAT_UNREAD_BADGE_WIDGET, CHAT_UNREAD_BADGE_WIDGET_REGISTRATION, provideChatUnreadBadgeWidget } from './lib/feature/unread/chat-unread-badge.widget';
