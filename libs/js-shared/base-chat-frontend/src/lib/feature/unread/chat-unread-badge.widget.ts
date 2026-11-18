import { Provider } from '@angular/core';
import { provideWidget, WidgetRegistration } from '@processpuzzle/widgets';
import { ChatUnreadBadgeComponent } from './chat-unread-badge.component';

export const CHAT_UNREAD_BADGE_WIDGET = 'chat-unread-badge';

/** Registered here for the same reason as the panel: see {@link CHAT_PANEL_WIDGET_REGISTRATION}. */
export const CHAT_UNREAD_BADGE_WIDGET_REGISTRATION: WidgetRegistration = {
  type: CHAT_UNREAD_BADGE_WIDGET,
  component: ChatUnreadBadgeComponent,
  definition: {
    name: 'Unread chat messages',
    translocoId: 'base_chat.widgets.unread_badge',
    description: 'A small number showing how many chat messages the user has not read yet, across every conversation they follow. Nothing is shown when there are none.',
    category: 'Social',
    icon: 'mark_chat_unread',
    propsSchema: {
      type: 'object',
      properties: {
        max: {
          type: 'number',
          title: 'Largest number shown',
          description: 'More unread messages than this show as, for example, 99+.',
          default: 99,
        },
      },
      additionalProperties: false,
    },
  },
};

export function provideChatUnreadBadgeWidget(): Provider[] {
  return provideWidget(CHAT_UNREAD_BADGE_WIDGET_REGISTRATION);
}
