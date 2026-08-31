import { Routes } from '@angular/router';
import { BASE_APP_ROUTES } from '@processpuzzle/base-app';
import { BASE_DOCUMENT_ROUTES } from '@processpuzzle/base-document';
import { BASE_RULE_ROUTES } from '@processpuzzle/base-rule';
import { BASE_WIDGET_ROUTES } from '@processpuzzle/base-widget';
import { BASE_WORKFLOW_ROUTES } from '@processpuzzle/base-workflow';
import { ApplicationDesignerComponent } from './application-designer/application-designer.component';
import { DesignContentComponent } from './content/design-content.component';
import { WorkflowDesignerComponent } from './workflow-designer/workflow-designer.component';
import { UnderConstructionComponent } from './under-construction/under-construction.component';

export const DESIGN_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'ProcessPuzzle Design - Designer Home',
    data: { icon: 'home', menuTitle: 'design.designer-home' },
    component: DesignContentComponent,
  },
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
  // One section, six tabs — see WorkflowDesignerComponent. `BASE_WORKFLOW_ROUTES` is spread unchanged, so
  // its branches keep their own transloco scopes and stay mountable elsewhere (the testbed mounts them a
  // second time under `/base-workflow/samples`); only the prefix they hang under is new. As for base-app,
  // base-document and base-widget, the hosting application has to spread BASE_WORKFLOW_FACADE_PROVIDERS
  // and BASE_WORKFLOW_ENTITY_FACADES into its own providers, because a base-entity list resolves the
  // entity and its embedded levels through BASE_ENTITY_FACADE_REGISTRY and this library cannot contribute
  // to that token without replacing it.
  //
  // Nested under one route rather than spread at this level like BASE_RULE_ROUTES and
  // BASE_DOCUMENT_ROUTES: six branches for one authoring subject would be six sidenav entries, and their
  // `menuTitle` keys live in the `base_workflow` scope while DesignSidenavComponent registers only
  // `design`.
  {
    path: 'workflows',
    title: 'ProcessPuzzle Design - Workflows',
    data: { icon: 'schema', menuTitle: 'design.workflows' },
    component: WorkflowDesignerComponent,
    // No `providers`: the tab bar names its `design` scope on the directive itself, so this route adds
    // nothing to the injectors the tabs' own screens resolve through.
    children: [{ path: '', pathMatch: 'full', redirectTo: 'workflow' }, ...BASE_WORKFLOW_ROUTES],
  },
  // One section, three tabs — see ApplicationDesignerComponent. Both spreads are unchanged, so the branches
  // keep their own transloco scopes and stay mountable elsewhere (the testbed mounts BASE_APP_ROUTES a second
  // time under `/base-app/samples`); only the prefix they hang under is new. As for base-app and
  // base-document, the hosting application has to spread BASE_WIDGET_FACADE_PROVIDERS and
  // BASE_WIDGET_ENTITY_FACADES into its own providers for the Widgets tab to resolve its facades.
  {
    path: 'application',
    title: 'ProcessPuzzle Design - Application',
    data: { icon: 'web', menuTitle: 'design.application' },
    component: ApplicationDesignerComponent,
    // No `providers`: the tab bar names its `design` scope on the directive itself, so this route adds
    // nothing to the injectors the tabs' own screens resolve through. What those screens need —
    // `base_entity` and `base_app` — each tab's route declares for itself.
    children: [{ path: '', pathMatch: 'full', redirectTo: 'app-definition' }, ...BASE_APP_ROUTES, ...BASE_WIDGET_ROUTES],
  },
];
