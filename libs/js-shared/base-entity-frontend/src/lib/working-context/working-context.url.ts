import { ActivatedRouteSnapshot } from '@angular/router';

/** The URL segment every context route starts with: `ctx/<name>/<id>/...`. */
export const WORKING_CONTEXT_SEGMENT = 'ctx';
/** Route data naming the context a route belongs to. Read from the route's own config, never inherited. */
export const WORKING_CONTEXT_ROUTE_DATA_KEY = 'workingContext';
export const WORKING_CONTEXT_ID_PARAM = 'contextId';
/** The id of an aggregate's own route (`<aggregate>/:entityId`), which is where a context object's context level sits. */
export const AGGREGATE_ID_PARAM = 'entityId';
/** What an aggregate's id is while no context object is known — see {@link UNKNOWN_CONTEXT_OBJECT_ID}'s guard. */
export const UNKNOWN_CONTEXT_OBJECT_ID = 'unknown';

/** A route that names the working context its id segment selects. */
export interface WorkingContextRoute {
  name: string;
  /** The route parameter holding the context's entity id. */
  idParam: string;
  /** True for an aggregate's own `:entityId` route (a **context object**), false for an application's `ctx/<name>/:contextId`. */
  aggregate: boolean;
}

/**
 * The context a route declares itself as the id segment of, from its own config — never inherited. Either an
 * application's `ctx/<name>/:contextId`, or an aggregate's `<aggregate>/:entityId` carrying the context's name as route data.
 */
export function workingContextOfRoute(snapshot: ActivatedRouteSnapshot): WorkingContextRoute | undefined {
  const name = snapshot.routeConfig?.data?.[WORKING_CONTEXT_ROUTE_DATA_KEY];
  const path = snapshot.routeConfig?.path ?? '';
  if (typeof name !== 'string') return undefined;
  if (path.includes(':' + WORKING_CONTEXT_ID_PARAM)) return { name, idParam: WORKING_CONTEXT_ID_PARAM, aggregate: false };
  if (path === ':' + AGGREGATE_ID_PARAM) return { name, idParam: AGGREGATE_ID_PARAM, aggregate: true };
  return undefined;
}

/** One context the URL walks through. `id` is undefined on the empty-state branch — a context view opened without one. */
export interface WorkingContextRouteLevel {
  name: string;
  /** True for a context object: the id segment of an aggregate's own URL, not an application's `ctx/<name>/<id>`. */
  aggregate: boolean;
  id: string | undefined;
  /** The URL up to and including this level's `ctx/<name>[/<id>]` segments. */
  url: string;
  /** The URL before this level's `ctx` segment, which a sibling context URL is built on. */
  baseUrl: string;
  /** What follows this level in the current URL — the view inside the context, with a leading `/`, or `''`. */
  rest: string;
}

/**
 * The contexts the URL walks through, outermost first. Read from the route configs the snapshot passes, so a
 * refresh and a deep link describe the same contexts as a selection does.
 */
export function readWorkingContextLevels(snapshot: ActivatedRouteSnapshot | null): WorkingContextRouteLevel[] {
  const snapshots: ActivatedRouteSnapshot[] = [];
  for (let current = snapshot; current; current = current.parent) snapshots.unshift(current);

  const levels: WorkingContextRouteLevel[] = [];
  let url = '';
  for (const current of snapshots) {
    const baseUrl = url;
    url += (current.url ?? []).map((segment) => '/' + segment.path).join('');
    const aggregateRoute = workingContextOfRoute(current);
    if (aggregateRoute?.aggregate) {
      levels.push({ name: aggregateRoute.name, aggregate: true, id: current.params[AGGREGATE_ID_PARAM] as string, url, baseUrl, rest: '' });
      continue;
    }
    const name = current.routeConfig?.data?.[WORKING_CONTEXT_ROUTE_DATA_KEY];
    if (typeof name !== 'string' || !current.routeConfig?.path?.startsWith(WORKING_CONTEXT_SEGMENT + '/')) continue;

    const declaresId = current.routeConfig.path.includes(':' + WORKING_CONTEXT_ID_PARAM);
    levels.push({ name, aggregate: false, id: declaresId ? (current.params[WORKING_CONTEXT_ID_PARAM] as string) : undefined, url, baseUrl, rest: '' });
  }
  for (const level of levels) level.rest = url.slice(level.url.length);
  return levels;
}
