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
import { PLATFORM_ADMIN_ENTITY_FACADES, PLATFORM_ADMIN_FACADE_PROVIDERS, PLATFORM_ADMIN_TRANSLATION_SOURCE } from '@processpuzzle/platform-admin';
import { RuntimeConfiguration } from './runtime-configuration';
import { appRoutes } from './app.routes';

export function createAppConfig(runtimeConfiguration: RuntimeConfiguration): ApplicationConfig {
  const platformAdminRoot = runtimeConfiguration.BASE_CONFIGURATION.PLATFORM_ADMIN_SERVICE_ROOT;

  return {
    providers: [
      provideZonelessChangeDetection(),
      // One `provideHttpClient` with both interceptors, rather than `provideAuthTokenInterceptor()`
      // next to a second `provideHttpClient` — two calls compete and only one chain survives.
      //
      // The auth interceptor is given the platform-admin root and nothing else. Every entry is a host
      // this application is willing to hand a working staff token to, and a staff token is the one
      // credential on this platform that can delete a tenant.
      provideHttpClient(withInterceptors([authTokenInterceptorFor(platformAdminRoot ? [platformAdminRoot] : []), centralHttpErrorInterceptor])),
      provideAuthenticationService(runtimeConfiguration),
      provideLoggingService(runtimeConfiguration.LOGGING_CONFIGURATION),
      provideCentralErrorHandler(),
      provideErrorSnackbar(),
      // The four facades this application's screens resolve `ACTIVE_ENTITY_FACADE` from. Application
      // level and not route level: `PLATFORM_ADMIN_ROUTES` binds them with `useExisting`, which needs
      // the instance to already exist — a route-level provider would create a second one per
      // activation and lose whatever the first had loaded.
      ...PLATFORM_ADMIN_FACADE_PROVIDERS,
      {
        provide: BASE_ENTITY_FACADE_REGISTRY,
        useValue: { ...PLATFORM_ADMIN_ENTITY_FACADES },
      },
      // Which backend serves which transloco scope, for bundles with no asset to fall back on. All
      // three contributions have to be here rather than on a route branch: a `multi` token is not
      // merged across injectors, so a route-level entry replaces the collection instead of extending
      // it.
      ...[PLATFORM_ADMIN_TRANSLATION_SOURCE, BASE_ENTITY_TRANSLATION_SOURCE, BASE_WIDGET_TRANSLATION_SOURCE].map((source) => ({
        provide: TRANSLATION_SOURCE_REGISTRY,
        useValue: source,
        multi: true,
      })),
      { provide: OVERLAY_DEFAULT_CONFIG, useValue: { usePopover: false } },
      { provide: RUNTIME_CONFIGURATION, useValue: runtimeConfiguration },
      { provide: AUTHENTICATION_CONFIGURATION, useValue: runtimeConfiguration.AUTHENTICATION_CONFIGURATION },
      provideRouter(appRoutes, withComponentInputBinding()),
      provideEntityRouteRegistry(),
      provideNativeDateAdapter(),
      provideTranslocoService(runtimeConfiguration.LANGUAGE_CONFIGURATION),
      LayoutService,
    ],
  };
}
