import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { baseEntityRoutes, type EmbeddedChildRoute } from '@processpuzzle/base-entity';
import { BASE_APP_TRANSLOCO_SCOPE, BASE_ENTITY_TRANSLOCO_SCOPE } from './base-app.i18n';
import { APP_DEFINITION_ENTITY_NAME } from './domain/app-definition.descriptors';
import {
  APP_MODULE_MOUNT_ENTITY_NAME,
  APP_NAV_ITEM_ENTITY_NAME,
  APP_REGION_ENTITY_NAME,
  APP_ROUTE_ENTITY_NAME,
  APP_WIDGET_ENTITY_NAME,
  MODULE_DEFINITION_ENTITY_NAME,
} from './domain/app-entity-names';
import { AppDefinitionContainerComponent } from './feature/app-definition-container.component';
import { AppModuleMountFacade } from './feature/app-module-mount.facade';
import { AppNavItemFacade } from './feature/app-nav-item.facade';
import { AppRegionFacade } from './feature/app-region.facade';
import { AppRouteFacade } from './feature/app-route.facade';
import { AppWidgetFacade } from './feature/app-widget.facade';
import { ModuleDefinitionContainerComponent } from './feature/module-definition-container.component';

/**
 * The path segment has to be `snakeCaseName('App Definition')`, because
 * `BaseFormNavigatorSingletonStore` builds the details URL from the entity name — see the same
 * constraint on `BASE_RULE_ROUTES`.
 */
export const BASE_APP_ROUTES: Routes = [
  {
    path: 'app-definition',
    title: 'ProcessPuzzle Design - Applications',
    data: { icon: 'web', menuTitle: 'design.applications', entityName: APP_DEFINITION_ENTITY_NAME },
    component: AppDefinitionContainerComponent,
    providers: [authoringScopes()],
    children: baseEntityRoutes(embeddedDefinitionRoutes()),
  },
  // A sibling rather than a child of the branch above: a module is an aggregate of its own, with its own
  // endpoints and its own list screen. What ties the two together is data — a `ModuleMount` naming a key —
  // and the run-time shell that resolves it, not the authoring URL.
  {
    path: 'module-definition',
    title: 'ProcessPuzzle Design - Modules',
    data: { icon: 'extension', menuTitle: 'design.modules', entityName: MODULE_DEFINITION_ENTITY_NAME },
    component: ModuleDefinitionContainerComponent,
    providers: [authoringScopes()],
    children: baseEntityRoutes([routeRoute()]),
  },
];

/**
 * The transloco scopes every authoring branch of this library needs. Both are required: a route that
 * declares TRANSLOCO_SCOPE replaces the collection it inherits rather than adding to it, and the generic
 * tabs translate the framework's own `base_entity.*` keys (see BASE_ENTITY_TRANSLOCO_SCOPE).
 *
 * Registered on the top-level routes rather than on the containers, so the base-entity tabs, list and form
 * rendered in the child routes resolve entity and attribute labels from the same scopes. The embedded
 * branches below need none of their own: `base_app.app_region.*` and its siblings are keys of the scope
 * already registered here.
 *
 * Both aliases are spelled out, as everywhere in this workspace: transloco camel-cases the default alias,
 * so `base_app` would silently become `baseApp` and miss every key below it.
 */
function authoringScopes() {
  return provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE }, { scope: BASE_APP_TRANSLOCO_SCOPE, alias: BASE_APP_TRANSLOCO_SCOPE });
}

/**
 * The definition graph as route branches: a region, a route and a module mount hang below the
 * definition's details route, a nav item and a widget below the region's, a widget below the route's,
 * and a nav item below itself.
 *
 * The nesting mirrors the containment of `base-app-api.yaml` exactly, and it has to: an embedded row has
 * no id of its own to be looked up by, so the URL — `app-definition/claims-app/details/app-region/sidenav/
 * details/app-nav-item/nav-claims/details` — is what addresses it, and each segment resolves against the
 * rows of the level above it.
 *
 * These are the *authoring* routes and have nothing to do with the routes an `AppDefinition` declares:
 * `App Route` rows are data here, and it is the run-time shell that turns them into `Routes` of their
 * own. A module mount is a leaf — the module it names is a separate aggregate with its own endpoints,
 * so its routes are authored there rather than through this branch.
 */
function embeddedDefinitionRoutes(): EmbeddedChildRoute[] {
  return [
    { entityName: APP_REGION_ENTITY_NAME, facade: AppRegionFacade, children: () => [navItemRoute(), widgetRoute()] },
    routeRoute(),
    { entityName: APP_MODULE_MOUNT_ENTITY_NAME, facade: AppModuleMountFacade },
  ];
}

/**
 * The `App Route` branch, shared by both aggregates that own routes: an app's `routes` and a module's are
 * the same rows edited by the same descriptor, so the branch below them has to be the same too. Only the
 * URL prefix differs — `app-definition/demo/details/app-route/...` against
 * `module-definition/claims/details/app-route/...`.
 */
function routeRoute(): EmbeddedChildRoute {
  return { entityName: APP_ROUTE_ENTITY_NAME, facade: AppRouteFacade, children: () => [widgetRoute()] };
}

/** A nav item nests in itself — a group node's children are nav items. Hence the thunk: the structure is
 * infinite, and `loadChildren` expands one level per navigation. */
function navItemRoute(): EmbeddedChildRoute {
  return { entityName: APP_NAV_ITEM_ENTITY_NAME, facade: AppNavItemFacade, children: () => [navItemRoute()] };
}

/**
 * A widget, unlike a nav item, is a leaf branch: widgets do not nest, so there is no deeper level to
 * expand. A container widget places its siblings by id through `props.childIds`, and those siblings are
 * rows of this same list — reachable at this level, not below it.
 */
function widgetRoute(): EmbeddedChildRoute {
  return { entityName: APP_WIDGET_ENTITY_NAME, facade: AppWidgetFacade };
}
