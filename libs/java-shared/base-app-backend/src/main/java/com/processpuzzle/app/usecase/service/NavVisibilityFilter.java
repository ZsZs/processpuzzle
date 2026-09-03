package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.platformadmin.usecase.OrganizationGuard;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Drops the nav entries the current principal may not see, and answers whether a route is reachable
 * through the ones that remain.
 *
 * <p>Both methods were on {@code OrganizationGuard} until the {@code Organization} aggregate moved to
 * {@code platform-admin}. They could not go with it: they walk {@link Region} and {@link NavNode},
 * base-app's own domain, and a guard in {@code platformadmin} reaching back into {@code app} would be
 * a dependency cycle. The split turns out to be the right seam anyway — whether a principal belongs
 * to a tenant is a platform question, while which of that tenant's nav entries the principal may see
 * is an app-shell question that merely needs the platform's answer. So this class delegates the one
 * decision it does not own, {@link OrganizationGuard#isVisible}, and keeps the tree walking.
 */
@Component
public class NavVisibilityFilter {

    private final OrganizationGuard guard;

    public NavVisibilityFilter(OrganizationGuard guard) {
        this.guard = guard;
    }

    /**
     * Drops the nav entries the principal may not see, recursing into groups. Filtering happens
     * server side so a nav entry a user may not see never reaches the browser.
     *
     * <p>A group node (no {@code routePath}) whose children are all filtered away is dropped too:
     * rendering an empty expandable group would leak the fact that something exists behind it.
     */
    public List<Region> filterRegions(List<Region> regions) {
        if (regions == null) {
            return List.of();
        }
        List<Region> filtered = new ArrayList<>(regions.size());
        for (Region region : regions) {
            filtered.add(region.withNavItems(filterNavItems(region.navItems())));
        }
        return List.copyOf(filtered);
    }

    private List<NavNode> filterNavItems(List<NavNode> navItems) {
        List<NavNode> visible = new ArrayList<>(navItems.size());
        for (NavNode item : navItems) {
            if (guard.isVisible(item.roles())) {
                List<NavNode> children = filterNavItems(item.children());
                if (!isEmptiedGroup(item, children)) {
                    visible.add(item.withChildren(children));
                }
            }
        }
        return List.copyOf(visible);
    }

    private static boolean isEmptiedGroup(NavNode item, List<NavNode> visibleChildren) {
        return item.routePath() == null && !item.children().isEmpty() && visibleChildren.isEmpty();
    }

    /**
     * Whether any nav entry the principal can see reaches {@code routePath}. Guards the lazy route
     * fetch, so a route is not readable just because its id was guessed.
     */
    public boolean isRouteReachable(List<Region> regions, String routePath) {
        if (regions == null || routePath == null) {
            return false;
        }
        return regions.stream().anyMatch(region -> reaches(region.navItems(), routePath));
    }

    private boolean reaches(List<NavNode> navItems, String routePath) {
        for (NavNode item : navItems) {
            if (!guard.isVisible(item.roles())) {
                continue;
            }
            if (routePath.equals(item.routePath()) || reaches(item.children(), routePath)) {
                return true;
            }
        }
        return false;
    }
}
