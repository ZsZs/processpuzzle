import { Route } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { ACTIVE_ENTITY_FACADE, BASE_ENTITY_ROUTES } from '@processpuzzle/base-entity';
import { TestEntityFacade } from './content/base-forms/test-entity/test-entity.facade';
import { TestEntityComponentFacade } from './content/base-forms/test-entity-component/test-entity-component.facade';
import { TrunkDataFacade } from './content/base-forms/trunk-data/trunk-data.facade';
import { LayoutService } from '@processpuzzle/util';
import { ContentComponent } from './content/content.component';
import { FirestoreDocFacade } from './content/base-forms/firestore/firestore-doc.facade';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE, authMatcher } from '@processpuzzle/auth';
import { inject } from '@angular/core';

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
    providers: [LayoutService, TestEntityFacade, TestEntityComponentFacade, TrunkDataFacade, FirestoreDocFacade],
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
            loadComponent: () => import('./content/base-forms/test-entity/test-entity-container.component').then((comp) => comp.TestEntityContainerComponent),
            providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: TestEntityFacade }],
            children: BASE_ENTITY_ROUTES,
          },
          {
            path: 'test-entity-component',
            loadComponent: () => import('./content/base-forms/test-entity-component/test-entity-component-container.component').then((comp) => comp.TestEntityComponentContainerComponent),
            providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: TestEntityComponentFacade }],
            children: BASE_ENTITY_ROUTES,
          },
          {
            path: 'trunk-data',
            loadComponent: () => import('./content/base-forms/trunk-data/trunk-data-container.component').then((comp) => comp.TrunkDataContainerComponent),
            providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: TrunkDataFacade }],
            children: BASE_ENTITY_ROUTES,
          },
          {
            path: 'firestore-doc',
            loadComponent: () => import('./content/base-forms/firestore/firestore-doc-container.component').then((comp) => comp.FirestoreDocContainerComponent),
            providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: FirestoreDocFacade }],
            children: BASE_ENTITY_ROUTES,
          },
        ],
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
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('@processpuzzle/design').then((comp) => comp.DesignContentComponent),
      },
      {
        path: 'rules',
        loadComponent: () => import('@processpuzzle/base-rule-frontend').then((comp) => comp.BaseRuleContainerComponent),
      },
    ],
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
