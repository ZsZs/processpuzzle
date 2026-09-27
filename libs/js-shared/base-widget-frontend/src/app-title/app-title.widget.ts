import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { AppTitleComponent } from './app-title.component';

export const APP_TITLE_WIDGET = 'app-title';

export const APP_TITLE_WIDGET_REGISTRATION: WidgetRegistration = {
  type: APP_TITLE_WIDGET,
  component: AppTitleComponent,
  definition: {
    name: 'Application title',
    translocoId: 'base_widget.app_title.name',
    description: "The application's name as a heading, coloured by the theme. Defaults to the name of the application it is placed in.",
    category: 'Navigation',
    icon: 'title',
    propsSchema: {
      type: 'object',
      properties: {
        title: {
          type: 'string',
          title: 'Title',
          description: "Overrides the application's name, e.g. inside a document.",
        },
      },
      additionalProperties: false,
    },
    inputPorts: [
      {
        name: 'title',
        type: 'STRING',
        required: false,
        description: 'The text of the heading.',
      },
    ],
  },
};
