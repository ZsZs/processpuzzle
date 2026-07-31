package com.processpuzzle.app.usecase;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppPage;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Organization;
import com.processpuzzle.app.domain.OrganizationRepository;
import com.processpuzzle.app.domain.OrganizationStatus;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.AppNotPublishedException;
import com.processpuzzle.app.usecase.exception.OrganizationAccessDeniedException;
import com.processpuzzle.app.usecase.exception.PageDefinitionNotFoundException;
import com.processpuzzle.app.usecase.port.OrganizationAccessPolicy;
import com.processpuzzle.app.usecase.port.PermitAllOrganizationAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetAppLayoutTest {

    private final AppDefinitionRepository repository = mock(AppDefinitionRepository.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);

    @Test
    void publishedRequestBeforeAnyPublish_is404() {
        GetAppLayout getAppLayout = layoutUseCase(new PermitAllOrganizationAccessPolicy());
        stored(graphWithRoles());

        assertThatThrownBy(() -> getAppLayout.execute("my-org", "claims-app", false))
                .isInstanceOf(AppNotPublishedException.class);
    }

    @Test
    void draftRequestBeforeAnyPublish_servesTheDraft() {
        GetAppLayout getAppLayout = layoutUseCase(new PermitAllOrganizationAccessPolicy());
        stored(graphWithRoles());

        GetAppLayout.Result result = getAppLayout.execute("my-org", "claims-app", true);

        assertThat(result.graph().regions()).hasSize(1);
        assertThat(result.defaultLocale()).isEqualTo("en-GB");
    }

    @Test
    void unknownDefinition_is404() {
        GetAppLayout getAppLayout = layoutUseCase(new PermitAllOrganizationAccessPolicy());
        when(repository.findByOrgKeyAndId("my-org", "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getAppLayout.execute("my-org", "nope", true))
                .isInstanceOf(AppDefinitionNotFoundException.class);
    }

    @Test
    void permissiveDefault_filtersNothingEvenWhenRolesAreConfigured() {
        GetAppLayout getAppLayout = layoutUseCase(new PermitAllOrganizationAccessPolicy());
        stored(graphWithRoles());

        GetAppLayout.Result result = getAppLayout.execute("my-org", "claims-app", true);

        List<NavNode> navItems = result.graph().regions().getFirst().navItems();
        assertThat(navItems).extracting(NavNode::id)
                .containsExactly("nav-open", "nav-restricted", "nav-group");
        assertThat(navItems.getLast().children()).hasSize(1);
    }

    @Test
    void restrictedEntriesAreDroppedForAPrincipalWithoutTheRole() {
        GetAppLayout getAppLayout = layoutUseCase(rolePolicy(Set.of("VIEWER")));
        stored(graphWithRoles());

        GetAppLayout.Result result = getAppLayout.execute("my-org", "claims-app", true);

        assertThat(result.graph().regions().getFirst().navItems())
                .extracting(NavNode::id)
                .containsExactly("nav-open");
    }

    @Test
    void groupWhoseChildrenAreAllHidden_isDroppedRatherThanRenderedEmpty() {
        GetAppLayout getAppLayout = layoutUseCase(rolePolicy(Set.of("CLAIMS_ADJUSTER")));
        stored(graphWithRoles());

        GetAppLayout.Result result = getAppLayout.execute("my-org", "claims-app", true);

        assertThat(result.graph().regions().getFirst().navItems())
                .extracting(NavNode::id)
                .containsExactly("nav-open", "nav-restricted");
    }

    @Test
    void deniedTenant_is403() {
        GetAppLayout getAppLayout = layoutUseCase(new OrganizationAccessPolicy() {
            @Override
            public void requireAccess(String orgKey) {
                throw new OrganizationAccessDeniedException(orgKey);
            }
        });

        assertThatThrownBy(() -> getAppLayout.execute("other-org", "claims-app", false))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    @Test
    void pageNotReachableByTheCallersRoles_is404NotForbidden() {
        GetPageDefinition getPageDefinition = pageUseCase(rolePolicy(Set.of("VIEWER")));
        stored(graphWithRoles());

        assertThatThrownBy(() -> getPageDefinition.execute("my-org", "claims-app", "page-restricted", true))
                .isInstanceOf(PageDefinitionNotFoundException.class);
    }

    @Test
    void reachablePage_isServed() {
        GetPageDefinition getPageDefinition = pageUseCase(rolePolicy(Set.of("VIEWER")));
        stored(graphWithRoles());

        AppPage page = getPageDefinition.execute("my-org", "claims-app", "page-open", true);

        assertThat(page.id()).isEqualTo("page-open");
    }

    // --- fixtures ------------------------------------------------------------------------

    private void stored(AppGraph graph) {
        AppDefinition definition = new AppDefinition("my-org", "claims-app", "Claims", null, null, graph);
        when(repository.findByOrgKeyAndId("my-org", "claims-app")).thenReturn(Optional.of(definition));
        when(organizationRepository.findById("my-org")).thenReturn(Optional.of(new Organization(
                "my-org", "My Org", null, null, "en-GB", OrganizationStatus.ACTIVE)));
    }

    private GetAppLayout layoutUseCase(OrganizationAccessPolicy policy) {
        return new GetAppLayout(repository, organizationRepository, guard(policy));
    }

    private GetPageDefinition pageUseCase(OrganizationAccessPolicy policy) {
        return new GetPageDefinition(repository, guard(policy));
    }

    @SuppressWarnings("unchecked")
    private static OrganizationGuard guard(OrganizationAccessPolicy policy) {
        ObjectProvider<OrganizationAccessPolicy> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(policy);
        return new OrganizationGuard(provider);
    }

    private static OrganizationAccessPolicy rolePolicy(Set<String> granted) {
        return new OrganizationAccessPolicy() {
            @Override
            public boolean hasAnyRole(Collection<String> requiredRoles) {
                return requiredRoles.stream().anyMatch(granted::contains);
            }
        };
    }

    /**
     * An open entry, a role-restricted entry, and a group whose only child is role-restricted with a
     * different role — so the three filtering behaviours are distinguishable.
     */
    private static AppGraph graphWithRoles() {
        NavNode open = new NavNode("nav-open", "Open", null, null, "page-open", List.of(), List.of());
        NavNode restricted = new NavNode("nav-restricted", "Restricted", null, null, "page-restricted",
                List.of("CLAIMS_ADJUSTER"), List.of());
        NavNode groupChild = new NavNode("nav-group-child", "Child", null, null, "page-open",
                List.of("AUDITOR"), List.of());
        NavNode group = new NavNode("nav-group", "Group", null, null, null, List.of(), List.of(groupChild));

        return new AppGraph(null, null,
                List.of(new Region("sidenav", List.of(open, restricted, group), List.of())),
                List.of(new AppPage("page-open", "Open", null, List.of()),
                        new AppPage("page-restricted", "Restricted", null, List.of())));
    }
}
