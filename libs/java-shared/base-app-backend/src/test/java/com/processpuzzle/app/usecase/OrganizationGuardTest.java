package com.processpuzzle.app.usecase;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.usecase.exception.OrganizationAccessDeniedException;
import com.processpuzzle.app.usecase.port.OrganizationAccessPolicy;
import com.processpuzzle.app.usecase.port.PermitAllOrganizationAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Nav filtering happens server-side, so this is where a nav entry a user may not see is prevented
 * from ever reaching the browser. Two decisions carry the weight: an entry with no roles is visible
 * to any member (the alternative would deny access precisely in the apps someone had configured),
 * and a group whose children are all filtered away is dropped rather than rendered empty — an empty
 * expandable group leaks that something exists behind it.
 */
class OrganizationGuardTest {

    private static final NavNode PUBLIC_ITEM =
            new NavNode("nav-claims", "Claims", null, null, "page-claims", List.of(), List.of());
    private static final NavNode RESTRICTED_ITEM =
            new NavNode("nav-audit", "Audit", null, null, "page-audit", List.of("CLAIMS_AUDITOR"), List.of());

    @Test
    void anEntryWithoutRoles_isVisibleToAnyMember() {
        OrganizationGuard guard = withRoles(false);

        assertThat(guard.isVisible(null)).isTrue();
        assertThat(guard.isVisible(List.of())).isTrue();
    }

    @Test
    void anEntryWithRoles_isVisibleOnlyWhenThePolicyAgrees() {
        assertThat(withRoles(true).isVisible(List.of("CLAIMS_AUDITOR"))).isTrue();
        assertThat(withRoles(false).isVisible(List.of("CLAIMS_AUDITOR"))).isFalse();
    }

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
     * A childless entry with no pageId is malformed rather than emptied, so it is not the guard's job
     * to drop it — {@code AppDefinitionValidator} reports it as a dead nav item instead.
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

        assertThat(withRoles(true).isPageReachable(regions, "page-claims")).isTrue();
        assertThat(withRoles(true).isPageReachable(regions, "page-audit")).isTrue();
        assertThat(withRoles(false).isPageReachable(regions, "page-audit")).isFalse();
        assertThat(withRoles(true).isPageReachable(regions, "page-unknown")).isFalse();
    }

    @Test
    void aPageBehindANestedVisibleEntry_isReachable() {
        NavNode group = new NavNode("nav-group", "Claims", null, null, null, List.of(),
                List.of(PUBLIC_ITEM));

        assertThat(withRoles(true).isPageReachable(List.of(sidenav(group)), "page-claims")).isTrue();
    }

    @Test
    void nothingIsReachableWithoutRegionsOrWithoutAPageId() {
        OrganizationGuard guard = withRoles(true);

        assertThat(guard.isPageReachable(null, "page-claims")).isFalse();
        assertThat(guard.isPageReachable(List.of(sidenav(PUBLIC_ITEM)), null)).isFalse();
    }

    @Test
    void accessAndDesignChecksAreDelegatedToThePolicy() {
        assertThatCode(() -> {
            OrganizationGuard permitted = AppTestFixtures.permissiveGuard();
            permitted.requireAccess("my-org");
            permitted.requireDesign("my-org");
        }).doesNotThrowAnyException();

        OrganizationGuard denied = AppTestFixtures.denyingGuard();
        assertThatThrownBy(() -> denied.requireAccess("my-org"))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> denied.requireDesign("my-org"))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    /**
     * {@code getIfUnique} rather than {@code @ConditionalOnMissingBean}: with no application policy
     * bean the guard has to fall back to permit-all, order-independently.
     */
    @Test
    @SuppressWarnings("unchecked")
    void withNoPolicyBean_theGuardFallsBackToPermitAll() {
        ObjectProvider<OrganizationAccessPolicy> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenAnswer(call ->
                ((java.util.function.Supplier<OrganizationAccessPolicy>) call.getArgument(0)).get());

        OrganizationGuard guard = new OrganizationGuard(provider);

        assertThatCode(() -> guard.requireDesign("my-org")).doesNotThrowAnyException();
        assertThat(guard.isVisible(List.of("ANY_ROLE"))).isTrue();
        assertThat(new PermitAllOrganizationAccessPolicy().hasAnyRole(List.of("ANY_ROLE"))).isTrue();
    }

    private static OrganizationGuard withRoles(boolean granted) {
        return AppTestFixtures.guardWith(new OrganizationAccessPolicy() {
            @Override
            public boolean hasAnyRole(Collection<String> requiredRoles) {
                return granted;
            }
        });
    }

    private static Region sidenav(NavNode... navItems) {
        return new Region("sidenav", List.of(navItems), List.of());
    }
}
