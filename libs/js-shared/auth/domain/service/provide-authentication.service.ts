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

export function provideAuthenticationService(runtimeConfig: { BASE_CONFIGURATION: BaseConfiguration; AUTHENTICATION_CONFIGURATION: AuthenticationConfiguration }): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: AUTHENTICATION_SERVICE,
      useFactory: () => {
        const baseConfig: BaseConfiguration = runtimeConfig.BASE_CONFIGURATION;
        const authConfig: AuthenticationConfiguration = runtimeConfig.AUTHENTICATION_CONFIGURATION;
        if (authConfig.AUTHENTICATION_PROVIDER === 'keycloak' && authConfig.AUTH_SERVICE_CONFIG) {
          return new KeycloakAuthService(authConfig.AUTH_SERVICE_CONFIG as KeycloakAuthConfig);
        } else if (authConfig.AUTHENTICATION_PROVIDER === 'firebase-auth') {
          return provideFirebaseAuthService(baseConfig, authConfig);
        } else {
          throw new Error('No AUTHENTICATION_PROVIDER or AUTH_SERVICE_CONFIG configured. Verify your RUNTIME_CONFIGURATION.');
        }
      },
      deps: [RUNTIME_CONFIGURATION],
    },
    // Finalize the OIDC redirect callback and/or restore an existing SSO session
    // at bootstrap, so auth state is set no matter which route the user lands on
    // (and on every refresh) — independent of route guards.
    provideAppInitializer(async () => {
      const authService = inject(AUTHENTICATION_SERVICE);
      try {
        await authService.authenticate();
      } catch (e) {
        // Never block app bootstrap if the IdP is unreachable.
        console.error('Authentication initialization failed', e);
      }
    }),
  ]);
}
