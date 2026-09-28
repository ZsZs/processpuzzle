import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { NavBarComponent } from './nav-bar.component';

export const NAV_BAR_WIDGET = 'nav-bar';

export const NAV_BAR_WIDGET_REGISTRATION: WidgetRegistration = {
  type: NAV_BAR_WIDGET,
  component: NavBarComponent,
  definition: {
    name: 'Navigation bar',
    translocoId: 'base_widget.nav_bar.name',
    description: "The application's navigation as a row of links, a group opening a drop-down. Hidden on small screens, where the navigation menu takes over.",
    category: 'Navigation',
    icon: 'more_horiz',
    propsSchema: {
      type: 'object',
      properties: {
        visibility: {
          type: 'string',
          title: 'Visibility',
          enum: ['large-screens', 'always'],
          description: 'Defaults to large screens; on a small one the navigation menu takes over.',
        },
        items: {
          type: 'array',
          title: 'Items',
          description: "Overrides the application's navigation.",
          items: {
            type: 'object',
            properties: {
              id: {
                type: 'string',
              },
              label: {
                type: 'string',
              },
              translocoId: {
                type: 'string',
              },
              icon: {
                type: 'string',
              },
              routePath: {
                type: 'string',
              },
              children: {
                type: 'array',
              },
            },
            required: ['id', 'label'],
          },
        },
      },
      additionalProperties: false,
    },
  },
};
