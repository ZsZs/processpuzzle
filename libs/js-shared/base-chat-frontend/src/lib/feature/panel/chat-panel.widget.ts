import { Provider } from '@angular/core';
import { provideWidget, WidgetRegistration } from '@processpuzzle/widgets';
import { ChatPanelComponent } from './chat-panel.component';

export const CHAT_PANEL_WIDGET = 'chat-panel';

/**
 * Registered here, not in base-widget-frontend: the widget talks to base-chat's API, and the widget library
 * must not depend on a feature library. The seed catalogue carries a copy of this definition all the same, so a
 * designer can place it; `chat-widgets.contract.spec.ts` keeps the two equal.
 */
export const CHAT_PANEL_WIDGET_REGISTRATION: WidgetRegistration = {
  type: CHAT_PANEL_WIDGET,
  component: ChatPanelComponent,
  definition: {
    name: 'Chat panel',
    translocoId: 'base_chat.widgets.chat_panel',
    description: 'The conversation of one record — an order, say — live, with a message box. Left without a record it follows the active working context.',
    category: 'Social',
    icon: 'forum',
    propsSchema: {
      type: 'object',
      properties: {
        subjectId: {
          type: 'string',
          title: 'Record',
          description: 'Id of the record to chat about; the active working context when left empty.',
        },
        subjectType: {
          type: 'string',
          enum: ['ENTITY', 'WORKFLOW_INSTANCE'],
          title: 'Kind of record',
          description: 'What the record is — an entity instance, or a workflow instance; an entity instance when left empty.',
        },
        definitionKey: {
          type: 'string',
          title: 'Chat definition',
          description: 'Key of the chat definition to show, and to start from when the record has no conversation yet.',
        },
        conversationId: {
          type: 'string',
          title: 'Conversation',
          description: 'Id of one conversation to show, as the topics list does; the record and definition are then not used to find it.',
        },
      },
      additionalProperties: false,
    },
    inputPorts: [
      {
        name: 'subjectId',
        type: 'STRING',
        required: false,
        description: 'Id of the record to chat about.',
      },
    ],
  },
};

export function provideChatPanelWidget(): Provider[] {
  return provideWidget(CHAT_PANEL_WIDGET_REGISTRATION);
}
