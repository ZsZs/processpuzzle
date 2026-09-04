import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { OVERLAY_DEFAULT_CONFIG } from '@angular/cdk/overlay';
import {
  centralHttpErrorInterceptor,
  LayoutService,
  provideCentralErrorHandler,
  provideErrorSnackbar,
  provideLoggingService,
  provideTranslocoService,
  RUNTIME_CONFIGURATION,
  TRANSLATION_SOURCE_REGISTRY,
} from '@processpuzzle/util';
import { AUTHENTICATION_CONFIGURATION, authTokenInterceptorFor, provideAuthenticationService } from '@processpuzzle/auth/domain';
import { BASE_ENTITY_FACADE_REGISTRY, BASE_ENTITY_TRANSLATION_SOURCE, provideEntityRouteRegistry } from '@processpuzzle/base-entity';
import { BASE_WIDGET_TRANSLATION_SOURCE } from '@processpuzzle/base-widget';
import { ORG_ADMIN_ENTITY_FACADES, ORG_ADMIN_FACADE_PROVIDERS, ORG_ADMIN_ORG_KEY, ORG_ADMIN_TRANSLATION_SOURCE } from '@processpuzzle/org-admin';
import { RuntimeConfiguration } from './runtime-configuration';
import { createAppRoutes } from './app.routes';

/**
 * @param runtimeConfiguration the deep-merged `run-time-conf` files, with the tenant realm already
 *   substituted by `main.ts`
 * @param orgKey the tenant the URL named, or `undefined` on the landing page
 */
export function createAppConfig(runtimeConfiguration: RuntimeConfiguration, orgKey?: string): ApplicationConfig {
  const orgAdminRoot = runtimeConfiguration.BASE_CONFIGURATION.ORG_ADMIN_SERVICE_ROOT;

  return {
    providers: [
      provideZonelessChangeDetection(),
      // One `provideHttpClient` with both interceptors, rather than `provideAuthTokenInterceptor()`
      // next to a second `provideHttpClient` — two calls compete and only one chain survives.
      //
      // The auth interceptor is given the org-admin root and nothing else: that token is minted in
      // this tenant's realm and is meaningful to no other host.
      provideHttpClient(withInterceptors([authTokenInterceptorFor(orgAdminRoot ? [orgAdminRoot] : []), centralHttpErrorInterceptor])),
      provideAuthenticationService(runtimeConfiguration),
      provideLoggingService(runtimeConfiguration.LOGGING_CONFIGURATION),
      provideCentralErrorHandler(),
      provideErrorSnackbar(),
      // Application level, not route level, and for two separate reasons. `ORG_ADMIN_ORG_KEY` is
      // injected while `OrganizationUserService` — `providedIn: 'root'` — is constructed, which
      // happens before any route activates. And `ORG_ADMIN_ROUTES` binds `ACTIVE_ENTITY_FACADE`
      // with `useExisting`, which needs the facade instance to already exist; a route-level provider
      // would create a second one per activation and lose whatever the first had loaded.
      { provide: ORG_ADMIN_ORG_KEY, useValue: orgKey ?? '' },
      ...ORG_ADMIN_FACADE_PROVIDERS,
      {
        provide: BASE_ENTITY_FACADE_REGISTRY,
        useValue: { ...ORG_ADMIN_ENTITY_FACADES },
      },
      // Which backend serves which transloco scope, for bundles with no asset to fall back on. All
      // three contributions have to be here rather than on a route branch: a `multi` token is not
      // merged across injectors, so a route-level entry replaces the collection instead of extending
      // it.
      ...[ORG_ADMIN_TRANSLATION_SOURCE, BASE_ENTITY_TRANSLATION_SOURCE, BASE_WIDGET_TRANSLATION_SOURCE].map((source) => ({
        provide: TRANSLATION_SOURCE_REGISTRY,
        useValue: source,
        multi: true,
      })),
      { provide: OVERLAY_DEFAULT_CONFIG, useValue: { usePopover: false } },
      { provide: RUNTIME_CONFIGURATION, useValue: runtimeConfiguration },
      { provide: AUTHENTICATION_CONFIGURATION, useValue: runtimeConfiguration.AUTHENTICATION_CONFIGURATION },
      provideRouter(createAppRoutes(orgKey), withComponentInputBinding()),
      provideEntityRouteRegistry(),
      provideNativeDateAdapter(),
      provideTranslocoService(runtimeConfiguration.LANGUAGE_CONFIGURATION),
      LayoutService,
    ],
  };
}
