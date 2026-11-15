import { ActivatedRouteSnapshot } from '@angular/router';

/** The URL segment every context route starts with: `ctx/<name>/<id>/...`. */
export const WORKING_CONTEXT_SEGMENT = 'ctx';
/** Route data naming the context a route belongs to. Read from the route's own config, never inherited. */
export const WORKING_CONTEXT_ROUTE_DATA_KEY = 'workingContext';
export const WORKING_CONTEXT_ID_PARAM = 'contextId';

/** One context the URL walks through. `id` is undefined on the empty-state branch — a context view opened without one. */
export interface WorkingContextRouteLevel {
  name: string;
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
    const name = current.routeConfig?.data?.[WORKING_CONTEXT_ROUTE_DATA_KEY];
    if (typeof name !== 'string' || !current.routeConfig?.path?.startsWith(WORKING_CONTEXT_SEGMENT + '/')) continue;

    const declaresId = current.routeConfig.path.includes(':' + WORKING_CONTEXT_ID_PARAM);
    levels.push({ name, id: declaresId ? (current.params[WORKING_CONTEXT_ID_PARAM] as string) : undefined, url, baseUrl, rest: '' });
  }
  for (const level of levels) level.rest = url.slice(level.url.length);
  return levels;
}
