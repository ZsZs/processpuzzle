import { Routes } from '@angular/router';
import { BASE_APP_ROUTES } from '@processpuzzle/base-app';
import { BASE_RULE_ROUTES } from '@processpuzzle/base-rule';
import { DesignContentComponent } from './content/design-content.component';
import { UnderConstructionComponent } from './under-construction/under-construction.component';

export const DESIGN_ROUTES: Routes = [
  { path: '', pathMatch: 'full', component: DesignContentComponent },
  {
    path: 'entities',
    title: 'ProcessPuzzle Design - Entities',
    data: { icon: 'checkbook', menuTitle: 'design.entities' },
    component: UnderConstructionComponent,
  },
  // Under construction rather than BaseDocumentContainerComponent: `@processpuzzle/base-document`
  // has no facade for Document nor for its two embedded port entities, and a base-entity list needs
  // all three in BASE_ENTITY_FACADE_REGISTRY before it renders — see the testbed's base-documents
  // samples tab for the same reason. Swap the component once those facades exist.
  {
    path: 'documents',
    title: 'ProcessPuzzle Design - Documents',
    data: { icon: 'article', menuTitle: 'design.documents' },
    component: UnderConstructionComponent,
  },
  ...BASE_RULE_ROUTES,
  {
    path: 'states',
    title: 'ProcessPuzzle Design - States',
    data: { icon: 'flag_circle', menuTitle: 'design.states' },
    component: UnderConstructionComponent,
  },
  {
    path: 'workflows',
    title: 'ProcessPuzzle Design - Workflows',
    data: { icon: 'schema', menuTitle: 'design.workflows' },
    component: UnderConstructionComponent,
  },
  ...BASE_APP_ROUTES,
];
