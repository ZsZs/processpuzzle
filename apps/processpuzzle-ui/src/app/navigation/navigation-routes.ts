import { Route, Router } from '@angular/router';

/** A navigation entry: an absolute link, plus the icon and label key the route declared. */
export interface NavigationItem {
  readonly link: string;
  readonly icon: string;
  readonly menuTitle: string;
}

/**
 * The titled routes of the router's own configuration, as navigation items.
 *
 * Read from `Router.config` rather than from an `appRoutes` constant, because this application has
 * no such constant: `createAppRoutes(orgKey)` builds its routes around the tenant the URL named, so
 * the only place the real set exists is the router. A spec that provides
 * `provideRouter(createAppRoutes('acme'))` therefore exercises the same wiring the browser does.
 *
 * Links are made absolute. A bare `routerLink="acme/admin"` is resolved relative to the *current*
 * route, so it works from `/home` and silently produces `/acme/admin/organization-user/acme/admin`
 * from anywhere inside the admin branch.
 */
export function navigationItems(router: Router): NavigationItem[] {
  return router.config.filter(isTitledRoute).map((route) => ({
    link: `/${route.path ?? ''}`,
    icon: (route.data?.['icon'] as string) ?? '',
    menuTitle: (route.data?.['menuTitle'] as string) ?? '',
  }));
}

/** The auth matcher route and the `''` redirect carry no title, and so never appear in navigation. */
function isTitledRoute(route: Route): boolean {
  return route.title !== null && route.title !== undefined;
}
