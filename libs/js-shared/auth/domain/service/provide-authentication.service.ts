import { effect, EnvironmentProviders, inject, InjectionToken, makeEnvironmentProviders, provideAppInitializer, provideEnvironmentInitializer } from '@angular/core';
import { KeycloakAuthConfig } from './keycloak-auth.config';
import { AuthService } from './auth.service';
import { BaseConfiguration, RUNTIME_CONFIGURATION, SessionUser } from '@processpuzzle/util';
import { KeycloakAuthService } from './keycloak-auth.service';
import { User } from '../user/user';

export const AUTHENTICATION_CONFIGURATION = new InjectionToken<AuthenticationConfiguration>('AUTHENTICATION_CONFIGURATION');
export const AUTHENTICATION_SERVICE = new InjectionToken<AuthService>('AUTHENTICATION_SERVICE');
/**
 * An optional application-owned location to use after logout.
 *
 * A public home page should use this instead of the route the user left: returning to a guarded
 * route after ending the SSO session immediately starts another login.
 */
export const LOGOUT_REDIRECT_URL = new InjectionToken<string>('LOGOUT_REDIRECT_URL');

export interface AuthenticationConfiguration {
  readonly AUTHENTICATION_PROVIDER?: 'local-auth' | 'oauth2' | 'keycloak';
  readonly AUTHENTICATION_SERVICE_ROOT?: string;
  readonly AUTH_SERVICE_CONFIG?: KeycloakAuthConfig;
  /**
   * Realm to authenticate against when `AUTH_SERVICE_CONFIG.realm` is a `{orgKey}` template and the
   * URL names no tenant — a landing page or a sign-up form.
   *
   * Read before `bootstrapApplication` by the hosting application's `main.ts`, not by anything in
   * this library: by the time these providers run the realm has already been substituted. It is
   * declared here so the value sits next to the template it qualifies in `run-time-conf`.
   */
  readonly FALLBACK_AUTH_REALM?: string;
}

// Single source of truth for which providers the factory below can construct.
// Extend this (and the matching branch in the factory) when implementing a new
// provider such as 'local-auth' or 'oauth2'. The app initializer relies on this to
// decide whether resolving AUTHENTICATION_SERVICE at bootstrap is safe — resolving a
// provider the factory can't build throws and permanently poisons the injector record.
function canBuildAuthService(authConfig: AuthenticationConfiguration): boolean {
  return authConfig.AUTHENTICATION_PROVIDER === 'keycloak' && !!authConfig.AUTH_SERVICE_CONFIG;
}

export function provideAuthenticationService(runtimeConfig: { BASE_CONFIGURATION: BaseConfiguration; AUTHENTICATION_CONFIGURATION: AuthenticationConfiguration }): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: AUTHENTICATION_SERVICE,
      useFactory: () => {
        const authConfig: AuthenticationConfiguration = runtimeConfig.AUTHENTICATION_CONFIGURATION;
        if (!canBuildAuthService(authConfig)) {
          throw new Error('No AUTHENTICATION_PROVIDER or AUTH_SERVICE_CONFIG configured. Verify your RUNTIME_CONFIGURATION.');
        }
        return new KeycloakAuthService(authConfig.AUTH_SERVICE_CONFIG as KeycloakAuthConfig);
      },
      deps: [RUNTIME_CONFIGURATION],
    },
    // Finalize the OIDC redirect callback and/or restore an existing SSO session
    // at bootstrap, so auth state is set no matter which route the user lands on
    // (and on every refresh) — independent of route guards.
    provideAppInitializer(async () => {
      // Skip when the factory can't build a service (misconfig / not-yet-implemented
      // provider): resolving it would throw and poison the injector record, so
      // consumers would later see a confusing NG0200 instead of the clear error.
      if (!canBuildAuthService(runtimeConfig.AUTHENTICATION_CONFIGURATION)) return;
      try {
        await inject(AUTHENTICATION_SERVICE).authenticate();
      } catch (e) {
        // Never block app bootstrap if the IdP is unreachable.
        console.error('Authentication initialization failed', e);
      }
    }),
    // Keeps util's SessionUser — what the feature libraries read — in step with the session. An effect, because the
    // user arrives after the OIDC redirect is finalized and a token refresh or sign-out replaces it.
    provideEnvironmentInitializer(() => {
      if (!canBuildAuthService(runtimeConfig.AUTHENTICATION_CONFIGURATION)) return;
      const authService = inject(AUTHENTICATION_SERVICE);
      const sessionUser = inject(SessionUser);
      effect(() => {
        const user = authService.currentUser();
        sessionUser.set({ userId: user?.id ?? '', displayName: displayNameOf(user) });
      });
    }),
  ]);
}

/** The full name, or the email when the identity provider knows no name. */
export function displayNameOf(user: User | undefined): string {
  const fullName = [user?.firstName, user?.lastName].filter((part) => !!part?.trim()).join(' ');
  return fullName || user?.email || '';
}
