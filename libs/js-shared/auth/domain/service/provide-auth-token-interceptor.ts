import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptors } from '@angular/common/http';
import { authTokenInterceptor } from './auth-token.interceptor';

/**
 * Registers {@link authTokenInterceptor} for this application.
 *
 * Opt-in rather than automatic: an interceptor that attached credentials merely by being on the
 * classpath would send this platform's tokens from every application that happens to depend on
 * `@processpuzzle/auth`.
 *
 * @param apiRoots absolute URL prefixes that count as this platform's own API. Same-origin requests
 *   are always authorized without being listed; an absolute URL to another origin is authorized only
 *   if it starts with one of these. Pass the backend's root when the SPA and the API are on different
 *   origins, which is the normal case in development (`http://localhost:8080`) — and pass nothing
 *   else, because every entry here is a host you are willing to hand a working token.
 */
export function provideAuthTokenInterceptor(apiRoots: readonly string[] = []): EnvironmentProviders {
  return makeEnvironmentProviders([provideHttpClient(withInterceptors([authTokenInterceptor(apiRoots)]))]);
}

/**
 * The interceptor alone, for an application that already calls `provideHttpClient(withInterceptors(...))`
 * itself and wants to add this to its own list rather than have a second `provideHttpClient` compete
 * with it.
 */
export function authTokenInterceptorFor(apiRoots: readonly string[] = []) {
  return authTokenInterceptor(apiRoots);
}

// Re-exported so a consumer reading this file does not have to guess where the DI token lives when
// wiring the class-based interceptor chain of an older application.
export { HTTP_INTERCEPTORS };
