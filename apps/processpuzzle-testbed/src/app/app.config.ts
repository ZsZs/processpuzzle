import { APP_INITIALIZER, ApplicationConfig, provideZoneChangeDetection, SecurityContext } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { appRoutes } from './app.routes';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { AppInitializer, CONFIGURATION_APP_INITIALIZER, CONFIGURATION_OPTIONS, CONFIGURATION_TYPE, ConfigurationService, LayoutService, RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { RuntimeConfiguration } from './runtime-configuration';
import { provideNativeDateAdapter } from '@angular/material/core';
import { CLIPBOARD_OPTIONS, ClipboardButtonComponent, MERMAID_OPTIONS, provideMarkdown } from 'ngx-markdown';

export const appConfig: ApplicationConfig = {
  providers: [
    provideAnimationsAsync(),
    provideHttpClient(),
    provideRouter(appRoutes, withComponentInputBinding()),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideNativeDateAdapter(),
    AppInitializer,
    ConfigurationService,
    LayoutService,
    { provide: CONFIGURATION_TYPE, useValue: RuntimeConfiguration },
    { provide: RUNTIME_CONFIGURATION, useFactory: (configurationService: ConfigurationService<RuntimeConfiguration>) => configurationService.configuration, deps: [ConfigurationService] },
    { provide: CONFIGURATION_APP_INITIALIZER, useValue: [] },
    {
      provide: CONFIGURATION_OPTIONS,
      useValue: {
        urlFactory: () => {
          return ['environments/config.common.json', `run-time-conf/config.custom.json`];
        },
        log: true,
      },
    },
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: (initializer: AppInitializer) => {
        return async () => await initializer.init().then();
      },
      deps: [AppInitializer],
    },
    provideMarkdown({
      loader: HttpClient,
      sanitize: SecurityContext.NONE,
      mermaidOptions: {
        provide: MERMAID_OPTIONS,
        useValue: {
          darkMode: true,
          look: 'handDrawn',
        },
      },
      clipboardOptions: {
        provide: CLIPBOARD_OPTIONS,
        useValue: {
          buttonComponent: ClipboardButtonComponent,
        },
      },
    }),
  ],
};
