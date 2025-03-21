import { APP_INITIALIZER, ApplicationConfig, inject, provideZoneChangeDetection, SecurityContext } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { appRoutes } from './app.routes';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { AppInitializer, CONFIGURATION_APP_INITIALIZER, CONFIGURATION_OPTIONS, CONFIGURATION_TYPE, ConfigurationService, LayoutService, RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { RuntimeConfiguration } from './runtime-configuration';
import { provideNativeDateAdapter } from '@angular/material/core';
import { CLIPBOARD_OPTIONS, ClipboardButtonComponent, MERMAID_OPTIONS, provideMarkdown } from 'ngx-markdown';
import { initializeApp } from 'firebase/app';
import { provideFirebaseApp } from '@angular/fire/app';
import { FIREBASE_OPTIONS } from '@angular/fire/compat';
import { connectFirestoreEmulator, getFirestore, provideFirestore } from '@angular/fire/firestore';
import { connectAuthEmulator, getAuth, provideAuth } from '@angular/fire/auth';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideAnimationsAsync(),
    provideAuth(() => {
      const auth = getAuth();
      const pipelineStage = environment.PIPELINE_STAGE || 'CI';
      if (pipelineStage === 'DEV') connectAuthEmulator(auth, `http://localhost:9099`);
      else if (pipelineStage === 'CI') connectAuthEmulator(auth, `http://firebase:9099`);
      return auth;
    }),
    provideFirebaseApp(() => initializeApp(inject(FIREBASE_OPTIONS)), [FIREBASE_OPTIONS]),
    {
      provide: FIREBASE_OPTIONS,
      useFactory: () => {
        const runtimeConfig: RuntimeConfiguration = inject(RUNTIME_CONFIGURATION);
        return runtimeConfig.FIREBASE_CONFIG;
      },
      deps: [RUNTIME_CONFIGURATION],
    },
    provideFirestore(() => {
      const firestore = getFirestore();
      const pipelineStage = environment.PIPELINE_STAGE || 'CI';
      if (pipelineStage === 'DEV') connectFirestoreEmulator(firestore, 'localhost', 8080);
      else if (pipelineStage === 'CI') connectFirestoreEmulator(firestore, 'firebase', 9090);
      return firestore;
    }),
    provideHttpClient(),
    provideRouter(appRoutes, withComponentInputBinding()),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideNativeDateAdapter(),
    AppInitializer,
    ConfigurationService,
    LayoutService,
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: (initializer: AppInitializer) => {
        return async () => await initializer.init().then();
      },
      deps: [AppInitializer],
    },
    { provide: CONFIGURATION_TYPE, useValue: RuntimeConfiguration },
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
      provide: RUNTIME_CONFIGURATION,
      useFactory: (configurationService: ConfigurationService<RuntimeConfiguration>) => {
        const config: RuntimeConfiguration = configurationService.configuration;
        const apiKey = { apiKey: environment.FIREBASE_API_KEY || '' };
        const firebaseConfig = { ...config.FIREBASE_CONFIG, ...apiKey };

        return { ...config, ...{ FIREBASE_CONFIG: firebaseConfig } };
      },
      deps: [ConfigurationService],
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
