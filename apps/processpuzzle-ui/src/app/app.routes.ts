import { inject } from '@angular/core';
import { Route } from '@angular/router';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { AUTHENTICATION_SERVICE, authMatcher } from '@processpuzzle/auth';
import { ORG_ADMIN_ROUTES } from '@processpuzzle/org-admin';

/**
 * The application's routes, as a function of the tenant the URL named.
 *
 * A function rather than a constant because the org-admin branch is mounted under a **literal**
 * `<orgKey>/admin` path built at bootstrap, not under a `:orgKey` parameter. Two reasons:
 *
 * - `ORG_ADMIN_ORG_KEY` is injected when `OrganizationUserService` is constructed, and that service
 *   is `providedIn: 'root'` — so the key must be a value settled before the first route activates.
 *   The same is true of the Keycloak realm, substituted in `main.ts`.
 * - Because the path is literal, a URL naming a *different* tenant matches nothing and the browser
 *   reloads. That is the behaviour we want: switching tenants must re-resolve the realm, or org B's
 *   data would be fetched with a token minted for org A.
 *
 * Nesting `ORG_ADMIN_ROUTES` under a two-segment prefix is safe even though
 * `BaseFormNavigatorSingletonStore` rebuilds details URLs from the entity name: it prefers the
 * breadcrumb, whose `baseUrl` accumulates from the root down to the route that declares `entityName`
 * in its own `routeConfig.data` — which `ORG_ADMIN_ROUTES` does, on the segment-contributing route.
 * `apps/platform-admin` had to spread its library routes at the top level for exactly the reason
 * that does not apply here.
 *
 * The header and the sidenav both render `router.config.filter(route => route.title)`, which is why
 * the home route carries a `title` and the auth matcher and the redirects deliberately do not.
 */
export function createAppRoutes(orgKey?: string): Route[] {
  const orgAdminBranch: Route[] = orgKey
    ? [
        {
          path: `${orgKey}/admin`,
          title: 'ProcessPuzzle - Administration',
          data: { icon: 'group', menuTitle: 'admin.users' },
          children: ORG_ADMIN_ROUTES,
        },
        // `/<orgKey>` on its own is the tenant's front door; send it to the only surface that exists
        // for a tenant today.
        { path: orgKey, pathMatch: 'full', redirectTo: `${orgKey}/admin` },
      ]
    : [];

  return [
    {
      path: 'home',
      title: 'ProcessPuzzle - Home',
      // The resolver runs the provider's authentication flow before the component renders, so a user
      // landing on `/` is sent to Keycloak rather than shown an empty page that only fails once a
      // screen issues its first request.
      resolve: { auth: () => inject(AUTHENTICATION_SERVICE).authenticate() },
      data: { icon: 'home', menuTitle: 'home' },
      loadComponent: () => import('./home/home.component').then((component) => component.HomeComponent),
    },
    ...orgAdminBranch,
    // Only reachable for `/`, which by definition names no tenant, so this is always `home`.
    { path: '', pathMatch: 'full', redirectTo: 'home' },
    {
      // Matches any URL containing `auth`, which is how the library's callback and profile routes are
      // reached regardless of the provider's redirect shape.
      matcher: authMatcher,
      loadChildren: () => import('@processpuzzle/auth/feature').then((routes) => routes.authRoutes),
    },
  ];
}
