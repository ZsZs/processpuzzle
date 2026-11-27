import type { ActivatedRouteSnapshot } from '@angular/router';

/** One level of the trail: the text to show (a literal or a Transloco key) and where it leads. */
export interface BreadcrumbItem {
  label: string;
  url: string;
}

/**
 * Reads the trail off the activated route tree, root first.
 *
 * A route contributes a crumb when it has a label: `data.breadcrumb` (a string; `false` hides the route), else
 * `data.translocoId`, else a static `title`. Routes without one — pathless wrappers, locale prefixes, bare
 * `:id` routes — add their URL segments to the links of the crumbs below them but no crumb of their own.
 *
 * It stops at the route that owns an entity (`data.entityName`): the entity status bar already draws every
 * level from there on, so descending further would show them twice.
 */
export function readBreadcrumbTrail(root: ActivatedRouteSnapshot): BreadcrumbItem[] {
  const trail: BreadcrumbItem[] = [];
  const segments: string[] = [];
  for (let route: ActivatedRouteSnapshot | null = root; route; route = route.firstChild) {
    segments.push(...route.url.map((segment) => segment.path));
    const label = labelOf(route);
    if (label) trail.push({ label, url: '/' + segments.join('/') });
    if (ownData(route, 'entityName')) break;
  }
  return trail;
}

function labelOf(route: ActivatedRouteSnapshot): string | undefined {
  const breadcrumb = ownData(route, 'breadcrumb');
  const translocoId = ownData(route, 'translocoId');
  if (breadcrumb === false) return undefined;
  for (const candidate of [breadcrumb, translocoId, route.routeConfig?.title]) {
    if (typeof candidate === 'string' && candidate.length > 0) return candidate;
  }
  return undefined;
}

/**
 * A `data` value the route declares itself, or resolves. The router also copies a componentless parent's data
 * into its children, which would make every child repeat its parent's crumb.
 */
function ownData(route: ActivatedRouteSnapshot, key: string): unknown {
  return route.routeConfig?.data?.[key] ?? (route.routeConfig?.resolve?.[key] ? route.data[key] : undefined);
}
