import { Provider } from '@angular/core';
import { provideWidget, WidgetRegistration } from '@processpuzzle/widgets';
import { AdminButtonComponent } from './admin-button.component';

export const ADMIN_BUTTON_WIDGET = 'admin-button';

/**
 * Registered here, not in base-widget-frontend, for the reason {@link AUTH_BUTTON_WIDGET_REGISTRATION} gives: it belongs to
 * a feature library. The seed catalogue carries a copy of this definition, which `admin-button.widget.spec.ts` keeps equal.
 */
export const ADMIN_BUTTON_WIDGET_REGISTRATION: WidgetRegistration = {
  type: ADMIN_BUTTON_WIDGET,
  component: AdminButtonComponent,
  definition: {
    name: 'Administration button',
    translocoId: 'base_widget.admin_button.name',
    description: 'Opens the administration of the organization, with its users, roles and subscription.',
    category: 'Navigation',
    icon: 'admin_panel_settings',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
  },
};

export function provideAdminButtonWidget(): Provider[] {
  return provideWidget(ADMIN_BUTTON_WIDGET_REGISTRATION);
}
