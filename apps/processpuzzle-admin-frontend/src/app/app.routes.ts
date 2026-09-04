import { inject } from '@angular/core';
import { Route } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE, authMatcher } from '@processpuzzle/auth';
import { PLATFORM_ADMIN_ROUTES } from '@processpuzzle/platform-admin';

/**
 * The whole application: a landing page, the four platform-admin screens, and the auth branch.
 *
 * `PLATFORM_ADMIN_ROUTES` is spread at the top level rather than nested under a prefix. The library
 * already fixes each path to `snakeCaseName(entityName)` — `organization`, `plan`, `subscription`,
 * `invoice` — because `BaseFormNavigatorSingletonStore` rebuilds the details URL from the entity
 * name, and it does so from the root. A prefix segment here would make every Name-column link and
 * every Edit navigation resolve one level too high, silently: the row would simply not open.
 *
 * The header and the sidenav both render `appRoutes.filter(route => route.title)`, which is why the
 * home route carries a `title` and the auth matcher route deliberately does not.
 */
export const appRoutes: Route[] = [
  {
    path: 'home',
    title: 'ProcessPuzzle Platform - Home',
    // Same shape as the testbed's home route: the resolver runs the provider's authentication flow
    // before the component renders, so a staff member landing on `/` is sent to Keycloak rather than
    // shown an empty page that only fails once a screen issues its first request.
    resolve: { auth: () => inject(AUTHENTICATION_SERVICE).authenticate() },
    data: { icon: 'home', menuTitle: 'home' },
    loadComponent: () => import('./home/home.component').then((component) => component.HomeComponent),
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'home',
  },
  ...PLATFORM_ADMIN_ROUTES,
  {
    // Matches any URL containing `auth`, which is how the library's callback and profile routes are
    // reached regardless of the provider's redirect shape.
    matcher: authMatcher,
    loadChildren: () => import('@processpuzzle/auth/feature').then((routes) => routes.authRoutes),
  },
];
