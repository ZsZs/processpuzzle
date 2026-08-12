import { Routes } from '@angular/router';
import { BASE_APP_ROUTES } from '@processpuzzle/base-app';
import { BASE_DOCUMENT_ROUTES } from '@processpuzzle/base-document';
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
  // Mounted at `document`, singular, because the path segment is the snake-cased entity name — see the
  // comment on BASE_DOCUMENT_ROUTES. The hosting application still has to spread
  // BASE_DOCUMENT_FACADE_PROVIDERS and BASE_DOCUMENT_ENTITY_FACADES into its own providers, exactly as it
  // does for base-app: a base-entity list resolves the entity and its two embedded ports through
  // BASE_ENTITY_FACADE_REGISTRY, and this library cannot contribute to that token without replacing it.
  ...BASE_DOCUMENT_ROUTES,
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
