import { InjectionToken } from '@angular/core';
import { Router, UrlTree } from '@angular/router';

/**
 * Where the "Your session has ended" guidance page is mounted: the auth feature's `session-expired` route, reached
 * by the same `authMatcher` as the other auth pages. Provide another value only for an application that mounts
 * the page elsewhere.
 */
export const SESSION_EXPIRED_ROUTE = new InjectionToken<string>('SESSION_EXPIRED_ROUTE', { factory: () => '/auth/session-expired' });

/**
 * Whether the application, where the user is now, requires a signed-in user. It is the one thing that makes a 401 for
 * an *anonymous* visitor worth a guidance page. Defaults to "no": what an anonymous visitor may see is the designer's
 * decision, and an application with public pages must keep working. An application whose every page needs a login
 * provides `() => true`; a designer-level "login required" flag, once there is one, plugs in here.
 */
export const LOGIN_REQUIRED = new InjectionToken<() => boolean>('LOGIN_REQUIRED', { factory: () => () => false });

/** The guidance page, remembering `returnUrl`: the router URL that "Sign in again" comes back to. */
export function sessionExpiredTree(router: Router, route: string, returnUrl?: string): UrlTree {
  return router.createUrlTree([route], returnUrl ? { queryParams: { returnUrl } } : {});
}
