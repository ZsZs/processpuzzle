import { Provider } from '@angular/core';
import { provideWidget, WidgetRegistration } from '@processpuzzle/widgets';
import { ChatTopicsComponent } from './chat-topics.component';

export const CHAT_TOPICS_WIDGET = 'chat-topics';

/** Registered here for the same reason as the panel: see {@link CHAT_PANEL_WIDGET_REGISTRATION}. */
export const CHAT_TOPICS_WIDGET_REGISTRATION: WidgetRegistration = {
  type: CHAT_TOPICS_WIDGET,
  component: ChatTopicsComponent,
  definition: {
    name: 'Chat topics',
    translocoId: 'base_chat.widgets.chat_topics',
    description: "The titled topics of one record's chat — a protest under a race, say — with a way to start a new one, and the selected topic's conversation beside them.",
    category: 'Social',
    icon: 'forum',
    propsSchema: {
      type: 'object',
      properties: {
        subjectId: {
          type: 'string',
          title: 'Record',
          description: 'Id of the record whose topics are listed; the active working context when left empty.',
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
          description: 'Key of the chat definition, which has to be one with several topics per record.',
        },
      },
      additionalProperties: false,
    },
    inputPorts: [
      {
        name: 'subjectId',
        type: 'STRING',
        required: false,
        description: 'Id of the record whose topics are listed.',
      },
    ],
  },
};

export function provideChatTopicsWidget(): Provider[] {
  return provideWidget(CHAT_TOPICS_WIDGET_REGISTRATION);
}
