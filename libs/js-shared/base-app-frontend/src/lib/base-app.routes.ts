import { Routes } from '@angular/router';
import { provideTranslocoScope } from '@jsverse/transloco';
import { baseEntityRoutes, type EmbeddedChildRoute } from '@processpuzzle/base-entity';
import { BASE_APP_TRANSLOCO_SCOPE, BASE_ENTITY_TRANSLOCO_SCOPE } from './base-app.i18n';
import { APP_DEFINITION_ENTITY_NAME } from './domain/app-definition.descriptors';
import { APP_NAV_ITEM_ENTITY_NAME, APP_PAGE_ENTITY_NAME, APP_REGION_ENTITY_NAME, APP_WIDGET_ENTITY_NAME } from './domain/app-entity-names';
import { AppDefinitionContainerComponent } from './feature/app-definition-container.component';
import { AppNavItemFacade } from './feature/app-nav-item.facade';
import { AppPageFacade } from './feature/app-page.facade';
import { AppRegionFacade } from './feature/app-region.facade';
import { AppWidgetFacade } from './feature/app-widget.facade';

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
    // Provided here rather than on the container, so the base-entity tabs, list and form rendered in the
    // child routes resolve the entity and attribute labels from the same scopes. Both are needed: a route
    // that declares TRANSLOCO_SCOPE replaces the collection it inherits rather than adding to it, and the
    // generic tabs translate the framework's own `base_entity.*` keys (see BASE_ENTITY_TRANSLOCO_SCOPE).
    // The embedded branches below need none of their own: `base_app.app_region.*` and its siblings are
    // keys of the scope already registered here.
    providers: [provideTranslocoScope({ scope: BASE_ENTITY_TRANSLOCO_SCOPE, alias: BASE_ENTITY_TRANSLOCO_SCOPE }, { scope: BASE_APP_TRANSLOCO_SCOPE, alias: BASE_APP_TRANSLOCO_SCOPE })],
    children: baseEntityRoutes(embeddedDefinitionRoutes()),
  },
];

/**
 * The definition graph as route branches: a region and a page hang below the definition's details route,
 * a nav item and a widget below theirs, and each of those two below itself.
 *
 * The nesting mirrors the containment of `base-app-api.yaml` exactly, and it has to: an embedded row has
 * no id of its own to be looked up by, so the URL — `app-definition/claims-app/details/app-region/sidenav/
 * details/app-nav-item/nav-claims/details` — is what addresses it, and each segment resolves against the
 * rows of the level above it.
 */
function embeddedDefinitionRoutes(): EmbeddedChildRoute[] {
  return [
    { entityName: APP_REGION_ENTITY_NAME, facade: AppRegionFacade, children: () => [navItemRoute(), widgetRoute()] },
    { entityName: APP_PAGE_ENTITY_NAME, facade: AppPageFacade, children: () => [widgetRoute()] },
  ];
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
