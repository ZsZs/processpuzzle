import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { MatCardsGridComponent } from './mat-cards-grid.component';

export const CARDS_GRID_WIDGET = 'cards-grid';

// Every card text is a transloco key, resolved under the card's own `translocoPrefix`; an untranslated key
// renders as itself, so a designer can type plain text too.
export const CARDS_GRID_WIDGET_REGISTRATION: WidgetRegistration = {
  type: CARDS_GRID_WIDGET,
  component: MatCardsGridComponent,
  definition: {
    name: 'Cards grid',
    translocoId: 'base_widget.cards_grid.name',
    description: 'A responsive grid of Material cards, each with a title, optional subtitle and text, action buttons and a menu.',
    category: 'Content',
    icon: 'grid_view',
    propsSchema: {
      type: 'object',
      properties: {
        cards: {
          type: 'array',
          title: 'Cards',
          items: {
            type: 'object',
            properties: {
              title: {
                type: 'string',
                title: 'Title',
              },
              subtitle: {
                type: 'string',
                title: 'Subtitle',
              },
              icon: {
                type: 'string',
                title: 'Icon',
              },
              content: {
                type: 'array',
                title: 'Content',
                description: 'The first entry is a paragraph, the rest a bulleted list.',
                items: {
                  type: 'string',
                },
              },
              actions: {
                type: 'array',
                title: 'Actions',
                items: {
                  type: 'object',
                  properties: {
                    caption: {
                      type: 'string',
                    },
                    link: {
                      type: 'string',
                    },
                    buttonType: {
                      type: 'string',
                      enum: ['text', 'filled', 'elevated', 'outlined', 'tonal'],
                    },
                  },
                  required: ['caption', 'link'],
                },
              },
              menuItems: {
                type: 'array',
                title: 'Menu items',
                items: {
                  type: 'object',
                  properties: {
                    label: {
                      type: 'string',
                    },
                    link: {
                      type: 'string',
                    },
                    icon: {
                      type: 'string',
                    },
                  },
                  required: ['label', 'link'],
                },
              },
              translocoPrefix: {
                type: 'string',
                title: 'Transloco prefix',
              },
            },
            required: ['title'],
          },
        },
      },
      required: ['cards'],
      additionalProperties: false,
    },
    inputPorts: [
      {
        name: 'cards',
        type: 'ARRAY',
        required: true,
        description: 'The cards to render, in order.',
      },
    ],
  },
};
