import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { NavMenuComponent } from './nav-menu.component';

export const NAV_MENU_WIDGET = 'nav-menu';

export const NAV_MENU_WIDGET_REGISTRATION: WidgetRegistration = {
  type: NAV_MENU_WIDGET,
  component: NavMenuComponent,
  definition: {
    name: 'Navigation menu',
    translocoId: 'base_widget.nav_menu.name',
    description: "The application's navigation as a drop-down menu, standing in for the sidenav on small screens.",
    category: 'Navigation',
    icon: 'menu',
    propsSchema: {
      type: 'object',
      properties: {
        visibility: {
          type: 'string',
          title: 'Visibility',
          enum: ['small-screens', 'always'],
          description: 'Defaults to small screens, where the sidenav is hidden.',
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
