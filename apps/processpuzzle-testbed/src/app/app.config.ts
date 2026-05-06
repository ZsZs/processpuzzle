import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { appRoutes } from './app.routes';
import { provideAnimations } from '@angular/platform-browser/animations';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { centralHttpErrorInterceptor, LayoutService, provideCentralErrorHandler, provideLoggingService, RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { RuntimeConfiguration } from './runtime-configuration';
import { provideNativeDateAdapter } from '@angular/material/core';
import { CLIPBOARD_OPTIONS, ClipboardButtonComponent, MERMAID_OPTIONS, provideMarkdown } from 'ngx-markdown';
import { initializeApp } from 'firebase/app';
import { provideFirebaseApp } from '@angular/fire/app';
import { FIREBASE_OPTIONS } from '@angular/fire/compat';
import { connectFirestoreEmulator, getFirestore, provideFirestore } from '@angular/fire/firestore';
import { environment } from '../environments/environment';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { provideAppPropertyStore, provideErrorSnackbar, provideTranslocoService } from '@processpuzzle/widgets';
import { AUTHENTICATION_CONFIGURATION, provideAuthenticationService } from '@processpuzzle/auth/domain';
import { OVERLAY_DEFAULT_CONFIG } from '@angular/cdk/overlay';
import { provideShareButtonsOptions } from 'ngx-sharebuttons';
import { shareIcons } from 'ngx-sharebuttons/icons';

export function createAppConfig(runtimeConfiguration: RuntimeConfiguration): ApplicationConfig {
  return {
    providers: [
      provideAppPropertyStore(),
      provideAnimations(),
      { provide: OVERLAY_DEFAULT_CONFIG, useValue: { usePopover: false } },
      { provide: RUNTIME_CONFIGURATION, useValue: runtimeConfiguration },
      { provide: AUTHENTICATION_CONFIGURATION, useValue: runtimeConfiguration.AUTHENTICATION_CONFIGURATION },
      { provide: FIREBASE_OPTIONS, useValue: runtimeConfiguration.BASE_CONFIGURATION.FIREBASE_CONFIGURATION },
      provideAuthenticationService(runtimeConfiguration),
      provideZonelessChangeDetection(),
      provideFirebaseApp(() => initializeApp(runtimeConfiguration.BASE_CONFIGURATION.FIREBASE_CONFIGURATION), [FIREBASE_OPTIONS]),
      provideFirestore(() => {
        const firestore = getFirestore();
        const pipelineStage = environment.PIPELINE_STAGE ?? 'ci';
        if (pipelineStage === 'dev') connectFirestoreEmulator(firestore, 'localhost', 8080);
        else if (pipelineStage === 'ci') connectFirestoreEmulator(firestore, 'firebase', 9090);
        return firestore;
      }),
      provideHttpClient(withInterceptors([centralHttpErrorInterceptor])),
      provideLoggingService(runtimeConfiguration.LOGGING_CONFIGURATION),
      provideCentralErrorHandler(),
      provideErrorSnackbar(),
      provideRouter(appRoutes, withComponentInputBinding()),
      provideNativeDateAdapter(),
      provideShareButtonsOptions(shareIcons()),
      provideTranslocoService(runtimeConfiguration.LANGUAGE_CONFIGURATION),
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
}
