import type { EnvironmentProviders, Provider, ProviderToken } from '@angular/core';
import { Route, Routes } from '@angular/router';
import { BaseEntity } from './base-entity/base-entity';
import { ACTIVE_ENTITY_FACADE } from './base-entity-facade/active-entity-facade.token';
import { BaseEntityFacade } from './base-entity-facade/base-entity-facade';
import { embeddedAggregateGuard } from './base-entity-embedded/embedded-aggregate.guard';
import { EmbeddedEntityHostComponent } from './base-entity-embedded/embedded-entity-host.component';
import { BaseEntityFormComponent } from './base-form/base-entity-form.component';
import { BaseUrlSegments } from './base-form-navigator/base-url-segments';
import { snakeCaseName } from './base-form-navigator/base-form-navigator.store';
import { EMBEDDED_ENTITY_ROUTE_DATA_KEY, ENTITY_NAME_ROUTE_DATA_KEY } from './base-form-navigator/entity-route.registry';
import { BaseEntityListComponent } from './base-list/base-entity-list.component';

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
export function baseEntityRoutes(embeddedChildren: EmbeddedChildRoute[] = []): Routes {
  return [
    { path: '', redirectTo: BaseUrlSegments.ListForm, pathMatch: 'full' },
    {
      path: ':' + BaseUrlSegments.EntityID + '/' + BaseUrlSegments.DetailsForm,
      component: BaseEntityFormComponent,
      ...(embeddedChildren.length > 0 ? { loadChildren: () => embeddedChildren.map(embeddedChildRoute) } : {}),
    },
    { path: BaseUrlSegments.ListForm, component: BaseEntityListComponent },
  ];
}

/** The stand-alone entity routes, unchanged for every caller that has no embedded children to mount. */
export const BASE_ENTITY_ROUTES: Routes = baseEntityRoutes();

// region private helper functions
function embeddedChildRoute(child: EmbeddedChildRoute): Route {
  return {
    path: snakeCaseName(child.entityName),
    data: { [ENTITY_NAME_ROUTE_DATA_KEY]: child.entityName, [EMBEDDED_ENTITY_ROUTE_DATA_KEY]: true },
    providers: [{ provide: ACTIVE_ENTITY_FACADE, useExisting: child.facade }, ...(child.providers ?? [])],
    canActivate: [embeddedAggregateGuard],
    component: EmbeddedEntityHostComponent,
    loadChildren: () => embeddedEntityRoutes(child.children?.() ?? []),
  };
}

/**
 * An embedded level gets its details form and nothing else — no list, and so no redirect to one. The rows
 * are already listed on the form of the entity that contains them, which is also the only place they can
 * be reached from, so a list of its own would be a second door to the same room.
 */
function embeddedEntityRoutes(embeddedChildren: EmbeddedChildRoute[]): Routes {
  return [
    {
      path: ':' + BaseUrlSegments.EntityID + '/' + BaseUrlSegments.DetailsForm,
      component: BaseEntityFormComponent,
      // Guarded again here, not only on the branch above it: this is the route that names the row, so it is
      // the first place a link to a row that no longer exists can be told apart from a valid one.
      canActivate: [embeddedAggregateGuard],
      ...(embeddedChildren.length > 0 ? { loadChildren: () => embeddedChildren.map(embeddedChildRoute) } : {}),
    },
  ];
}
// endregion
