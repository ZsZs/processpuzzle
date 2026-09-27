import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { AppLogoComponent } from './app-logo.component';

export const APP_LOGO_WIDGET = 'app-logo';

export const APP_LOGO_WIDGET_REGISTRATION: WidgetRegistration = {
  type: APP_LOGO_WIDGET,
  component: AppLogoComponent,
  definition: {
    name: 'Application logo',
    translocoId: 'base_widget.app_logo.name',
    description: 'A small logo linking home. Defaults to the logo of the application it is placed in.',
    category: 'Navigation',
    icon: 'image',
    propsSchema: {
      type: 'object',
      properties: {
        logo: {
          type: 'object',
          format: 'artifact',
          title: 'Logo',
          description: "An uploaded image; overrides the application's logo.",
        },
        alt: {
          type: 'string',
          title: 'Alternative text',
        },
        link: {
          type: 'string',
          title: 'Link',
          description: 'Route the logo navigates to. Defaults to /; empty for no link.',
        },
      },
      additionalProperties: false,
    },
  },
};
