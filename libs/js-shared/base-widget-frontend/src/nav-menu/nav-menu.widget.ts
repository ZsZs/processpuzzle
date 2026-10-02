import { createNavigationPropsSchema } from '../app-context/navigation-props-schema';
import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { NAV_MENU_VISIBILITIES, NavMenuComponent } from './nav-menu.component';

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
    propsSchema: createNavigationPropsSchema(NAV_MENU_VISIBILITIES, 'Defaults to small screens, where the sidenav is hidden.'),
  },
};
