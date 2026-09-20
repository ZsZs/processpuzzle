import { InjectionToken } from '@angular/core';

/**
 * Path the designer is mounted under, without a trailing slash — `''` when it sits at the root.
 *
 * <h2>Why this exists</h2>
 *
 * The design surfaces address themselves absolutely. `DesignRouteService` decides design mode with
 * `url.startsWith('/design')`, `DesignButtonComponent` links to `/design` and back to `/home`,
 * `DesignSidenavComponent` builds `'/design/' + item.path`, and `DesignContentComponent`'s cards
 * name `/design/entities` and its five siblings. Every one of those is correct for an application
 * whose designer is at the root, which was every application there was when they were written.
 *
 * `processpuzzle-custom-frontend` is not one. It is a single build serving every customer, with the
 * organization resolved at bootstrap from the first path segment, so its designer is at
 * `/{orgKey}/design`. Without this token all four pieces fail *silently* and differently: the
 * button navigates to a URL that matches nothing, the mode switch never engages so the design
 * sidenav never appears, and the Designer Home cards lead out of the tenant.
 *
 * <h2>Why a token and not a route parameter</h2>
 *
 * The consumers are a root-provided service and three components rendered outside the routed
 * outlet — none of them has an `ActivatedRoute` carrying the organization. The prefix is also
 * settled before the router exists at all, being read from the URL in `main.ts`, so a value is the
 * honest shape for it.
 *
 * <h2>Defaulting to `''` is what keeps this backward compatible</h2>
 *
 * An application that provides nothing gets exactly the previous behaviour — `/design`, `/home`,
 * `startsWith('/design')`. That is why `processpuzzle-testbed-frontend` needs no change.
 *
 * Provide it with a leading slash and no trailing one: `/acme`, never `acme` or `/acme/`.
 */
export const DESIGN_ROUTE_PREFIX = new InjectionToken<string>('DESIGN_ROUTE_PREFIX', {
  providedIn: 'root',
  factory: () => '',
});
