import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { appRoutes } from './app.routes';
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
import { BASE_WIDGET_ENTITY_FACADES, BASE_WIDGET_FACADE_PROVIDERS, BASE_WIDGET_TRANSLATION_SOURCE, provideAppPropertyStore, provideBaseWidgets } from '@processpuzzle/base-widget';
import { provideErrorSnackbar, provideTranslocoService } from '@processpuzzle/util';
import { AUTHENTICATION_CONFIGURATION, provideAuthenticationService } from '@processpuzzle/auth/domain';
import { OVERLAY_DEFAULT_CONFIG } from '@angular/cdk/overlay';
import { provideShareButtonsOptions } from 'ngx-sharebuttons';
import { shareIcons } from 'ngx-sharebuttons/icons';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BASE_ENTITY_FACADE_REGISTRY, BASE_ENTITY_TRANSLATION_SOURCE, provideEntityRouteRegistry } from '@processpuzzle/base-entity';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { BASE_APP_ENTITY_FACADES, BASE_APP_FACADE_PROVIDERS, BASE_APP_TRANSLATION_SOURCE } from '@processpuzzle/base-app';
import { BASE_DOCUMENT_ENTITY_FACADES, BASE_DOCUMENT_FACADE_PROVIDERS, BASE_DOCUMENT_TRANSLATION_SOURCE } from '@processpuzzle/base-document';
import { BASE_STATE_ENTITY_FACADES, BASE_STATE_FACADE_PROVIDERS, BASE_STATE_TRANSLATION_SOURCE } from '@processpuzzle/base-state';
import { provideBaseRuleEngine } from '@processpuzzle/base-rule';
import { TRANSLATION_SOURCE_REGISTRY } from '@processpuzzle/util';
import { TestEntityFacade } from './content/base-forms/test-entity/test-entity.facade';
import { TestEntityComponentFacade } from './content/base-forms/test-entity-component/test-entity-component.facade';
import { RelatedEntityFacade } from './content/base-forms/related-entity/related-entity.facade';
import { EmbeddedComponentFacade } from './content/base-forms/embedded-component/embedded-component.facade';
import { EmbeddedDetailFacade } from './content/base-forms/embedded-detail/embedded-detail.facade';
import { TrunkDataFacade } from './content/base-forms/trunk-data/trunk-data.facade';

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
      RelatedEntityFacade,
      TrunkDataFacade,
      // Embedded entities are registered like any other: their facade gives them a store, which reads and
      // writes the containing entity's document rather than an endpoint of their own. base-app ships the
      // whole definition graph — the routable `App Definition` and the four embedded levels below it — as
      // one list, so a consuming application cannot register half of it.
      ...BASE_APP_FACADE_PROVIDERS,
      // Same shape for base-document: the routable `Document` plus the two embedded port lists its form
      // carries, which the design section's document route renders.
      ...BASE_DOCUMENT_FACADE_PROVIDERS,
      // And for base-widget: the routable `Widget Definition` plus its two embedded port lists, rendered by
      // the Widgets tab of the design section's Application page.
      ...BASE_WIDGET_FACADE_PROVIDERS,
      // And for base-state: the routable `State Machine Definition` plus the four embedded levels its form
      // carries — states and transitions, and a transition's guards and actions.
      ...BASE_STATE_FACADE_PROVIDERS,
      // Fills WIDGET_REGISTRY with the components behind the catalogue's keys. base-app's shell renders a
      // widget instance by looking its `type` up there, and provides nothing itself by design — which
      // component answers a key is the hosting application's decision, not the shell's.
      provideBaseWidgets(),
      // Binds base-entity's `RULE_ENGINE` seam to base-rule's evaluator, for the **whole application**.
      //
      // `BaseEntityFormComponent` injects `RULE_ENGINE` optionally and, when it finds one, loads the rules
      // whose `context` is its descriptor's `entityName` and evaluates them on every change. Nothing else
      // turns rules on: with the token unbound, `loadRules()` returns on its first line and the form is
      // simply unvalidated — silently, because an application with no rule backend is a legitimate
      // deployment (this app runs against Firestore and json-server too).
      //
      // Which is why this belongs here and not on a route. It used to sit on the `base-rule` route alone,
      // which made rules a property of *which section of the testbed you were in* rather than of the entity
      // being edited: an `Order` form validated under `/base-rule/samples` and the same generated form
      // validated nothing under `/base-entity` or `/design`. Every form loads its own rules, or the feature
      // does not mean anything.
      provideBaseRuleEngine(),
      EmbeddedComponentFacade,
      EmbeddedDetailFacade,
      {
        provide: BASE_ENTITY_FACADE_REGISTRY,
        useValue: {
          'Test Entity': TestEntityFacade,
          'Test Entity Component': TestEntityComponentFacade,
          'Related Entity': RelatedEntityFacade,
          'Trunk Data': TrunkDataFacade,
          // 'Dynamic Entity' with its two embedded levels, and 'Order' with its 'Order Line', are
          // deliberately absent and stay absent: they are metadata — base-entity-backend's
          // processpuzzle-testbed-entities.yaml — and their descriptors are synthesized from those
          // definitions at run-time by base-entity's `EntityScreenResolver`. Registering them here would
          // *override* that, since a compile-time facade wins by design, and the two surfaces that mount
          // them — the Dynamic Entity sample and the demo application's Order screens — exist to exercise
          // the metadata path end to end. An entity only needs an entry below when the application has
          // something to add that a definition cannot express: an extra tab, a hand-tuned layout.
          ...BASE_APP_ENTITY_FACADES,
          ...BASE_DOCUMENT_ENTITY_FACADES,
          ...BASE_WIDGET_ENTITY_FACADES,
          ...BASE_STATE_ENTITY_FACADES,
          'Embedded Component': EmbeddedComponentFacade,
          'Embedded Detail': EmbeddedDetailFacade,
        },
      },
      // Which backend serves which transloco scope, for the bundles that have no asset to fall back on.
      // Each library declares its own entry; a scope nobody claims — a designer-authored module's, named
      // at run-time — goes to base-app, which owns ModuleDefinition. All contributions have to be here
      // rather than on route branches: a `multi` token is not merged across injectors.
      ...[BASE_APP_TRANSLATION_SOURCE, BASE_ENTITY_TRANSLATION_SOURCE, BASE_WIDGET_TRANSLATION_SOURCE, BASE_DOCUMENT_TRANSLATION_SOURCE, BASE_STATE_TRANSLATION_SOURCE].map((source) => ({
        provide: TRANSLATION_SOURCE_REGISTRY,
        useValue: source,
        multi: true,
      })),
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
