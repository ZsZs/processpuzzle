import { EnvironmentProviders, inject, InjectionToken, makeEnvironmentProviders, provideAppInitializer } from '@angular/core';
import { KeycloakAuthConfig } from './keycloak-auth.config';
import { AuthService } from './auth.service';
import { FirebaseAuthConfig } from './firebase-auth.config';
import { BaseConfiguration, RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { KeycloakAuthService } from './keycloak-auth.service';
import { provideFirebaseAuthService } from './provide-firebase-auth-service';

export const AUTHENTICATION_CONFIGURATION = new InjectionToken<AuthenticationConfiguration>('AUTHENTICATION_CONFIGURATION');
export const AUTHENTICATION_SERVICE = new InjectionToken<AuthService>('AUTHENTICATION_SERVICE');

export interface AuthenticationConfiguration {
  readonly AUTHENTICATION_PROVIDER?: 'local-auth' | 'firebase-auth' | 'oauth2' | 'keycloak';
  readonly AUTHENTICATION_SERVICE_ROOT?: string;
  readonly AUTH_SERVICE_CONFIG?: KeycloakAuthConfig | FirebaseAuthConfig;
}

// Single source of truth for which providers the factory below can construct.
// Add a case here (and the matching branch in the factory) when implementing a new
// provider such as 'local-auth' or 'oauth2'. The app initializer relies on this to
// decide whether resolving AUTHENTICATION_SERVICE at bootstrap is safe — resolving a
// provider the factory can't build throws and permanently poisons the injector record.
function canBuildAuthService(authConfig: AuthenticationConfiguration): boolean {
  switch (authConfig.AUTHENTICATION_PROVIDER) {
    case 'firebase-auth':
      return true;
    case 'keycloak':
      return !!authConfig.AUTH_SERVICE_CONFIG;
    default:
      return false;
  }
}

export function provideAuthenticationService(runtimeConfig: { BASE_CONFIGURATION: BaseConfiguration; AUTHENTICATION_CONFIGURATION: AuthenticationConfiguration }): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: AUTHENTICATION_SERVICE,
      useFactory: () => {
        const baseConfig: BaseConfiguration = runtimeConfig.BASE_CONFIGURATION;
        const authConfig: AuthenticationConfiguration = runtimeConfig.AUTHENTICATION_CONFIGURATION;
        if (!canBuildAuthService(authConfig)) {
          throw new Error('No AUTHENTICATION_PROVIDER or AUTH_SERVICE_CONFIG configured. Verify your RUNTIME_CONFIGURATION.');
        }
        if (authConfig.AUTHENTICATION_PROVIDER === 'keycloak') {
          return new KeycloakAuthService(authConfig.AUTH_SERVICE_CONFIG as KeycloakAuthConfig);
        }
        return provideFirebaseAuthService(baseConfig, authConfig); // firebase-auth
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
  ]);
}
