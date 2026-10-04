import { createNavigationPropsSchema } from '../app-context/navigation-props-schema';
import { WidgetRegistration } from '../widget-registry/widget-registry.token';
import { NAV_BAR_VISIBILITIES, NavBarComponent } from './nav-bar.component';

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
    propsSchema: createNavigationPropsSchema(NAV_BAR_VISIBILITIES, 'Defaults to large screens; on a small one the navigation menu takes over.'),
  },
};
