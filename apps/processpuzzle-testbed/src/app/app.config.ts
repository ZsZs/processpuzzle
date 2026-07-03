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
import { connectFirestoreEmulator, Firestore, getFirestore, provideFirestore } from '@angular/fire/firestore';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { provideAppPropertyStore, provideErrorSnackbar, provideTranslocoService } from '@processpuzzle/widgets';
import { AUTHENTICATION_CONFIGURATION, provideAuthenticationService } from '@processpuzzle/auth/domain';
import { OVERLAY_DEFAULT_CONFIG } from '@angular/cdk/overlay';
import { provideShareButtonsOptions } from 'ngx-sharebuttons';
import { shareIcons } from 'ngx-sharebuttons/icons';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BASE_ENTITY_FACADE_REGISTRY, provideEntityRouteRegistry } from '@processpuzzle/base-entity';
import { TestEntityFacade } from './content/base-forms/test-entity/test-entity.facade';
import { TestEntityComponentFacade } from './content/base-forms/test-entity-component/test-entity-component.facade';
import { TrunkDataFacade } from './content/base-forms/trunk-data/trunk-data.facade';
import { FirestoreDocFacade } from './content/base-forms/firestore/firestore-doc.facade';
import { OrderFacade } from './content/base-rules/order/order.facade';
import { OrderLineFacade } from './content/base-rules/order-line/order-line.facade';

export function createAppConfig(runtimeConfiguration: RuntimeConfiguration): ApplicationConfig {
  return {
    providers: [
      provideFirestore(() => {
        const fireConf = runtimeConfiguration.BASE_CONFIGURATION.FIREBASE_CONFIGURATION;
        const firestore = getFirestore();
        if (fireConf.FIRESTORE_EMULATOR_HOST && fireConf.FIRESTORE_EMULATOR_PORT) {
          connectFirestoreEmulator(firestore, fireConf.FIRESTORE_EMULATOR_HOST, fireConf.FIRESTORE_EMULATOR_PORT);
        }
        return firestore;
      }),
      provideAuthenticationService(runtimeConfiguration),
      provideZonelessChangeDetection(),
      provideFirebaseApp(() => initializeApp(runtimeConfiguration.BASE_CONFIGURATION.FIREBASE_CONFIGURATION), [FIREBASE_OPTIONS]),
      provideHttpClient(withInterceptors([centralHttpErrorInterceptor])),
      provideLoggingService(runtimeConfiguration.LOGGING_CONFIGURATION),
      provideCentralErrorHandler(),
      provideAppPropertyStore(Firestore),
      TestEntityFacade,
      TestEntityComponentFacade,
      TrunkDataFacade,
      FirestoreDocFacade,
      OrderFacade,
      OrderLineFacade,
      {
        provide: BASE_ENTITY_FACADE_REGISTRY,
        useValue: {
          'Test Entity': TestEntityFacade,
          'Test Entity Component': TestEntityComponentFacade,
          'Trunk Data': TrunkDataFacade,
          'Firestore Doc': FirestoreDocFacade,
          Order: OrderFacade,
          'Order Line': OrderLineFacade,
        },
      },
      provideAnimations(),
      { provide: OVERLAY_DEFAULT_CONFIG, useValue: { usePopover: false } },
      { provide: RUNTIME_CONFIGURATION, useValue: runtimeConfiguration },
      { provide: AUTHENTICATION_CONFIGURATION, useValue: runtimeConfiguration.AUTHENTICATION_CONFIGURATION },
      { provide: FIREBASE_OPTIONS, useValue: runtimeConfiguration.BASE_CONFIGURATION.FIREBASE_CONFIGURATION },
      provideErrorSnackbar(),
      provideRouter(appRoutes, withComponentInputBinding()),
      provideEntityRouteRegistry(),
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
