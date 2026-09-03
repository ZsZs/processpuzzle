package com.processpuzzle.app.usecase;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.domain.Widget;
import com.processpuzzle.app.domain.WidgetPlacement;
import com.processpuzzle.app.domain.RouteTarget;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.AppNotPublishedException;
import com.processpuzzle.app.usecase.service.NavVisibilityFilter;
import com.processpuzzle.platformadmin.usecase.OrganizationGuard;
import com.processpuzzle.platformadmin.usecase.exception.OrganizationAccessDeniedException;
import com.processpuzzle.app.usecase.exception.RouteDefinitionNotFoundException;
import com.processpuzzle.platformadmin.usecase.port.OrganizationAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.processpuzzle.app.AppTestFixtures.APP_ID;
import static com.processpuzzle.app.AppTestFixtures.ORG_KEY;
import static com.processpuzzle.app.AppTestFixtures.ROUTE_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The lazy route fetch is a second authorization surface, not just a read: a route id is guessable, so
 * the check that matters is reachability through a nav entry the caller can actually see. A route the
 * caller may not reach is reported as missing rather than forbidden, because 403 would confirm it
 * exists.
 */
class GetRouteDefinitionTest {

    private static final String HIDDEN_PAGE_ID = "route-audit";

    private AppDefinitionRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(AppDefinitionRepository.class);
    }

    @Test
    void servesTheDraftPageToTheDesigner() {
        given(AppTestFixtures.storedDefinition());

        AppRoute route = permissive().execute(ORG_KEY, APP_ID, ROUTE_PATH, true);

        assertThat(route.path()).isEqualTo(ROUTE_PATH);
        assertThat(route.title()).isEqualTo("Claims");
    }

    @Test
    void servesThePublishedPageToEndUsers() {
        AppDefinition definition = AppTestFixtures.storedDefinition();
        definition.publish();
        given(definition);

        assertThat(permissive().execute(ORG_KEY, APP_ID, ROUTE_PATH, false).path()).isEqualTo(ROUTE_PATH);
    }

    /**
     * The draft must not leak through the run-time route, so an app that has never been published has
     * no published route to serve — even though its draft holds one.
     */
    @Test
    void anAppThatWasNeverPublished_hasNoPublishedPage() {
        given(AppTestFixtures.storedDefinition());

        assertThatThrownBy(() -> permissive().execute(ORG_KEY, APP_ID, ROUTE_PATH, false))
                .isInstanceOf(AppNotPublishedException.class)
                .hasMessageContaining(ORG_KEY + "/" + APP_ID);
    }

    @Test
    void unknownAppDefinition_is404() {
        when(repository.findByOrgKeyAndId(ORG_KEY, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissive().execute(ORG_KEY, "nope", ROUTE_PATH, true))
                .isInstanceOf(AppDefinitionNotFoundException.class);
    }

    @Test
    void unknownPageId_is404() {
        given(AppTestFixtures.storedDefinition());

        assertThatThrownBy(() -> permissive().execute(ORG_KEY, APP_ID, "route-nope", true))
                .isInstanceOf(RouteDefinitionNotFoundException.class)
                .hasMessageContaining("route-nope");
    }

    @Test
    void aNullPageId_is404RatherThanAnEmptyMatch() {
        given(AppTestFixtures.storedDefinition());

        assertThatThrownBy(() -> permissive().execute(ORG_KEY, APP_ID, null, true))
                .isInstanceOf(RouteDefinitionNotFoundException.class);
    }

    @Test
    void aPageReachedOnlyThroughANestedNavItem_isStillServed() {
        given(new AppDefinition(ORG_KEY, APP_ID, "Claims Management", null, null, nestedGraph()));

        assertThat(permissive().execute(ORG_KEY, APP_ID, HIDDEN_PAGE_ID, true).path()).isEqualTo(HIDDEN_PAGE_ID);
    }

    /** Guessing the id of a route behind a role-restricted nav entry must not be enough to read it. */
    @Test
    void aPageOnlyReachableThroughANavItemThePrincipalCannotSee_isReportedAsMissing() {
        given(new AppDefinition(ORG_KEY, APP_ID, "Claims Management", null, null, nestedGraph()));
        GetRouteDefinition withoutTheRole = routeUseCase(
                AppTestFixtures.guardWith(new OrganizationAccessPolicy() {
                    @Override
                    public boolean hasAnyRole(Collection<String> requiredRoles) {
                        return false;
                    }
                }));

        assertThatThrownBy(() -> withoutTheRole.execute(ORG_KEY, APP_ID, HIDDEN_PAGE_ID, true))
                .isInstanceOf(RouteDefinitionNotFoundException.class);
    }

    @Test
    void theDraftRouteRequiresDesignRightsWhileTheRuntimeRouteRequiresMembership() {
        GetRouteDefinition denied = routeUseCase(AppTestFixtures.denyingGuard());

        assertThatThrownBy(() -> denied.execute(ORG_KEY, APP_ID, ROUTE_PATH, true))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> denied.execute(ORG_KEY, APP_ID, ROUTE_PATH, false))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    private GetRouteDefinition permissive() {
        return routeUseCase(AppTestFixtures.permissiveGuard());
    }

    /**
     * The reachability walk lives in {@link NavVisibilityFilter} now — it reads base-app's nav tree,
     * so it could not travel to platform-admin with the guard. Both are built from the same guard here
     * so that a policy handed in still governs both the 403 and the "reported as missing" path.
     */
    private GetRouteDefinition routeUseCase(OrganizationGuard guard) {
        return new GetRouteDefinition(repository, guard, new NavVisibilityFilter(guard));
    }

    private void given(AppDefinition definition) {
        when(repository.findByOrgKeyAndId(anyString(), anyString())).thenReturn(Optional.of(definition));
    }

    /** A group node whose only child navigates to a role-restricted route. */
    private static AppGraph nestedGraph() {
        AppRoute route = new AppRoute(HIDDEN_PAGE_ID, "Audit", null, null, List.of(), RouteTarget.ofWidgets(List.of(new Widget("widget-audit", "entity-grid", Map.of("entityName", "Claim"), WidgetPlacement.STANDALONE))));
        NavNode child = new NavNode("nav-audit", "Audit", null, null, HIDDEN_PAGE_ID,
                List.of("CLAIMS_AUDITOR"), List.of());
        NavNode group = new NavNode("nav-group", "Claims", null, null, null, List.of(), List.of(child));
        return new AppGraph(null, null, List.of(new Region("sidenav", List.of(group), List.of())), List.of(route), List.of());
    }
}
