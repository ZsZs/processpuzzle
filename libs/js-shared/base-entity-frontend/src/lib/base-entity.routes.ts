import type { EnvironmentProviders, Provider, ProviderToken } from '@angular/core';
import { Route, Routes } from '@angular/router';
import { BaseEntity } from './base-entity/base-entity';
import type { EntityTabDescriptor } from './base-entity/base-entity.descriptor';
import { ACTIVE_ENTITY_FACADE } from './base-entity-facade/active-entity-facade.token';
import { BaseEntityFacade } from './base-entity-facade/base-entity-facade';
import { embeddedAggregateGuard } from './base-entity-embedded/embedded-aggregate.guard';
import { EmbeddedEntityHostComponent } from './base-entity-embedded/embedded-entity-host.component';
import { BaseEntityFormComponent } from './base-form/base-entity-form.component';
import { unsavedChangesGuard } from './base-form/unsaved-changes/unsaved-changes.guard';
import { BaseUrlSegments } from './base-form-navigator/base-url-segments';
import { snakeCaseName } from './base-form-navigator/base-form-navigator.store';
import { EMBEDDED_ENTITY_ROUTE_DATA_KEY, ENTITY_NAME_ROUTE_DATA_KEY } from './base-form-navigator/entity-route.registry';
import { BaseEntityListComponent } from './base-list/base-entity-list.component';
import { EmbeddedListScreenComponent } from './base-entity-embedded/embedded-list-screen.component';
import { contextObjectGuard, CONTEXT_OBJECT_ENTITY_ROUTE_DATA_KEY } from './working-context/context-object.guard';
import { AGGREGATE_ID_PARAM, WORKING_CONTEXT_ROUTE_DATA_KEY } from './working-context/working-context.url';

/**
 * An embedded child to mount under an entity's details route. The entity's descriptor has to reference it
 * with `FormControlType.EMBEDDED_COMPONENTS`, and the child's own descriptor has to name the entity as a
 * `componentParent`.
 */
export interface EmbeddedChildRoute {
  entityName: string;
  facade: ProviderToken<BaseEntityFacade<BaseEntity>>;
  /** Extra providers for the branch — a transloco scope, most often. */
  providers?: Array<Provider | EnvironmentProviders>;
  /**
   * The child's own embedded children. A thunk so a child may name itself: `App Nav Item` nests inside
   * `App Nav Item`, which no eager value could express.
   */
  children?: () => EmbeddedChildRoute[];
}

/**
 * The list and details routes of one entity, plus a branch per embedded child.
 *
 * An embedded child is addressed by its position in the containing document, so its screens hang **below**
 * the owner's details route — `test-entity/1/details/embedded-component/embedded_1_1/details`. That nesting
 * is not decoration: it is what carries the path, so a deep link and a refresh resolve to the same row, and
 * what makes the child unreachable except through its parent, since the parent's segments are part of the
 * URL that matches at all.
 *
 * The branch is attached with `loadChildren` rather than `children` because it is expanded one level per
 * navigation. A child that names itself would otherwise be an infinite structure; lazily, it is finite at
 * any moment and Angular memoizes each level it has expanded.
 */
export function baseEntityRoutes(embeddedChildren: EmbeddedChildRoute[] = [], extraTabs: EntityTabDescriptor[] = [], contextObject?: ContextObjectRoutes): Routes {
  return [
    { path: '', redirectTo: BaseUrlSegments.ListForm, pathMatch: 'full' },
    {
      path: ':' + BaseUrlSegments.EntityID + '/' + BaseUrlSegments.DetailsForm,
      component: BaseEntityFormComponent,
      canDeactivate: [unsavedChangesGuard],
      ...(embeddedChildren.length > 0 ? { loadChildren: () => embeddedChildren.map((child) => embeddedChildRoute(child)) } : {}),
    },
    // Siblings of the details route rather than children of it: an extra tab is another screen *of the
    // entity*, addressed by the same `<entity>/<id>` prefix, not a part of the details form. The prefix is
    // what BaseFormNavigatorSingletonStore.determineBaseUrl counts back over, which is why the shape has
    // to match the details route's exactly.
    ...extraTabs.map(extraTabRoute),
    { path: BaseUrlSegments.ListForm, component: BaseEntityListComponent },
    ...(contextObject && embeddedChildren.length > 0 ? [contextObjectRoute(contextObject, embeddedChildren)] : []),
  ];
}

/** An entity that `canContextObject`: what its routes need to know to address the parts through the picked object. */
export interface ContextObjectRoutes {
  entityName: string;
}

/** The stand-alone entity routes, unchanged for every caller that has no embedded children to mount. */
export const BASE_ENTITY_ROUTES: Routes = baseEntityRoutes();

// region private helper functions
/**
 * One extra tab's route. `canMatch` and `children` are only set when the tab asked for them, so a tab that
 * declares neither produces exactly the route it always did.
 *
 * `children` is **copied**. The same `Routes` array may be spread into more than one place in an
 * application's config — `BASE_APP_ROUTES` is mounted both under the designer and standalone — and a guard
 * that populates a tab's children by assigning to `route.children` would otherwise be writing through an
 * array shared with the other mount. A copy per route keeps the two independent.
 */
function extraTabRoute(tab: EntityTabDescriptor): Route {
  const route: Route = { path: ':' + BaseUrlSegments.EntityID + '/' + tab.segment, component: tab.component };
  if (tab.canMatch) route.canMatch = tab.canMatch;
  if (tab.children) route.children = [...tab.children];
  return route;
}

/**
 * The branch of one embedded child: its details routes below the owner it is mounted under, guarded so a row that is gone
 * sends the user back to its owner. Mounted under an entity's details route, and under a working context's.
 */
export function embeddedChildRoute(child: EmbeddedChildRoute, withList = false): Route {
  return {
    path: snakeCaseName(child.entityName),
    data: { [ENTITY_NAME_ROUTE_DATA_KEY]: child.entityName, [EMBEDDED_ENTITY_ROUTE_DATA_KEY]: true },
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: child.facade }, ...(child.providers ?? [])],
    canActivate: [embeddedAggregateGuard],
    component: EmbeddedEntityHostComponent,
    loadChildren: () => embeddedEntityRoutes(child.children?.() ?? [], withList),
  };
}

/**
 * `<entity>/<id>/<part>/list` — the parts of an aggregate listed straight from the aggregate's own address, without
 * its form in between. The id may be `unknown`, which {@link contextObjectGuard} resolves to the context object or to
 * the choice of one. A bare `<entity>/<id>` lands on the first kind of part.
 *
 * It follows the details and the list routes so neither is taken for an id, and the `details` segment is not part of its
 * URL: the parts hang directly below the id.
 */
function contextObjectRoute({ entityName }: ContextObjectRoutes, embeddedChildren: EmbeddedChildRoute[]): Route {
  return {
    path: ':' + AGGREGATE_ID_PARAM,
    data: { [WORKING_CONTEXT_ROUTE_DATA_KEY]: snakeCaseName(entityName), [CONTEXT_OBJECT_ENTITY_ROUTE_DATA_KEY]: entityName },
    canActivate: [contextObjectGuard],
    children: [{ path: '', pathMatch: 'full', redirectTo: snakeCaseName(embeddedChildren[0].entityName) + '/' + BaseUrlSegments.ListForm }, ...embeddedChildren.map((child) => embeddedChildRoute(child, true))],
  };
}

/**
 * An embedded level gets its details form and nothing else — no list, and so no redirect to one. The rows
 * are already listed on the form of the entity that contains them, which is also the only place they can
 * be reached from, so a list of its own would be a second door to the same room.
 */
function embeddedEntityRoutes(embeddedChildren: EmbeddedChildRoute[], withList = false): Routes {
  return [
    ...(withList ? [{ path: BaseUrlSegments.ListForm, component: EmbeddedListScreenComponent, canActivate: [embeddedAggregateGuard] }] : []),
    {
      path: ':' + BaseUrlSegments.EntityID + '/' + BaseUrlSegments.DetailsForm,
      component: BaseEntityFormComponent,
      // Guarded again here, not only on the branch above it: this is the route that names the row, so it is
      // the first place a link to a row that no longer exists can be told apart from a valid one.
      canActivate: [embeddedAggregateGuard],
      canDeactivate: [unsavedChangesGuard],
      ...(embeddedChildren.length > 0 ? { loadChildren: () => embeddedChildren.map((child) => embeddedChildRoute(child)) } : {}),
    },
  ];
}
// endregion
