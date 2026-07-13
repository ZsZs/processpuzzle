import { Routes } from '@angular/router';
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
  {
    path: 'desktop',
    title: 'ProcessPuzzle Design - Desktop',
    data: { icon: 'desktop_windows', menuTitle: 'design.desktop' },
    component: UnderConstructionComponent,
  },
];
