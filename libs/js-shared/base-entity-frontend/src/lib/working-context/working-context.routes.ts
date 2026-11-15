import { inject } from '@angular/core';
import { CanActivateFn, CanMatchFn, Route, Router, Routes, UrlSegment } from '@angular/router';
import { WorkingContextEmptyStateComponent } from './working-context-empty-state.component';
import { WorkingContextService } from './working-context.service';
import { WORKING_CONTEXT_ID_PARAM, WORKING_CONTEXT_ROUTE_DATA_KEY, WORKING_CONTEXT_SEGMENT } from './working-context.url';

/**
 * The routes of `routes` bound to the context `contextName`, mounted twice:
 *
 * - `ctx/<name>/:contextId/<route>` resolves the active entity, through {@link workingContextGuard}. A bare
 *   `ctx/<name>/<id>` lands on the first route.
 * - `ctx/<name>/<route>` is the same view **without** a context. It renders the "Select a …" empty state with
 *   the picker embedded instead of the view — it never redirects to a list — and restores the last active
 *   entity first when the context persists to localStorage.
 *
 * The id branch refuses an id that equals the first segment of one of the views, which is what keeps
 * `ctx/race/participants` the empty state rather than a race called `participants`.
 *
 * Nest a child context by calling this again inside `routes`; its URL then reads
 * `ctx/regatta/<id>/ctx/race/<id>/...`.
 */
export function workingContextRoutes(contextName: string, routes: Routes): Routes {
  const viewSegments = routes.map((route) => firstSegment(route.path)).filter((segment): segment is string => !!segment);
  const landing = viewSegments[0];
  const data = { [WORKING_CONTEXT_ROUTE_DATA_KEY]: contextName };
  return [
    {
      path: `${WORKING_CONTEXT_SEGMENT}/${contextName}/:${WORKING_CONTEXT_ID_PARAM}`,
      data,
      canMatch: [idIsNotAView(viewSegments)],
      canActivate: [workingContextGuard],
      children: [...(landing ? [{ path: '', redirectTo: landing, pathMatch: 'full' as const }] : []), ...routes],
    },
    {
      path: `${WORKING_CONTEXT_SEGMENT}/${contextName}`,
      data,
      canActivate: [restoreWorkingContextGuard],
      children: [{ path: '**', component: WorkingContextEmptyStateComponent }],
    },
  ];
}

/**
 * Makes the entity named in the URL the active one. An id the entity's store cannot load is not an error page:
 * the same view is shown without a context, so the user can pick another.
 */
export const workingContextGuard: CanActivateFn = async (route, state) => {
  const service = inject(WorkingContextService);
  const router = inject(Router);
  const name = route.routeConfig?.data?.[WORKING_CONTEXT_ROUTE_DATA_KEY] as string;
  const id = route.params[WORKING_CONTEXT_ID_PARAM] as string;

  if (await service.load(name, id)) {
    service.remember(name, id);
    return true;
  }
  service.forget(name, id);
  return router.parseUrl(state.url.replace(`/${WORKING_CONTEXT_SEGMENT}/${name}/${id}`, `/${WORKING_CONTEXT_SEGMENT}/${name}`));
};

/** A context view opened without a context continues with the last active entity, when the context persists it. */
export const restoreWorkingContextGuard: CanActivateFn = (route, state) => {
  const service = inject(WorkingContextService);
  const router = inject(Router);
  const name = route.routeConfig?.data?.[WORKING_CONTEXT_ROUTE_DATA_KEY] as string;
  const lastId = service.lastId(name);
  if (!lastId) return true;

  const prefix = `/${WORKING_CONTEXT_SEGMENT}/${name}`;
  const at = state.url.split(/[?#]/)[0].lastIndexOf(prefix);
  if (at < 0) return true;
  return router.parseUrl(state.url.slice(0, at + prefix.length) + '/' + encodeURIComponent(lastId) + state.url.slice(at + prefix.length));
};

// region private helper functions
function firstSegment(path: string | undefined): string | undefined {
  const segment = path?.split('/')[0];
  return segment && !segment.startsWith(':') && segment !== '**' ? segment : undefined;
}

function idIsNotAView(viewSegments: string[]): CanMatchFn {
  return (_route: Route, segments: UrlSegment[]) => !viewSegments.includes(segments[2]?.path ?? '');
}
// endregion
