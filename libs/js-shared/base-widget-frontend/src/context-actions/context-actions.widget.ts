import { WorkingContextActionsComponent } from '@processpuzzle/base-entity';
import { WidgetRegistration } from '../widget-registry/widget-registry.token';

export const CONTEXT_ACTIONS_WIDGET = 'context-actions';

// The component is base-entity's own: its inputs, `context` and `triggers`, are the props below. Which transitions it
// offers and what they are called comes from the entity's state machine, so there is nothing to author but the names.
export const CONTEXT_ACTIONS_WIDGET_REGISTRATION: WidgetRegistration = {
  type: CONTEXT_ACTIONS_WIDGET,
  component: WorkingContextActionsComponent,
  definition: {
    name: 'Context actions',
    translocoId: 'base_widget.context_actions.name',
    description: 'Buttons for the transitions the active entity of a working context can take, such as Start race and Finish race. Renders nothing without an active context.',
    category: 'Navigation',
    icon: 'play_arrow',
    propsSchema: {
      type: 'object',
      properties: {
        context: {
          type: 'string',
          title: 'Context',
          description: 'Name of the working context whose active entity the buttons act on.',
        },
        triggers: {
          type: 'array',
          title: 'Triggers',
          description: 'The transitions to offer, by trigger, in this order. All available transitions when empty.',
          items: { type: 'string' },
        },
      },
      required: ['context'],
      additionalProperties: false,
    },
  },
};
