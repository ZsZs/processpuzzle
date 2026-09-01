import { Routes } from '@angular/router';
import { BASE_APP_ROUTES } from '@processpuzzle/base-app';
import { BASE_DOCUMENT_ROUTES } from '@processpuzzle/base-document';
import { BASE_ENTITY_AUTHORING_ROUTES } from '@processpuzzle/base-entity';
import { BASE_RULE_ROUTES } from '@processpuzzle/base-rule';
import { BASE_STATE_ROUTES } from '@processpuzzle/base-state';
import { BASE_WIDGET_ROUTES } from '@processpuzzle/base-widget';
import { BASE_WORKFLOW_ROUTES } from '@processpuzzle/base-workflow';
import { ApplicationDesignerComponent } from './application-designer/application-designer.component';
import { DesignContentComponent } from './content/design-content.component';
import { WorkflowDesignerComponent } from './workflow-designer/workflow-designer.component';

export const DESIGN_ROUTES: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'ProcessPuzzle Design - Designer Home',
    data: { icon: 'home', menuTitle: 'design.designer-home' },
    component: DesignContentComponent,
  },
  // The Entities section: base-entity's own `Entity Definition` branch, with the `Entity Attribute` level
  // its form carries. These are the screens with which a tenant declares its entity types — the metadata
  // every other feature interprets, and the same rows `EntityScreenResolver` synthesizes screens from.
  //
  // Nested under `entities` rather than spread at this level like BASE_RULE_ROUTES and BASE_DOCUMENT_ROUTES,
  // for the reason given on the States section: `/design/entities` is what the sidenav entry and the
  // Designer Home card already address, while `BASE_ENTITY_AUTHORING_ROUTES` mounts at
  // `entity-definition` — `snakeCaseName(entityName)`, which `BaseFormNavigatorSingletonStore` builds its
  // details URL from and so cannot be renamed. The redirect is what makes the section itself open something.
  //
  // Componentless rather than a tabbed page like Workflows and Application: the section holds one routable
  // entity, and a tab bar with a single button repeats the sidenav entry beside it. The `menuTitle` stays on
  // this route because DesignSidenavComponent lists the top-level routes that declare one — the branch's own
  // route carries the same key, which is harmless because the sidenav never reaches it.
  //
  // As for every other feature mounted here, the hosting application has to spread
  // BASE_ENTITY_AUTHORING_FACADE_PROVIDERS and BASE_ENTITY_AUTHORING_ENTITY_FACADES into its own providers:
  // both levels resolve their store and descriptor through BASE_ENTITY_FACADE_REGISTRY, and this library
  // cannot contribute to that token without replacing it. The branch declares its own transloco scope, so
  // this route adds none.
  {
    path: 'entities',
    title: 'ProcessPuzzle Design - Entities',
    data: { icon: 'checkbook', menuTitle: 'design.entities' },
    children: [{ path: '', pathMatch: 'full', redirectTo: 'entity-definition' }, ...BASE_ENTITY_AUTHORING_ROUTES],
  },
  // Mounted at `document`, singular, because the path segment is the snake-cased entity name — see the
  // comment on BASE_DOCUMENT_ROUTES. The hosting application still has to spread
  // BASE_DOCUMENT_FACADE_PROVIDERS and BASE_DOCUMENT_ENTITY_FACADES into its own providers, exactly as it
  // does for base-app: a base-entity list resolves the entity and its two embedded ports through
  // BASE_ENTITY_FACADE_REGISTRY, and this library cannot contribute to that token without replacing it.
  ...BASE_DOCUMENT_ROUTES,
  ...BASE_RULE_ROUTES,
  // The States section: base-state's `State Machine Definition` branch, with the four embedded levels its
  // form carries — states and transitions, and a transition's guards and actions — and the State Modeler
  // the branch mounts beside its details route.
  //
  // Nested under `states` rather than spread at this level like BASE_RULE_ROUTES and BASE_DOCUMENT_ROUTES,
  // because `/design/states` is what the sidenav entry and the Designer Home card already address, while
  // `BASE_STATE_ROUTES` mounts at `state-machine-definition` — `snakeCaseName(entityName)`, which
  // `BaseFormNavigatorSingletonStore` builds its details URL from and so cannot be renamed. The redirect
  // is what makes the section itself open something.
  //
  // Componentless rather than a tabbed page like Workflows and Application: the section holds one routable
  // entity, and a tab bar with a single button repeats the sidenav entry beside it. The `menuTitle` stays
  // on this route because DesignSidenavComponent lists the top-level routes that declare one, and
  // translates the key from this library's `design` scope — base-state's own route names `state.machines`,
  // a key of the `base_state` scope, which the sidenav does not register.
  //
  // As for base-app, base-document, base-widget and base-workflow, the hosting application has to spread
  // BASE_STATE_FACADE_PROVIDERS and BASE_STATE_ENTITY_FACADES into its own providers: every level of the
  // machine resolves its store and descriptor through BASE_ENTITY_FACADE_REGISTRY, and this library cannot
  // contribute to that token without replacing it. The branch declares its own transloco scopes, so this
  // route adds none.
  {
    path: 'states',
    title: 'ProcessPuzzle Design - States',
    data: { icon: 'flag_circle', menuTitle: 'design.states' },
    children: [{ path: '', pathMatch: 'full', redirectTo: 'state-machine-definition' }, ...BASE_STATE_ROUTES],
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
