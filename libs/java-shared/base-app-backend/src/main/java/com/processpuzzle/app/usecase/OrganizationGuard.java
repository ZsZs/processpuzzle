package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.usecase.port.OrganizationAccessPolicy;
import com.processpuzzle.app.usecase.port.PermitAllOrganizationAccessPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The single place the feature consults {@link OrganizationAccessPolicy}. Use cases depend on this
 * component, never on the port directly.
 *
 * <p>The policy is resolved with {@link ObjectProvider#getIfUnique}, not
 * {@code @ConditionalOnMissingBean}. That condition is only reliable inside auto-configuration,
 * which is processed after user beans are registered — and putting an {@code @AutoConfiguration}
 * under {@code com.processpuzzle} would not help either, because the application scans that
 * package with {@code scanBasePackages} and the component scan picks such a class up before the
 * auto-configuration import is applied (exactly what already happens to
 * {@code LoggingAspectAutoConfiguration}). {@code getIfUnique} is order-independent: it returns
 * the application's bean when there is exactly one, and falls back otherwise.
 */
@Component
public class OrganizationGuard {

    private final OrganizationAccessPolicy policy;

    public OrganizationGuard(ObjectProvider<OrganizationAccessPolicy> policyProvider) {
        this.policy = policyProvider.getIfUnique(PermitAllOrganizationAccessPolicy::new);
    }

    /** Rejects the call with 403 when the principal is not a member of {@code orgKey}. */
    public void requireAccess(String orgKey) {
        policy.requireAccess(orgKey);
    }

    /** Rejects the call with 403 when the principal may not author metadata for {@code orgKey}. */
    public void requireDesign(String orgKey) {
        policy.requireDesign(orgKey);
    }

    /** Whether the principal may see a nav entry restricted to {@code roles}. */
    public boolean isVisible(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return true;
        }
        return policy.hasAnyRole(roles);
    }

    /**
     * Drops the nav entries the principal may not see, recursing into groups. Filtering happens
     * here — server side — so a nav entry a user may not see never reaches the browser.
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
            if (isVisible(item.roles())) {
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
            if (!isVisible(item.roles())) {
                continue;
            }
            if (routePath.equals(item.routePath()) || reaches(item.children(), routePath)) {
                return true;
            }
        }
        return false;
    }
}
