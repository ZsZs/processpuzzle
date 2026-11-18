import type { ActivatedRouteSnapshot } from '@angular/router';
import { toRsql } from '../base-entity-service/rsql';
import { FILTER_BY_CONTEXT_ROUTE_DATA_KEY, REQUESTED_ENTITY_ROUTE_DATA_KEY, RSQL_FILTER_ROUTE_DATA_KEY } from './entity-screen-route-data';

/** What a route's list is pre-filtered with, as the route declares it. */
export interface RouteListFilter {
  /** Raw RSQL the list is always filtered by. */
  rsqlFilter?: string;
  /** The working context whose active entity the list can be narrowed to. */
  filterByContext?: string;
}

/**
 * The filters the screens route of `entityName` declares, read from that route's own configuration.
 *
 * It walks up from the list's route to the screens route that asked for this entity, and reads only what that route
 * declares: `data` is inherited by every route below, and a list of another entity nested under the same screens must
 * not pick the filters up. A list reached without such a route has no filters.
 */
export function routeListFilterOf(snapshot: ActivatedRouteSnapshot | null, entityName: string): RouteListFilter {
  for (let current = snapshot; current; current = current.parent) {
    const declared = current.routeConfig?.data;
    if (declared?.[REQUESTED_ENTITY_ROUTE_DATA_KEY] !== entityName) continue;
    return { rsqlFilter: nonEmpty(declared[RSQL_FILTER_ROUTE_DATA_KEY]), filterByContext: nonEmpty(declared[FILTER_BY_CONTEXT_ROUTE_DATA_KEY]) };
  }
  return {};
}

function nonEmpty(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : undefined;
}

/**
 * The RSQL a list is loaded with: the route's own filter and, while the toggle is on and there is an active entity, the
 * condition that the context attribute equals that entity's id. Joined with `;` (AND). Undefined when neither applies,
 * so a plain list loads exactly as it did before.
 */
export function listQueryOf(filter: RouteListFilter, activeContextId: string | undefined, onlyActive: boolean): string | undefined {
  const parts: string[] = [];
  if (filter.rsqlFilter) parts.push(filter.rsqlFilter);
  if (filter.filterByContext && activeContextId && onlyActive) {
    parts.push(toRsql([{ property: filter.filterByContext, operator: '==', value: activeContextId }]));
  }
  return parts.length > 0 ? parts.join(';') : undefined;
}
