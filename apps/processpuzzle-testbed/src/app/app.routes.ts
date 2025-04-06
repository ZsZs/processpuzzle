import { Route } from '@angular/router';
import { BASE_ENTITY_SERVICE, BASE_ENTITY_STORE, BaseEntityFormComponent, BaseEntityListComponent } from '@processpuzzle/base-entity';
import { TestEntityStore } from './content/base-forms/test-entity/test-entity.store';
import { TestEntityService } from './content/base-forms/test-entity/test-entity.service';
import { TestEntityComponentStore } from './content/base-forms/test-entity-component/test-entity-component.store';
import { TestEntityComponentService } from './content/base-forms/test-entity-component/test-entity-component.service';
import { TrunkDataStore } from './content/base-forms/trunk-data/trunk-data.store';
import { TrunkDataService } from './content/base-forms/trunk-data/trunk-data.service';
import { TestEntity } from './content/base-forms/test-entity/test-entity';
import { TestEntityComponent } from './content/base-forms/test-entity-component/test-entity-component';
import { TrunkData } from './content/base-forms/trunk-data/trunk-data';
import { LayoutService } from '@processpuzzle/util';
import { ContentComponent } from './content/content.component';
import { FirestoreDocStore } from './content/base-forms/firestore/firestore-doc.store';
import { FirestoreDocService } from './content/base-forms/firestore/firestore-doc.service';
import { FirestoreDoc } from './content/base-forms/firestore/firestore-doc';

export const appRoutes: Route[] = [
  { path: 'home', title: 'ProcessPuzzle Testbed - Home', component: ContentComponent, data: { icon: 'home' } },
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  { path: 'util', title: 'ProcessPuzzle Testbed - Util', data: { icon: 'service_toolbox' }, loadComponent: () => import('./content/util/util.component').then((comp) => comp.UtilsComponent) },
  {
    path: 'widgets',
    title: 'ProcessPuzzle Testbed - Widgets',
    data: { icon: 'service_toolbox' },
    loadComponent: () => import('./content/widgets/widgets.component').then((comp) => comp.WidgetsComponent),
  },
  {
    path: 'base-entity',
    title: 'ProcessPuzzle Testbed - Base Entity',
    data: { icon: 'checkbook' },
    loadComponent: () => import('./content/base-forms/sample-forms.component').then((comp) => comp.SampleFormsComponent),
    providers: [LayoutService],
    children: [
      {
        path: 'test-entity',
        loadComponent: () => import('./content/base-forms/test-entity/test-entity-container.component').then((comp) => comp.TestEntityContainerComponent),
        providers: [TestEntityStore, { provide: BASE_ENTITY_SERVICE, useValue: TestEntityService }, { provide: BASE_ENTITY_STORE, useValue: TestEntityStore, deps: [TestEntityStore] }],
        children: [
          { path: '', redirectTo: 'list', pathMatch: 'full' },
          { path: 'list', component: BaseEntityListComponent<TestEntity> },
          { path: ':entityId/details', component: BaseEntityFormComponent<TestEntity> },
        ],
      },
      {
        path: 'test-entity-component',
        loadComponent: () => import('./content/base-forms/test-entity-component/test-entity-component-container.component').then((comp) => comp.TestEntityComponentContainerComponent),
        providers: [
          TestEntityComponentStore,
          { provide: BASE_ENTITY_SERVICE, useValue: TestEntityComponentService },
          { provide: BASE_ENTITY_STORE, useValue: TestEntityComponentStore, deps: [TestEntityComponentStore] },
        ],
        children: [
          { path: '', redirectTo: 'list', pathMatch: 'full' },
          { path: 'list', component: BaseEntityListComponent<TestEntityComponent> },
          { path: ':entityId/details', component: BaseEntityFormComponent<TestEntityComponent> },
        ],
      },
      {
        path: 'trunk-data',
        loadComponent: () => import('./content/base-forms/trunk-data/trunk-data-container.component').then((comp) => comp.TrunkDataContainerComponent),
        providers: [TrunkDataStore, { provide: BASE_ENTITY_SERVICE, useValue: TrunkDataService }, { provide: BASE_ENTITY_STORE, useValue: TrunkDataStore, deps: [TrunkDataStore] }],
        children: [
          { path: '', redirectTo: 'list', pathMatch: 'full' },
          { path: 'list', component: BaseEntityListComponent<TrunkData> },
          { path: ':entityId/details', component: BaseEntityFormComponent<TrunkData> },
        ],
      },
      {
        path: 'firestore-doc',
        loadComponent: () => import('./content/base-forms/firestore/firestore-doc-container.component').then((comp) => comp.FirestoreDocContainerComponent),
        providers: [FirestoreDocStore, { provide: BASE_ENTITY_SERVICE, useValue: FirestoreDocService }, { provide: BASE_ENTITY_STORE, useValue: FirestoreDocStore, deps: [FirestoreDocStore] }],
        children: [
          { path: '', redirectTo: 'list', pathMatch: 'full' },
          { path: 'list', component: BaseEntityListComponent<FirestoreDoc> },
          { path: ':entityId/details', component: BaseEntityFormComponent<FirestoreDoc> },
        ],
      },
    ],
  },
  { path: 'ci-cd', title: 'ProcessPuzzle Testbed - CI/CD', data: { icon: 'repartition' }, loadComponent: () => import('./content/ci-cd/ci-cd.component').then((comp) => comp.CiCdComponent) },
];
