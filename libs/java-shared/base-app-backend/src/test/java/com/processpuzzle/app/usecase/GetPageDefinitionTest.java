package com.processpuzzle.app.usecase;

import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppPage;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.domain.Widget;
import com.processpuzzle.app.domain.WidgetPlacement;
import com.processpuzzle.app.usecase.exception.AppDefinitionNotFoundException;
import com.processpuzzle.app.usecase.exception.AppNotPublishedException;
import com.processpuzzle.app.usecase.exception.OrganizationAccessDeniedException;
import com.processpuzzle.app.usecase.exception.PageDefinitionNotFoundException;
import com.processpuzzle.app.usecase.port.OrganizationAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.processpuzzle.app.AppTestFixtures.APP_ID;
import static com.processpuzzle.app.AppTestFixtures.ORG_KEY;
import static com.processpuzzle.app.AppTestFixtures.PAGE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The lazy page fetch is a second authorization surface, not just a read: a page id is guessable, so
 * the check that matters is reachability through a nav entry the caller can actually see. A page the
 * caller may not reach is reported as missing rather than forbidden, because 403 would confirm it
 * exists.
 */
class GetPageDefinitionTest {

    private static final String HIDDEN_PAGE_ID = "page-audit";

    private AppDefinitionRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(AppDefinitionRepository.class);
    }

    @Test
    void servesTheDraftPageToTheDesigner() {
        given(AppTestFixtures.storedDefinition());

        AppPage page = permissive().execute(ORG_KEY, APP_ID, PAGE_ID, true);

        assertThat(page.id()).isEqualTo(PAGE_ID);
        assertThat(page.title()).isEqualTo("Claims");
    }

    @Test
    void servesThePublishedPageToEndUsers() {
        AppDefinition definition = AppTestFixtures.storedDefinition();
        definition.publish();
        given(definition);

        assertThat(permissive().execute(ORG_KEY, APP_ID, PAGE_ID, false).id()).isEqualTo(PAGE_ID);
    }

    /**
     * The draft must not leak through the run-time route, so an app that has never been published has
     * no published page to serve — even though its draft holds one.
     */
    @Test
    void anAppThatWasNeverPublished_hasNoPublishedPage() {
        given(AppTestFixtures.storedDefinition());

        assertThatThrownBy(() -> permissive().execute(ORG_KEY, APP_ID, PAGE_ID, false))
                .isInstanceOf(AppNotPublishedException.class)
                .hasMessageContaining(ORG_KEY + "/" + APP_ID);
    }

    @Test
    void unknownAppDefinition_is404() {
        when(repository.findByOrgKeyAndId(ORG_KEY, "nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissive().execute(ORG_KEY, "nope", PAGE_ID, true))
                .isInstanceOf(AppDefinitionNotFoundException.class);
    }

    @Test
    void unknownPageId_is404() {
        given(AppTestFixtures.storedDefinition());

        assertThatThrownBy(() -> permissive().execute(ORG_KEY, APP_ID, "page-nope", true))
                .isInstanceOf(PageDefinitionNotFoundException.class)
                .hasMessageContaining("page-nope");
    }

    @Test
    void aNullPageId_is404RatherThanAnEmptyMatch() {
        given(AppTestFixtures.storedDefinition());

        assertThatThrownBy(() -> permissive().execute(ORG_KEY, APP_ID, null, true))
                .isInstanceOf(PageDefinitionNotFoundException.class);
    }

    @Test
    void aPageReachedOnlyThroughANestedNavItem_isStillServed() {
        given(new AppDefinition(ORG_KEY, APP_ID, "Claims Management", null, null, nestedGraph()));

        assertThat(permissive().execute(ORG_KEY, APP_ID, HIDDEN_PAGE_ID, true).id()).isEqualTo(HIDDEN_PAGE_ID);
    }

    /** Guessing the id of a page behind a role-restricted nav entry must not be enough to read it. */
    @Test
    void aPageOnlyReachableThroughANavItemThePrincipalCannotSee_isReportedAsMissing() {
        given(new AppDefinition(ORG_KEY, APP_ID, "Claims Management", null, null, nestedGraph()));
        GetPageDefinition withoutTheRole = new GetPageDefinition(repository,
                AppTestFixtures.guardWith(new OrganizationAccessPolicy() {
                    @Override
                    public boolean hasAnyRole(Collection<String> requiredRoles) {
                        return false;
                    }
                }));

        assertThatThrownBy(() -> withoutTheRole.execute(ORG_KEY, APP_ID, HIDDEN_PAGE_ID, true))
                .isInstanceOf(PageDefinitionNotFoundException.class);
    }

    @Test
    void theDraftRouteRequiresDesignRightsWhileTheRuntimeRouteRequiresMembership() {
        GetPageDefinition denied = new GetPageDefinition(repository, AppTestFixtures.denyingGuard());

        assertThatThrownBy(() -> denied.execute(ORG_KEY, APP_ID, PAGE_ID, true))
                .isInstanceOf(OrganizationAccessDeniedException.class);
        assertThatThrownBy(() -> denied.execute(ORG_KEY, APP_ID, PAGE_ID, false))
                .isInstanceOf(OrganizationAccessDeniedException.class);
    }

    private GetPageDefinition permissive() {
        return new GetPageDefinition(repository, AppTestFixtures.permissiveGuard());
    }

    private void given(AppDefinition definition) {
        when(repository.findByOrgKeyAndId(anyString(), anyString())).thenReturn(Optional.of(definition));
    }

    /** A group node whose only child navigates to a role-restricted page. */
    private static AppGraph nestedGraph() {
        AppPage page = new AppPage(HIDDEN_PAGE_ID, "Audit", null,
                List.of(new Widget("widget-audit", "entity-grid", Map.of("entityName", "Claim"), WidgetPlacement.STANDALONE)));
        NavNode child = new NavNode("nav-audit", "Audit", null, null, HIDDEN_PAGE_ID,
                List.of("CLAIMS_AUDITOR"), List.of());
        NavNode group = new NavNode("nav-group", "Claims", null, null, null, List.of(), List.of(child));
        return new AppGraph(null, null, List.of(new Region("sidenav", List.of(group), List.of())), List.of(page));
    }
}
