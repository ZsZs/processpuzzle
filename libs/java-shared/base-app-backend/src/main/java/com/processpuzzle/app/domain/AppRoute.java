package com.processpuzzle.app.domain;

import java.util.List;

/**
 * One navigable route, as persisted inside {@link AppGraph}.
 *
 * <p><b>Flat: a route has no children.</b> {@code path} may be multi-segment, so {@code claims},
 * {@code claims/open} and {@code claims/:id} are three sibling entries rather than a three-level
 * tree. Angular's own nesting — a parent route hosting a {@code <router-outlet>} — is derived by
 * the frontend from these path prefixes at registration time, because nesting is a rendering
 * concern. Structure is broken up at the module boundary instead; see {@link ModuleMount}.
 *
 * <p>Named {@code AppRoute} rather than {@code Route} for the same reason {@link AppRoute}'s
 * predecessor was: to stay clear of the framework types the mapper also imports.
 *
 * @param path unique within the app (or within a module); may contain {@code /} and {@code :param}
 * @param title default title, in the organization's default language
 * @param translocoId translation key preferred over {@code title} by the frontend
 * @param icon icon name, used where the route is surfaced directly
 * @param roles roles allowed to reach this route; empty means any authenticated member
 * @param target what this route renders
 */
public record AppRoute(
        String path,
        String title,
        String translocoId,
        String icon,
        List<String> roles,
        RouteTarget target) {

    public AppRoute {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
