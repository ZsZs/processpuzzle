import { Route } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { ACTIVE_ENTITY_FACADE, BASE_ENTITY_ROUTES, baseEntityRoutes } from '@processpuzzle/base-entity';
import { TestEntityFacade } from './content/base-forms/test-entity/test-entity.facade';
import { TestEntityComponentFacade } from './content/base-forms/test-entity-component/test-entity-component.facade';
import { EmbeddedComponentFacade } from './content/base-forms/embedded-component/embedded-component.facade';
import { EmbeddedDetailFacade } from './content/base-forms/embedded-detail/embedded-detail.facade';
import { RelatedEntityFacade } from './content/base-forms/related-entity/related-entity.facade';
import { TrunkDataFacade } from './content/base-forms/trunk-data/trunk-data.facade';
import { LayoutService } from '@processpuzzle/util';
import { ContentComponent } from './content/content.component';
import { FirestoreDocFacade } from './content/base-forms/firestore/firestore-doc.facade';
import { OrderFacade } from './content/base-rules/order/order.facade';
import { OrderLineFacade } from './content/base-rules/order-line/order-line.facade';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { provideBaseRuleEngine } from '@processpuzzle/base-rule';
import { BASE_APP_ROUTES } from '@processpuzzle/base-app';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE, authMatcher } from '@processpuzzle/auth';
import { inject } from '@angular/core';
import { provideTranslocoScope } from '@jsverse/transloco';

export const appRoutes: Route[] = [
  {
    path: 'home',
    title: 'ProcessPuzzle Testbed - Home',
    resolve: {
      auth: () => inject(AUTHENTICATION_SERVICE).authenticate(),
    },
    component: ContentComponent,
    data: { icon: 'home', menuTitle: 'home' },
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'home',
  },
  {
    path: 'util',
    title: 'ProcessPuzzle Testbed - Util',
    data: { icon: 'service_toolbox', menuTitle: 'util' },
    loadComponent: () => import('./content/util/util.component').then((comp) => comp.UtilsComponent),
  },
  {
    path: 'test-util',
    title: 'ProcessPuzzle Testbed - Test Util',
    data: { icon: 'check_circle', menuTitle: 'test-util' },
    loadComponent: () => import('./content/test-util/test-util.component').then((comp) => comp.TestUtilsComponent),
  },
  {
    path: 'widgets',
    title: 'ProcessPuzzle Testbed - Widgets',
    data: { icon: 'web_asset', menuTitle: 'widgets' },
    loadComponent: () => import('./content/widgets/widgets.component').then((comp) => comp.WidgetsComponent),
  },
  {
    path: 'auth-lib',
    title: 'ProcessPuzzle Testbed - Auth',
    data: { icon: 'person_add', menuTitle: 'auth' },
    loadComponent: () => import('./content/auth/auth.component').then((comp) => comp.AuthComponent),
  },
  {
    path: 'base-entity',
    title: 'ProcessPuzzle Testbed - Base Entity',
    data: { icon: 'checkbook', menuTitle: 'base-entity' },
    loadComponent: () => import('./content/base-forms/base-forms.component').then((comp) => comp.BaseFormsComponent),
    // The facades are provided once, in `app.config.ts`. Re-providing them here would give this route its own
    // instances — and so its own stores — while `BaseEntityDescriptorRegistry`, which is root-provided, would
    // still resolve the root ones: an embedded child would then read a document nobody has loaded.
    providers: [LayoutService, provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' })],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'overview',
      },
      {
        path: 'overview',
        loadComponent: () => import('./content/base-forms/overview.component').then((comp) => comp.OverviewComponent),
      },
      {
        path: 'samples',
        loadComponent: () => import('./content/base-forms/samples.component').then((comp) => comp.SamplesComponent),
        children: [
          {
            path: 'test-entity',
            data: { entityName: 'Test Entity' },
            loadComponent: () => import('./content/base-forms/test-entity/test-entity-container.component').then((comp) => comp.TestEntityContainerComponent),
            providers: [
              { provide: ACTIVE_ENTITY_FACADE, useExisting: TestEntityFacade },
              provideTranslocoScope({ scope: 'test_entity', alias: 'test_entity' }),
              // The embedded component's label is rendered on this form's row list, so its scope loads here.
              provideTranslocoScope({ scope: 'embedded_component', alias: 'embedded_component' }),
            ],
            // The embedded children have no route of their own: they hang below this entity's details route,
            // at `test-entity/:id/details/embedded-component/:id/details`. That nesting is what carries the
            // position identifying a row — an embedded entity is not stored anywhere else — and what makes
            // the child unreachable except through the entity that contains it.
            children: baseEntityRoutes([
              {
                entityName: 'Embedded Component',
                facade: EmbeddedComponentFacade,
                providers: [provideTranslocoScope({ scope: 'embedded_detail', alias: 'embedded_detail' })],
                children: () => [{ entityName: 'Embedded Detail', facade: EmbeddedDetailFacade }],
              },
            ]),
          },
          {
            path: 'test-entity-component',
            data: { entityName: 'Test Entity Component' },
            loadComponent: () => import('./content/base-forms/test-entity-component/test-entity-component-container.component').then((comp) => comp.TestEntityComponentContainerComponent),
            providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: TestEntityComponentFacade }, provideTranslocoScope({ scope: 'test_entity_component', alias: 'test_entity_component' })],
            children: BASE_ENTITY_ROUTES,
          },
          {
            path: 'related-entity',
            data: { entityName: 'Related Entity' },
            loadComponent: () => import('./content/base-forms/related-entity/related-entity-container.component').then((comp) => comp.RelatedEntityContainerComponent),
            providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: RelatedEntityFacade }, provideTranslocoScope({ scope: 'related_entity', alias: 'related_entity' })],
            children: BASE_ENTITY_ROUTES,
          },
          {
            path: 'trunk-data',
            data: { entityName: 'Trunk Data' },
            loadComponent: () => import('./content/base-forms/trunk-data/trunk-data-container.component').then((comp) => comp.TrunkDataContainerComponent),
            providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: TrunkDataFacade }, provideTranslocoScope({ scope: 'trunk_data', alias: 'trunk_data' })],
            children: BASE_ENTITY_ROUTES,
          },
          {
            path: 'firestore-doc',
            data: { entityName: 'Firestore Doc' },
            loadComponent: () => import('./content/base-forms/firestore/firestore-doc-container.component').then((comp) => comp.FirestoreDocContainerComponent),
            providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: FirestoreDocFacade }, provideTranslocoScope({ scope: 'firestore_doc', alias: 'firestore_doc' })],
            children: BASE_ENTITY_ROUTES,
          },
        ],
      },
    ],
  },
  {
    path: 'base-rule',
    title: 'ProcessPuzzle Testbed - Base Rule',
    data: { icon: 'gavel', menuTitle: 'base-rule' },
    loadComponent: () => import('./content/base-rules/base-rules.component').then((comp) => comp.BaseRulesComponent),
    providers: [LayoutService, provideBaseRuleEngine(), provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' })],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'overview',
      },
      {
        path: 'overview',
        loadComponent: () => import('./content/base-rules/overview.component').then((comp) => comp.OverviewComponent),
      },
      {
        path: 'samples',
        loadComponent: () => import('./content/base-rules/samples.component').then((comp) => comp.SamplesComponent),
        children: [
          {
            path: 'order',
            data: { entityName: 'Order' },
            loadComponent: () => import('./content/base-rules/order/order-container.component').then((comp) => comp.OrderContainerComponent),
            providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: OrderFacade }, provideTranslocoScope({ scope: 'order', alias: 'order' })],
            children: BASE_ENTITY_ROUTES,
          },
          {
            path: 'order-line',
            data: { entityName: 'Order Line' },
            loadComponent: () => import('./content/base-rules/order-line/order-line-container.component').then((comp) => comp.OrderLineContainerComponent),
            providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: OrderLineFacade }, provideTranslocoScope({ scope: 'order_line', alias: 'order_line' })],
            children: BASE_ENTITY_ROUTES,
          },
        ],
      },
    ],
  },
  {
    path: 'base-document',
    title: 'ProcessPuzzle Testbed - Base Document',
    data: { icon: 'article', menuTitle: 'base-document' },
    loadComponent: () => import('./content/base-documents/base-documents.component').then((comp) => comp.BaseDocumentsComponent),
    providers: [LayoutService],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'overview',
      },
      {
        path: 'overview',
        loadComponent: () => import('./content/base-documents/overview.component').then((comp) => comp.OverviewComponent),
      },
      {
        path: 'samples',
        loadComponent: () => import('./content/base-documents/samples.component').then((comp) => comp.SamplesComponent),
      },
    ],
  },
  {
    path: 'base-state',
    title: 'ProcessPuzzle Testbed - Base State',
    data: { icon: 'flag_circle', menuTitle: 'base-state' },
    loadComponent: () => import('./content/base-states/base-states.component').then((comp) => comp.BaseStatesComponent),
    providers: [LayoutService],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'overview',
      },
      {
        path: 'overview',
        loadComponent: () => import('./content/base-states/overview.component').then((comp) => comp.OverviewComponent),
      },
      {
        path: 'samples',
        loadComponent: () => import('./content/base-states/samples.component').then((comp) => comp.SamplesComponent),
      },
    ],
  },
  {
    path: 'base-workflow',
    title: 'ProcessPuzzle Testbed - Base Workflow',
    data: { icon: 'schema', menuTitle: 'base-workflow' },
    loadComponent: () => import('./content/base-workflows/base-workflows.component').then((comp) => comp.BaseWorkflowsComponent),
    providers: [LayoutService],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'overview',
      },
      {
        path: 'overview',
        loadComponent: () => import('./content/base-workflows/overview.component').then((comp) => comp.OverviewComponent),
      },
      {
        path: 'samples',
        loadComponent: () => import('./content/base-workflows/samples.component').then((comp) => comp.SamplesComponent),
      },
    ],
  },
  {
    path: 'base-app',
    title: 'ProcessPuzzle Testbed - Base App',
    data: { icon: 'web', menuTitle: 'base-app' },
    loadComponent: () => import('./content/base-apps/base-apps.component').then((comp) => comp.BaseAppsComponent),
    // The `App Definition` facades are provided once, in `app.config.ts` — see the note on `base-entity`
    // above for why re-providing them per route would give this branch its own, unregistered, stores.
    providers: [LayoutService],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'overview',
      },
      {
        path: 'overview',
        loadComponent: () => import('./content/base-apps/overview.component').then((comp) => comp.OverviewComponent),
      },
      {
        path: 'samples',
        loadComponent: () => import('./content/base-apps/samples.component').then((comp) => comp.SamplesComponent),
        // `BASE_APP_ROUTES` brings the whole `app-definition` branch — the routable definition plus the
        // four embedded levels below it — and declares its own transloco scopes, so nothing is added here.
        children: BASE_APP_ROUTES,
      },
    ],
  },
  {
    path: 'ci-cd',
    title: 'ProcessPuzzle Testbed - CI/CD',
    data: { icon: 'repartition', menuTitle: 'ci-cd', markdownSrc: 'https://raw.githubusercontent.com/ZsZs/processpuzzle/refs/heads/develop/.github/README.md' },
    loadComponent: () => import('@processpuzzle/widgets').then((comp) => comp.MarkdownPageComponent),
  },
  {
    path: 'design',
    loadChildren: () => import('@processpuzzle/design').then((m) => m.DESIGN_ROUTES),
    providers: [provideTranslocoScope({ scope: 'design', alias: 'design' })],
  },
  {
    path: 'entity-registry',
    loadComponent: () => import('@processpuzzle/base-entity').then((comp) => comp.EntityRegistryComponent),
  },
  // Custom matcher route for any URL containing 'auth'
  {
    matcher: authMatcher,
    loadChildren: () => import('@processpuzzle/auth/feature').then((r) => r.authRoutes),
  },
];
