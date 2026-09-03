package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.platformadmin.usecase.port.OrganizationAccessPolicy;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nav filtering happens server-side, so this is where a nav entry a user may not see is prevented
 * from ever reaching the browser. Two decisions carry the weight: an entry with no roles is visible
 * to any member (the alternative would deny access precisely in the apps someone had configured),
 * and a group whose children are all filtered away is dropped rather than rendered empty — an empty
 * expandable group leaks that something exists behind it.
 *
 * <p>These assertions were {@code OrganizationGuardTest}'s until the {@code Organization} aggregate
 * moved to platform-admin. They stayed because the tree they walk is base-app's; the guard's own
 * delegation assertions went with it.
 */
class NavVisibilityFilterTest {

    private static final NavNode PUBLIC_ITEM =
            new NavNode("nav-claims", "Claims", null, null, "route-claims", List.of(), List.of());
    private static final NavNode RESTRICTED_ITEM =
            new NavNode("nav-audit", "Audit", null, null, "route-audit", List.of("CLAIMS_AUDITOR"), List.of());

    @Test
    void filteringRegionsToleratesAGraphWithNoRegions() {
        assertThat(withRoles(true).filterRegions(null)).isEmpty();
        assertThat(withRoles(true).filterRegions(List.of())).isEmpty();
    }

    @Test
    void anEntryThePrincipalCannotSee_isDroppedFromTheRegion() {
        List<Region> filtered = withRoles(false)
                .filterRegions(List.of(sidenav(PUBLIC_ITEM, RESTRICTED_ITEM)));

        assertThat(filtered).singleElement().satisfies(region ->
                assertThat(region.navItems()).extracting(NavNode::id).containsExactly("nav-claims"));
    }

    @Test
    void aGroupWhoseChildrenAreAllFilteredAway_isDroppedRatherThanRenderedEmpty() {
        NavNode group = new NavNode("nav-group", "Claims", null, null, null, List.of(),
                List.of(RESTRICTED_ITEM));

        assertThat(withRoles(false).filterRegions(List.of(sidenav(group))))
                .singleElement().satisfies(region -> assertThat(region.navItems()).isEmpty());
    }

    @Test
    void aGroupThatKeepsAtLeastOneChild_survivesWithOnlyThatChild() {
        NavNode group = new NavNode("nav-group", "Claims", null, null, null, List.of(),
                List.of(PUBLIC_ITEM, RESTRICTED_ITEM));

        List<Region> filtered = withRoles(false).filterRegions(List.of(sidenav(group)));

        assertThat(filtered.getFirst().navItems()).singleElement().satisfies(node -> {
            assertThat(node.id()).isEqualTo("nav-group");
            assertThat(node.children()).extracting(NavNode::id).containsExactly("nav-claims");
        });
    }

    /**
     * A childless entry with no routePath is malformed rather than emptied, so it is not this filter's
     * job to drop it — {@code AppDefinitionValidator} reports it as a dead nav item instead.
     */
    @Test
    void aChildlessEntryWithoutAPage_isNotMistakenForAnEmptiedGroup() {
        NavNode dead = new NavNode("nav-dead", "Nowhere", null, null, null, List.of(), List.of());

        assertThat(withRoles(true).filterRegions(List.of(sidenav(dead))).getFirst().navItems())
                .extracting(NavNode::id).containsExactly("nav-dead");
    }

    @Test
    void aPageIsReachableWhenAVisibleEntryPointsAtIt() {
        List<Region> regions = List.of(sidenav(PUBLIC_ITEM, RESTRICTED_ITEM));

        assertThat(withRoles(true).isRouteReachable(regions, "route-claims")).isTrue();
        assertThat(withRoles(true).isRouteReachable(regions, "route-audit")).isTrue();
        assertThat(withRoles(false).isRouteReachable(regions, "route-audit")).isFalse();
        assertThat(withRoles(true).isRouteReachable(regions, "route-unknown")).isFalse();
    }

    @Test
    void aPageBehindANestedVisibleEntry_isReachable() {
        NavNode group = new NavNode("nav-group", "Claims", null, null, null, List.of(),
                List.of(PUBLIC_ITEM));

        assertThat(withRoles(true).isRouteReachable(List.of(sidenav(group)), "route-claims")).isTrue();
    }

    @Test
    void nothingIsReachableWithoutRegionsOrWithoutAPageId() {
        NavVisibilityFilter filter = withRoles(true);

        assertThat(filter.isRouteReachable(null, "route-claims")).isFalse();
        assertThat(filter.isRouteReachable(List.of(sidenav(PUBLIC_ITEM)), null)).isFalse();
    }

    private static NavVisibilityFilter withRoles(boolean granted) {
        return new NavVisibilityFilter(AppTestFixtures.guardWith(new OrganizationAccessPolicy() {
            @Override
            public boolean hasAnyRole(Collection<String> requiredRoles) {
                return granted;
            }
        }));
    }

    private static Region sidenav(NavNode... navItems) {
        return new Region("sidenav", List.of(navItems), List.of());
    }
}
