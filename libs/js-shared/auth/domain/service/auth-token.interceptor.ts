import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { AUTHENTICATION_SERVICE } from './provide-authentication.service';
import { AuthService } from './auth.service';

/**
 * Attaches `Authorization: Bearer <token>` to calls that go to this platform's own API.
 *
 * Registered with `provideAuthTokenInterceptor()`; nothing happens without it, which is deliberate —
 * an interceptor that attached tokens by merely being on the classpath would send credentials from
 * every application that happened to depend on this library.
 *
 * ## Which requests get a token
 *
 * Only same-origin requests and requests to a configured API root. **A bearer token must never be
 * attached to a third-party URL**: it is a credential for this platform, and sending it to an
 * arbitrary host hands that host a working token. So the default is same-origin, and any absolute
 * URL has to be named in `apiRoots` to qualify. Relative URLs — which is what
 * `BaseEntityRestService` produces — are same-origin by definition and always qualify.
 *
 * Keycloak's own endpoints are excluded even when they are same-origin: the token endpoint
 * authenticates with the refresh token in the body, and an `Authorization` header on it is at best
 * ignored and at worst rejected.
 *
 * ## Why it refreshes first
 *
 * `refreshAccessToken` is called before reading the token, so a screen left open past the token's
 * lifetime does not send an expired one. A failed refresh is not an error here: the request goes out
 * unauthenticated and the server answers 401, which reaches the user as "your session ended" rather
 * than as an exception from inside an HTTP pipeline.
 */
export function authTokenInterceptor(apiRoots: readonly string[] = []): HttpInterceptorFn {
  return (request, next) => {
    if (!shouldAuthorize(request, apiRoots)) return next(request);

    const authService = inject(AUTHENTICATION_SERVICE, { optional: true }) as AuthService | null;
    if (!authService) return next(request);

    return from(authService.refreshAccessToken()).pipe(
      switchMap(() => {
        const token = authService.getAccessToken();
        return next(token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request);
      }),
    );
  };
}

/** Keycloak's own endpoints authenticate by other means; an Authorization header there is noise at best. */
const IDENTITY_PROVIDER_PATHS = ['/realms/', '/protocol/openid-connect/'];

function shouldAuthorize(request: HttpRequest<unknown>, apiRoots: readonly string[]): boolean {
  const url = request.url;
  if (IDENTITY_PROVIDER_PATHS.some((path) => url.includes(path))) return false;
  if (!isAbsolute(url)) return true;
  if (isSameOrigin(url)) return true;
  return apiRoots.some((root) => url.startsWith(root));
}

function isAbsolute(url: string): boolean {
  return /^[a-z][a-z\d+\-.]*:\/\//i.test(url) || url.startsWith('//');
}

function isSameOrigin(url: string): boolean {
  try {
    return new URL(url, document.baseURI).origin === window.location.origin;
  } catch {
    // An unparseable URL is not something to guess about: withhold the credential.
    return false;
  }
}
