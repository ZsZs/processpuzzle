import { Routes } from '@angular/router';
import { BASE_APP_ROUTES } from '@processpuzzle/base-app';
import { BASE_DOCUMENT_ROUTES } from '@processpuzzle/base-document';
import { BASE_ENTITY_AUTHORING_ROUTES } from '@processpuzzle/base-entity';
import { BASE_RULE_ROUTES } from '@processpuzzle/base-rule';
import { BASE_STARTER_ROUTES } from '@processpuzzle/base-starter';
import { BASE_STATE_ROUTES } from '@processpuzzle/base-state';
import { BASE_WIDGET_ROUTES } from '@processpuzzle/widgets';
import { BASE_WORKFLOW_ROUTES } from '@processpuzzle/base-workflow';
import { BASE_CHAT_ROUTES } from '@processpuzzle/base-chat';
import { BASE_EVENT_ROUTES } from '@processpuzzle/base-event';
import { BASE_RELEASE_ROUTES } from '@processpuzzle/base-release';
import { BASE_CALCULATION_ROUTES, CALCULATION_RUNS_ROUTES } from '@processpuzzle/base-calculation';
import { ApplicationDesignerComponent } from './application-designer/application-designer.component';
import { CalculationDesignerComponent } from './calculation-designer/calculation-designer.component';
import { EntityDesignerComponent } from './entity-designer/entity-designer.component';
import { AiDesignerComponent } from './ai-designer/ai-designer.component';
import { DesignContentComponent } from './content/design-content.component';
import { DesignWorkbenchComponent } from './workbench/design-workbench.component';
import { WorkflowDesignerComponent } from './workflow-designer/workflow-designer.component';
import { TutorialViewerComponent } from './tutorial/tutorial-viewer.component';

/**
 * The sections of the designer, one per feature, in the order the sidenav lists them. Hosted by
 * {@link DESIGN_ROUTES}' workbench, which is what puts the design assistant beside every one of them; a hosting
 * application mounts DESIGN_ROUTES, and reads this list only to describe the sections.
 */
export const DESIGN_SECTIONS: Routes = [
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
  // A tabbed page like Calculations — see EntityDesignerComponent: the entity definitions, and the units the organization
  // adds to the standard catalog. (With one routable entity a tab bar repeated the sidenav entry beside it; with two it
  // is the way to the second.) The `menuTitle` stays on
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
    component: EntityDesignerComponent,
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
  // One section, two tabs — see CalculationDesignerComponent: the Calculation Definition branch, whose form
  // carries its own Source, Decision tables and Evaluate tabs, and the runs screen. Both spreads are
  // unchanged, so they keep their own transloco scopes and stay mountable elsewhere (the testbed mounts them
  // a second time under `/base-calculation/samples`). After States because a run is triggered by entity
  // changes and state transitions; before Workflows, which do not depend on it.
  //
  // As for every other feature mounted here, the hosting application has to spread
  // BASE_CALCULATION_FACADE_PROVIDERS and BASE_CALCULATION_ENTITY_FACADES into its own providers, because
  // the definition list resolves its store and descriptor through BASE_ENTITY_FACADE_REGISTRY.
  {
    path: 'calculations',
    title: 'ProcessPuzzle Design - Calculations',
    data: { icon: 'calculate', menuTitle: 'design.calculations' },
    component: CalculationDesignerComponent,
    children: [{ path: '', pathMatch: 'full', redirectTo: 'calculation-definition' }, ...BASE_CALCULATION_ROUTES, ...CALCULATION_RUNS_ROUTES],
  },
  // One section, seven tabs — see WorkflowDesignerComponent. `BASE_WORKFLOW_ROUTES` is spread unchanged, so
  // its branches keep their own transloco scopes and stay mountable elsewhere (the testbed mounts them a
  // second time under `/base-workflow/samples`); only the prefix they hang under is new. As for base-app,
  // base-document and base-widget, the hosting application has to spread BASE_WORKFLOW_FACADE_PROVIDERS
  // and BASE_WORKFLOW_ENTITY_FACADES into its own providers, because a base-entity list resolves the
  // entity and its embedded levels through BASE_ENTITY_FACADE_REGISTRY and this library cannot contribute
  // to that token without replacing it.
  //
  // BASE_EVENT_ROUTES is the seventh tab, the event catalog: base-workflow's start events and throw/catch
  // events name an Event Definition by id without importing base-event, so its authoring screen belongs
  // beside them. The same facade rule applies: BASE_EVENT_FACADE_PROVIDERS and BASE_EVENT_ENTITY_FACADES,
  // which also fill the Event pickers of the workflow's own form.
  //
  // Nested under one route rather than spread at this level like BASE_RULE_ROUTES and
  // BASE_DOCUMENT_ROUTES: seven branches for one authoring subject would be seven sidenav entries, and their
  // `menuTitle` keys live in the `base_workflow` scope while DesignSidenavComponent registers only
  // `design`.
  {
    path: 'workflows',
    title: 'ProcessPuzzle Design - Workflows',
    data: { icon: 'schema', menuTitle: 'design.workflows' },
    component: WorkflowDesignerComponent,
    // No `providers`: the tab bar names its `design` scope on the directive itself, so this route adds
    // nothing to the injectors the tabs' own screens resolve through.
    children: [{ path: '', pathMatch: 'full', redirectTo: 'workflow' }, ...BASE_WORKFLOW_ROUTES, ...BASE_EVENT_ROUTES],
  },
  // The Chat section: base-chat's `Chat Definition` branch — which entity types get a conversation, who may read,
  // write and start one, and whether the subject's state limits writing. Nested under `chats` like States and
  // Entities, and for the same reasons: `BASE_CHAT_ROUTES` mounts at `chat-definition`, the snake-cased entity
  // name `BaseFormNavigatorSingletonStore` builds its details URL from, and the sidenav lists the top-level
  // routes that declare a `design.*` menu title. Componentless, because the section holds one routable entity.
  // After Workflows, the last of the sections whose definitions it refers to (entities and states); it is
  // released on its own, after both.
  //
  // As for every other feature mounted here, the hosting application has to spread
  // BASE_CHAT_FACADE_PROVIDERS and BASE_CHAT_ENTITY_FACADES into its own providers. The branch declares its own
  // transloco scopes, so this route adds none.
  {
    path: 'chats',
    title: 'ProcessPuzzle Design - Chat',
    data: { icon: 'forum', menuTitle: 'design.chat' },
    children: [{ path: '', pathMatch: 'full', redirectTo: 'chat-definition' }, ...BASE_CHAT_ROUTES],
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
  // The video tutorials the designer cards link to, one route per tutorial (`tutorials/entity-designer`). No menuTitle:
  // they are reached from a card, not from the sidenav. The viewer reads the `tutorial` entity, anonymously in the testbed.
  {
    path: 'tutorials/:key',
    title: 'ProcessPuzzle Design - Tutorial',
    component: TutorialViewerComponent,
  },
  // The AI Designer section: the design assistant on a page of its own. It drafts into the other sections, so it
  // sits after them and before the starters and the release, which act on what they hold.
  {
    path: 'assistant',
    title: 'ProcessPuzzle Design - AI Designer',
    data: { icon: 'smart_toy', menuTitle: 'design.assistant' },
    component: AiDesignerComponent,
  },
  // The Business Starters section: import a bundle of definitions into the organization. Spread at this
  // level like BASE_RULE_ROUTES — one screen, mounted at `starters`, with a `design.*` menu title — and it
  // registers its own `base_starter` scope, whose translations the hosting application has to copy to
  // `assets/i18n/base_starter`. Last because it acts on everything the sections above it define.
  ...BASE_STARTER_ROUTES,
  // The Release section: the one place the designer's drafts reach the live application. Every definition
  // a release carries goes live together or not at all; see base-release's ReleasePanelComponent, which
  // registers its own `base_release` scope. Last, because it acts on everything above.
  ...BASE_RELEASE_ROUTES,
];

/**
 * What a hosting application mounts under its `design` path: the workbench, with every section as a child. The
 * workbench carries no path, title or menu title of its own, so the sections' URLs are what they were before it,
 * and the sidenav lists the sections, not the workbench.
 */
export const DESIGN_ROUTES: Routes = [{ path: '', component: DesignWorkbenchComponent, children: DESIGN_SECTIONS }];
