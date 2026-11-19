import { Provider } from '@angular/core';
import { provideWidget, WidgetRegistration } from '@processpuzzle/widgets';
import { AuthButtonComponent } from './auth-button.component';

export const AUTH_BUTTON_WIDGET = 'auth-button';

/**
 * Registered here, not in base-widget-frontend: the button talks to the authentication service, and the widget library
 * must not depend on `auth` (which depends on it). The seed catalogue carries a copy of this definition all the same, so a
 * designer can place it; `auth-button.widget.spec.ts` keeps the two equal.
 */
export const AUTH_BUTTON_WIDGET_REGISTRATION: WidgetRegistration = {
  type: AUTH_BUTTON_WIDGET,
  component: AuthButtonComponent,
  definition: {
    name: 'Sign-in button',
    translocoId: 'base_widget.auth_button.name',
    description: 'The signed-in user\'s avatar with the menu to sign in, sign out and open the profile.',
    category: 'Navigation',
    icon: 'account_circle',
    propsSchema: {
      type: 'object',
      properties: {},
      additionalProperties: false,
    },
  },
};

export function provideAuthButtonWidget(): Provider[] {
  return provideWidget(AUTH_BUTTON_WIDGET_REGISTRATION);
}
