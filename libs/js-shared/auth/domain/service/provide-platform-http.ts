import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { HttpInterceptorFn, provideHttpClient, withInterceptors } from '@angular/common/http';
import { centralHttpErrorInterceptor } from '@processpuzzle/util';
import { authTokenInterceptor } from './auth-token.interceptor';
import { sessionExpiredInterceptor } from './session-expired.interceptor';

export interface PlatformHttpOptions {
  /**
   * Absolute URL prefixes that count as this platform's own API, and so receive the bearer token and have their 401
   * treated as "session ended". Same-origin requests always qualify without being listed. Every entry is a host you
   * are willing to hand a working token: name the platform's own backends and nothing else. An undefined entry (a
   * root the configuration leaves out) is ignored.
   */
  readonly apiRoots?: readonly (string | undefined)[];
  /** URL prefixes that never receive a token, even when same-origin: a third-party service a reverse proxy publishes under the application's origin. */
  readonly withheldRoots?: readonly (string | undefined)[];
  /** The application's own interceptors that run **before** authentication: first on the way out, last on the way back. */
  readonly leadingInterceptors?: readonly HttpInterceptorFn[];
  /** The application's own interceptors that run **after** the central error interceptor, nearest the network: they handle an error before it is reported. */
  readonly trailingInterceptors?: readonly HttpInterceptorFn[];
}

/**
 * The platform's one HTTP client setup. Every application calls this instead of assembling a
 * `provideHttpClient(withInterceptors([...]))` chain of its own, so none can leave a link out or put one in the wrong place.
 *
 * The chain, outermost first:
 *
 * 1. {@link PlatformHttpOptions.leadingInterceptors}
 * 2. `authTokenInterceptor`: attaches the bearer token to this platform's own API
 * 3. `sessionExpiredInterceptor`: sends the user to sign in again when that API answers 401. It is outside the next
 *    one so that its message is not replaced by the generic snackbar
 * 4. `centralHttpErrorInterceptor`: logs and reports every failure
 * 5. {@link PlatformHttpOptions.trailingInterceptors}: inside the central one, so an error they turn into a normal
 *    answer (an unpublished registry's 404 becoming an empty catalog) is never reported
 *
 * One `provideHttpClient` only: two calls compete and one chain survives, which is why a library never registers its own.
 */
export function providePlatformHttp(options: PlatformHttpOptions = {}): EnvironmentProviders {
  const apiRoots = definedOnly(options.apiRoots);
  const withheldRoots = definedOnly(options.withheldRoots);

  return makeEnvironmentProviders([
    provideHttpClient(
      withInterceptors([
        ...(options.leadingInterceptors ?? []),
        authTokenInterceptor(apiRoots, withheldRoots),
        sessionExpiredInterceptor(apiRoots, withheldRoots),
        centralHttpErrorInterceptor,
        ...(options.trailingInterceptors ?? []),
      ]),
    ),
  ]);
}

/** A root the configuration leaves out is undefined or empty; passing it on would make `startsWith('')` match every URL. */
function definedOnly(roots: readonly (string | undefined)[] = []): string[] {
  return roots.filter((root): root is string => !!root);
}
