import { Route } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { ACTIVE_ENTITY_FACADE, BASE_ENTITY_ROUTES, baseEntityRoutes } from '@processpuzzle/base-entity';
import { TestEntityFacade } from './content/base-forms/test-entity/test-entity.facade';
import { TestEntityComponentFacade } from './content/base-forms/test-entity-component/test-entity-component.facade';
import { EmbeddedComponentFacade } from './content/base-forms/embedded-component/embedded-component.facade';
import { EmbeddedDetailFacade } from './content/base-forms/embedded-detail/embedded-detail.facade';
import { RelatedEntityFacade } from './content/base-forms/related-entity/related-entity.facade';
import { TrunkDataFacade } from './content/base-forms/trunk-data/trunk-data.facade';
import { DYNAMIC_ENTITY_NAME, DYNAMIC_ENTITY_PATH, dynamicEntityScreenRoutes } from './content/base-forms/dynamic-entity/dynamic-entity.routes';
import { ORDER_NAME, ORDER_PATH, orderScreenRoutes, SPECIAL_ORDER_NAME, SPECIAL_ORDER_PATH, specialOrderScreenRoutes } from './content/base-rules/rule-sample.routes';
import { LayoutService } from '@processpuzzle/util';
import { ContentComponent } from './content/content.component';
import { BASE_APP_ROUTES } from '@processpuzzle/base-app';
import { BASE_DOCUMENT_ROUTES } from '@processpuzzle/base-document';
import { BASE_STATE_ROUTES } from '@processpuzzle/base-state';
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
            // The metadata sample. No `providers` and no `children`: `Dynamic Entity` exists only as a
            // `BaseEntityDefinition` in base-entity-backend's seed data, so its descriptor — and with it its
            // store, its labels and its embedded branches — is synthesized at run-time and contributed as
            // routes by `dynamicEntityScreenRoutes`. That absence is the sample.
            path: DYNAMIC_ENTITY_PATH,
            data: { entityName: DYNAMIC_ENTITY_NAME },
            loadComponent: () => import('./content/base-forms/dynamic-entity/dynamic-entity-container.component').then((comp) => comp.DynamicEntityContainerComponent),
            loadChildren: dynamicEntityScreenRoutes,
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
    // No `provideBaseRuleEngine()` here any more: the rule engine is bound once, application-wide, in
    // `app.config.ts`. Having it on this route made rule validation a property of the section the user
    // happened to be in rather than of the entity being edited.
    providers: [LayoutService, provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' })],
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
        // The rule samples. `Order` and `Special Order` exist only as `BaseEntityDefinition` rows in
        // base-entity-backend's processpuzzle-testbed-entities.yaml, so neither has a facade, a descriptor or
        // a `providers` entry here — `rule-sample.routes.ts` resolves both at run-time, as the Dynamic Entity
        // sample under `base-entity` does. What makes them *rule* samples is only that these two entities are
        // the ones the seeded rules name as their context; the engine itself is application-wide.
        path: 'samples',
        loadComponent: () => import('./content/base-rules/samples.component').then((comp) => comp.SamplesComponent),
        children: [
          { path: '', pathMatch: 'full', redirectTo: ORDER_PATH },
          // `data.entityName` is not decoration: `readEmbeddedBreadcrumb` pushes a level when it meets the
          // route that *declares* the name, and takes that level's `baseUrl` from the URL accumulated so
          // far. The name has to sit on the route contributing the entity's own segment — here — or the
          // level records `…/samples/order` as its base and every URL built on it doubles the segment,
          // which is a Details link and a Details tab that silently do nothing.
          { path: ORDER_PATH, data: { entityName: ORDER_NAME }, loadChildren: orderScreenRoutes },
          { path: SPECIAL_ORDER_PATH, data: { entityName: SPECIAL_ORDER_NAME }, loadChildren: specialOrderScreenRoutes },
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
        // `BASE_DOCUMENT_ROUTES` brings the whole `document` branch — the routable document, its content tab
        // and the two embedded port lists below it — and declares its own transloco scopes, so nothing is
        // added here. Static children rather than `loadChildren`: see the note on SamplesComponent for why
        // the generated e2e suites cannot address an entity mounted behind a lazy branch.
        children: BASE_DOCUMENT_ROUTES,
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
        // `BASE_STATE_ROUTES` brings the whole `state-machine-definition` branch — the routable definition,
        // its embedded states and transitions, and the guards and actions below a transition — and declares
        // its own transloco scopes, so nothing is added here. Static children rather than `loadChildren`,
        // for the same reason as the base-document branch above.
        children: BASE_STATE_ROUTES,
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
    loadComponent: () => import('@processpuzzle/base-widget').then((comp) => comp.MarkdownPageComponent),
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
