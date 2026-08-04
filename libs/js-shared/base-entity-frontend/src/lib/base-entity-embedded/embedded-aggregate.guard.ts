import { computed, inject } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, CanActivateFn, Router, UrlTree } from '@angular/router';
import { first, firstValueFrom } from 'rxjs';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';
import { BaseUrlSegments } from '../base-form-navigator/base-url-segments';
import { findRow, readRows } from './embedded-aggregate';
import { readEmbeddedRouteChain, resolveEmbeddedRouteContext } from './embedded-route-context';

/**
 * Makes an embedded route survive a page refresh.
 *
 * Reached by drilling down, the aggregate is already in the root's store; reached by a deep link it is not,
 * and nothing else would load it — an embedded store reads its rows out of the root's payload, so it would
 * come up empty and the form would bind to nothing. Guards run before any component is created and
 * parent-before-child, which is exactly the ordering the nested case needs, and returning a `UrlTree` is how
 * a link to a row that no longer exists lands somewhere sensible instead of on an empty form.
 */
export const embeddedAggregateGuard: CanActivateFn = async (route: ActivatedRouteSnapshot): Promise<boolean | UrlTree> => {
  const router = inject(Router);
  const descriptorRegistry = inject(BaseEntityDescriptorRegistry);

  const levels = readEmbeddedRouteChain(route);
  const root = levels[0];
  if (levels.length < 2 || !root?.entityId) return router.parseUrl('/');

  // Injecting the store is what creates it, and its `onInit` hook starts the load.
  const rootStore = descriptorRegistry.getStore<BaseEntityStoreApi<BaseEntity>>(root.entityName);
  if (!rootStore) return router.parseUrl('/');

  const rootPayload = await loadRootEntity(rootStore, root.entityId);
  if (!rootPayload) return redirectToOwner(router, route);

  rootStore.setCurrentEntity(root.entityId);

  const context = resolveEmbeddedRouteContext(levels, rootPayload, (entityName) => descriptorRegistry.getDescriptor(entityName));
  if (!context) return redirectToOwner(router, route);

  // On the details route the row itself is named, and a link to one that is gone would otherwise open a form
  // bound to nothing.
  const entityId = levels[levels.length - 1].entityId;
  if (entityId !== undefined && entityId !== BaseUrlSegments.NewEntity) {
    const rows = readRows(rootPayload, context.path, context.attrName);
    if (!findRow(rows, entityId, context.referenceIdField)) return redirectToOwner(router, route);
  }

  return true;
};

// region private helper functions
/** Resolves once the entity is there, or once the store has finished loading without it. */
async function loadRootEntity(rootStore: BaseEntityStoreApi<BaseEntity>, rootId: string): Promise<BaseEntity | undefined> {
  const alreadyLoaded = rootStore.loadById(rootId);
  if (alreadyLoaded) return alreadyLoaded;

  if (rootStore.entities().length === 0 && !rootStore.isLoading()) rootStore.load({});

  const progress = computed(() => ({ entity: rootStore.loadById(rootId), isLoading: rootStore.isLoading() }));
  return firstValueFrom(
    // `first` with a predicate, not `take(1)`: the initial emission is the in-flight state, and settling on
    // it would decide the entity is missing before the request that fetches it has come back.
    toObservable(progress).pipe(first((state) => Boolean(state.entity) || !state.isLoading)),
  ).then((state) => state.entity);
}

/**
 * Back to the owner's form — the screen the user would have had to come from anyway. The embedded branch adds
 * `<entity>/<id>/details` to the owner's URL, so dropping from its entity segment onwards is what is left.
 */
function redirectToOwner(router: Router, route: ActivatedRouteSnapshot): UrlTree {
  const url = router.getCurrentNavigation()?.finalUrl?.toString() ?? router.url;
  const branchSegment = '/' + embeddedBranchSegment(route);
  const ownerUrl = url.substring(0, url.lastIndexOf(branchSegment));
  return router.parseUrl(ownerUrl.length > 0 ? ownerUrl : '/');
}

/** The static segment naming the embedded entity, whether the guard ran on it or on its details child. */
function embeddedBranchSegment(route: ActivatedRouteSnapshot): string {
  const path = route.routeConfig?.path ?? '';
  return path.includes(':' + BaseUrlSegments.EntityID) ? (route.parent?.routeConfig?.path ?? path) : path;
}
// endregion
