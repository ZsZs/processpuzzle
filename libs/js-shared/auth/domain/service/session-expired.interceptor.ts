import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injector } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ERROR_MESSAGE_REPORTER } from '@processpuzzle/util';
import { AUTHENTICATION_SERVICE } from './provide-authentication.service';
import { AuthService } from './auth.service';
import { shouldAuthorize } from './auth-token.interceptor';
import { LOGIN_REQUIRED, SESSION_EXPIRED_ROUTE, sessionExpiredTree } from './session-expired.route';

/** `sessionStorage` key holding the time (ms since epoch) of the last session-expiry redirect to the login page. */
export const SESSION_EXPIRED_REDIRECT_KEY = 'pp.session-expired.redirected-at';

/** A second session-expiry redirect within this window is taken for a loop and not performed. */
export const SESSION_EXPIRED_LOOP_WINDOW_MS = 60_000;

export const SESSION_EXPIRED_MESSAGE = 'Your session has ended and signing in again did not restore it. Reload the page or sign in again to continue.';

/**
 * Reacts to the server answering 401 to a request for this platform's own API.
 *
 * ## A signed-in user: the session ended
 *
 * Keycloak's SSO idle or maximum lifetime ran out while the page stayed open, so the cached user and token are
 * dropped and the user is sent to the login page, to come back to the page they were on. Without this nothing
 * reacts: `authenticate()` answers from a user cached at start-up, so guards keep saying "signed in", pages open,
 * and every call fails with 401.
 *
 * ## An anonymous visitor: never an automatic login
 *
 * What an anonymous visitor may see is the designer's decision (public documents, role-filtered navigation), so a
 * 401 for somebody who was never signed in is not "your session ended" and does not send them to the login page.
 * It propagates to the caller. Only where the application says login is required ({@link LOGIN_REQUIRED}, which is
 * `false` unless an application provides it) is the visitor taken to the {@link SESSION_EXPIRED_ROUTE} guidance
 * page, which offers "Sign in again". Neither a route nor a page definition carries such a flag yet; the token is
 * where one plugs in.
 *
 * ## Which 401s count
 *
 * Those of requests {@link shouldAuthorize} accepts: the same rule as `authTokenInterceptor`, with the same
 * arguments, so Keycloak's own endpoints and third-party or withheld URLs never trigger anything. Only 401: a 403
 * means the signed-in user is not allowed, which signing in again does not change.
 *
 * ## One redirect, and not a loop
 *
 * Parallel 401s (a page fires several calls at once) start **one** login: the first one clears the user (which
 * `login` requires, since it returns early for a signed-in user) and the rest find nobody to expire.
 *
 * If the 401 is not about the session at all (the backend rejects a perfectly valid token), a silent SSO login
 * would bring the user straight back to the same 401 and the same redirect, for ever. So a redirect is recorded in
 * `sessionStorage` (the redirect replaces the document, so a variable would be lost) and a 401 within
 * {@link SESSION_EXPIRED_LOOP_WINDOW_MS} of it is **not** answered with another one. The error snackbar reports
 * it and the user is taken to the guidance page, whose button signs in again on purpose.
 *
 * ## The error is rethrown
 *
 * Always. The caller sees the failure it would have seen anyway, so nothing downstream mistakes the redirect for
 * success.
 *
 * ## Where to put it
 *
 * After `authTokenInterceptor` and **before** `centralHttpErrorInterceptor` in the list, i.e. outside it.
 * The error then arrives here after the central interceptor has logged it, so the loop-guard message replaces its
 * generic "Http failure response" snackbar instead of being replaced by it. `providePlatformHttp` fixes that order.
 *
 * @internal Registered by `providePlatformHttp`; applications do not assemble it themselves.
 * @param apiRoots the same roots handed to `authTokenInterceptor`
 * @param withheldRoots the same withheld roots handed to `authTokenInterceptor`
 */
export function sessionExpiredInterceptor(
  apiRoots: readonly string[] = [],
  withheldRoots: readonly string[] = [],
  now: () => number = () => Date.now(),
): HttpInterceptorFn {
  // Per interceptor instance, so one per application; survives only until the redirect replaces the document.
  let redirecting = false;

  return (request, next) => {
    if (!shouldAuthorize(request, apiRoots, withheldRoots)) return next(request);

    const authService = inject(AUTHENTICATION_SERVICE, { optional: true }) as AuthService | null;
    const reporter = inject(ERROR_MESSAGE_REPORTER, { optional: true });
    const loginRequired = inject(LOGIN_REQUIRED);
    const injector = inject(Injector);
    if (!authService) return next(request);

    const showGuidance = () => {
      const router = injector.get(Router);
      const route = injector.get(SESSION_EXPIRED_ROUTE);
      // Not again when already there: the page's own calls would otherwise navigate it onto itself.
      if (!router.url.split('?')[0].endsWith(route)) void router.navigateByUrl(sessionExpiredTree(router, route, currentPath()));
    };

    return next(request).pipe(
      catchError((error: unknown) => {
        if (!(error instanceof HttpErrorResponse) || error.status !== 401 || redirecting) return throwError(() => error);

        if (!authService.getCurrentUser()) {
          if (loginRequired()) showGuidance();
        } else if (startedRecently(now()) || !recordRedirect(now())) {
          reporter?.showErrorMessage(SESSION_EXPIRED_MESSAGE, error);
          showGuidance();
        } else {
          redirecting = true;
          authService.clearSession();
          authService.login(currentPath()).catch(() => {
            // The redirect did not happen, so the next 401 may try again.
            redirecting = false;
            forgetRedirect();
          });
        }
        return throwError(() => error);
      }),
    );
  };
}

/** The router URL of the page the user is on (path and query, relative to `<base href>`), for coming back to it. */
function currentPath(): string {
  const base = new URL(document.baseURI).pathname;
  const { pathname, search } = window.location;
  return (pathname.startsWith(base) ? pathname.substring(base.length) : pathname) + search;
}

function startedRecently(now: number): boolean {
  try {
    const at = Number(sessionStorage.getItem(SESSION_EXPIRED_REDIRECT_KEY));
    return Number.isFinite(at) && at > 0 && now - at < SESSION_EXPIRED_LOOP_WINDOW_MS;
  } catch {
    return false;
  }
}

/** False when the record cannot be kept: without it the loop guard cannot work, so no redirect is risked. */
function recordRedirect(now: number): boolean {
  try {
    sessionStorage.setItem(SESSION_EXPIRED_REDIRECT_KEY, String(now));
    return true;
  } catch {
    return false;
  }
}

function forgetRedirect(): void {
  try {
    sessionStorage.removeItem(SESSION_EXPIRED_REDIRECT_KEY);
  } catch {
    // Nothing recorded, nothing to forget.
  }
}
