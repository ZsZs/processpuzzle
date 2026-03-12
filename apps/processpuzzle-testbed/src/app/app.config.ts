import { ApplicationConfig, importProvidersFrom, inject, provideAppInitializer, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { appRoutes } from './app.routes';
import { provideAnimations } from '@angular/platform-browser/animations';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import {
  AppInitializer,
  BaseConfiguration,
  CONFIGURATION_APP_INITIALIZER,
  CONFIGURATION_OPTIONS,
  ConfigurationService,
  FirebaseConfig,
  LayoutService,
  RUNTIME_CONFIGURATION,
} from '@processpuzzle/util';
import { RuntimeConfiguration } from './runtime-configuration';
import { provideNativeDateAdapter } from '@angular/material/core';
import { CLIPBOARD_OPTIONS, ClipboardButtonComponent, MERMAID_OPTIONS, provideMarkdown } from 'ngx-markdown';
import { initializeApp } from 'firebase/app';
import { provideFirebaseApp } from '@angular/fire/app';
import { FIREBASE_OPTIONS } from '@angular/fire/compat';
import { connectFirestoreEmulator, getFirestore, provideFirestore } from '@angular/fire/firestore';
import { environment } from '../environments/environment';
import { provideAppPropertyStore, WidgetsModule } from '@processpuzzle/widgets';
import { AUTHENTICATION_CONFIGURATION, provideAuthenticationService } from '@processpuzzle/auth/domain';
import { OVERLAY_DEFAULT_CONFIG } from '@angular/cdk/overlay';

export const appConfig: ApplicationConfig = {
  providers: [
    importProvidersFrom(WidgetsModule),
    provideAppInitializer(() => {
      const initializer = inject(AppInitializer);
      return initializer.init();
    }),
    provideAppPropertyStore(),
    provideAnimations(),
    { provide: OVERLAY_DEFAULT_CONFIG, useValue: { usePopover: false } },
    { provide: CONFIGURATION_APP_INITIALIZER, useValue: [] },
    {
      provide: CONFIGURATION_OPTIONS,
      useValue: {
        urlFactory: () => {
          const pipelineStage = environment.PIPELINE_STAGE ?? 'ci';
          return ['run-time-conf/config.common.json', `run-time-conf/config.${pipelineStage.toLocaleLowerCase()}.json`];
        },
        log: true,
      },
    },
    ConfigurationService,
    {
      provide: RUNTIME_CONFIGURATION,
      useFactory: (configurationService: ConfigurationService<RuntimeConfiguration>) => {
        const config: RuntimeConfiguration = configurationService.configuration;
        let baseConfig: BaseConfiguration = config.BASE_CONFIGURATION;
        const apiKey = { apiKey: environment.FIREBASE_API_KEY ?? '' };
        const firebaseConfig: FirebaseConfig = { ...baseConfig.FIREBASE_CONFIGURATION, ...apiKey };
        baseConfig = { ...baseConfig, ...{ FIREBASE_CONFIGURATION: firebaseConfig } };
        return { ...config, ...{ BASE_CONFIGURATION: baseConfig } };
      },
      deps: [ConfigurationService],
    },
    {
      provide: AUTHENTICATION_CONFIGURATION,
      useFactory: () => {
        const runtimeConfiguration: RuntimeConfiguration = inject<RuntimeConfiguration>(RUNTIME_CONFIGURATION);
        return runtimeConfiguration.AUTHENTICATION_CONFIGURATION;
      },
      deps: [RUNTIME_CONFIGURATION],
    },
    provideAuthenticationService(),
    provideZonelessChangeDetection(),
    {
      provide: FIREBASE_OPTIONS,
      useFactory: () => {
        const runtimeConfig: RuntimeConfiguration = inject<RuntimeConfiguration>(RUNTIME_CONFIGURATION);
        return runtimeConfig.BASE_CONFIGURATION.FIREBASE_CONFIGURATION;
      },
      deps: [RUNTIME_CONFIGURATION],
    },
    provideFirebaseApp(() => {
      const firebaseConfig: FirebaseConfig = inject(FIREBASE_OPTIONS);
      return initializeApp(firebaseConfig);
    }, [FIREBASE_OPTIONS]),
    provideFirestore(() => {
      const firestore = getFirestore();
      const pipelineStage = environment.PIPELINE_STAGE ?? 'ci';
      if (pipelineStage === 'dev') connectFirestoreEmulator(firestore, 'localhost', 8080);
      else if (pipelineStage === 'ci') connectFirestoreEmulator(firestore, 'firebase', 9090);
      return firestore;
    }),
    provideHttpClient(),
    provideRouter(appRoutes, withComponentInputBinding()),
    provideNativeDateAdapter(),
    AppInitializer,
    LayoutService,
    provideMarkdown({
      loader: HttpClient,
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
