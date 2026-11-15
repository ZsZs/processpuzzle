import { Provider } from '@angular/core';
import { provideChatPanelWidget } from './feature/panel/chat-panel.widget';
import { provideChatUnreadBadgeWidget } from './feature/unread/chat-unread-badge.widget';

/**
 * Registers the two chat widgets — `chat-panel` and `chat-unread-badge` — with the widget registry, so a designer
 * can place them and a run-time shell can render them. Spread into the application's providers.
 *
 * What the widgets need from the application is supplied separately, where it is composed: the identity
 * (`CHAT_IDENTITY`) and, optionally, the active working context (`provideChatWorkingContextSubject()`).
 */
export function provideChatWidgets(): Provider[] {
  return [...provideChatPanelWidget(), ...provideChatUnreadBadgeWidget()];
}
