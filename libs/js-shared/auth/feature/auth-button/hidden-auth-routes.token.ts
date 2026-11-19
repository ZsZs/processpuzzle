import { InjectionToken } from '@angular/core';

/**
 * Paths of the auth routes the button's menu leaves out — `['register']` for an application whose users are invited, not
 * self-registered. Provided by the application, so that the button is the same wherever it is placed: in a header the
 * designer composed as in a host that adds it itself.
 */
export const HIDDEN_AUTH_ROUTES = new InjectionToken<readonly string[]>('HIDDEN_AUTH_ROUTES');
