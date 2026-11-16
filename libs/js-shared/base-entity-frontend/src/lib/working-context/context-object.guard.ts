import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { WorkingContextService } from './working-context.service';
import { AGGREGATE_ID_PARAM, UNKNOWN_CONTEXT_OBJECT_ID, WORKING_CONTEXT_ROUTE_DATA_KEY } from './working-context.url';

/** Route data on an aggregate's `:entityId` route naming the entity — the guard has no other way to know which one it guards. */
export const CONTEXT_OBJECT_ENTITY_ROUTE_DATA_KEY = 'contextObjectEntity';

/**
 * Guards `<aggregate>/:entityId/<embedded>/…` of an entity that `canContextObject`.
 *
 * - A real id makes that object the context object: it is loaded, remembered, and the route activates. An id that no
 *   longer resolves is forgotten, and the URL is treated as if it named none.
 * - The id `unknown` stands for "whichever object I was working with". The remembered one replaces it; with none, the user
 *   is sent to the aggregate's list to choose, and the URL asked for is kept so choosing continues there.
 */
export const contextObjectGuard: CanActivateFn = async (route, state) => {
  const service = inject(WorkingContextService);
  const router = inject(Router);
  const entityName = route.routeConfig?.data?.[CONTEXT_OBJECT_ENTITY_ROUTE_DATA_KEY] as string;
  const name = route.routeConfig?.data?.[WORKING_CONTEXT_ROUTE_DATA_KEY] as string;
  const id = route.params[AGGREGATE_ID_PARAM] as string;
  service.ensureAggregate(entityName);

  if (id !== UNKNOWN_CONTEXT_OBJECT_ID) {
    if (await service.load(name, id)) {
      service.remember(name, id);
      return true;
    }
    service.forget(name, id);
  }

  const remembered = id === UNKNOWN_CONTEXT_OBJECT_ID ? service.lastId(name) : undefined;
  if (remembered && (await service.load(name, remembered))) {
    return router.parseUrl(withId(state.url, id, remembered));
  }

  service.rememberTarget(name, withId(state.url, id, UNKNOWN_CONTEXT_OBJECT_ID));
  return router.parseUrl(listUrlOf(state.url, id));
};

/** The same URL with the aggregate's id segment replaced. */
function withId(url: string, id: string, replacement: string): string {
  return url.replace(`/${encodeURIComponent(id)}`, `/${encodeURIComponent(replacement)}`);
}

/** The aggregate's own list: everything before the id segment, then `list`. */
function listUrlOf(url: string, id: string): string {
  const path = url.split(/[?#]/)[0];
  const at = path.indexOf(`/${encodeURIComponent(id)}`);
  return `${path.slice(0, at)}/list`;
}
