import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { SidenavToggleComponent } from './sidenav-toggle.component';

export const SIDENAV_TOGGLE_WIDGET = 'sidenav-toggle';

export const SIDENAV_TOGGLE_WIDGET_REGISTRATION: WidgetRegistration = {
  type: SIDENAV_TOGGLE_WIDGET,
  component: SidenavToggleComponent,
  definition: {
    name: 'Sidenav toggle',
    translocoId: 'base_widget.sidenav_toggle.name',
    description: "Shows or hides the application's sidenav. Renders nothing when the application has no sidenav.",
    category: 'Navigation',
    icon: 'menu',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
  },
};
