package com.processpuzzle.app;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppPage;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.NavItem;
import com.processpuzzle.app.model.PageDefinition;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.usecase.OrganizationGuard;
import com.processpuzzle.app.usecase.exception.OrganizationAccessDeniedException;
import com.processpuzzle.app.usecase.port.EntityNameRegistry;
import com.processpuzzle.app.usecase.port.OrganizationAccessPolicy;
import com.processpuzzle.app.usecase.port.PermitAllOrganizationAccessPolicy;
import com.processpuzzle.app.usecase.service.AppDefinitionValidator;
import com.processpuzzle.app.usecase.service.AppRuleValidator;
import com.processpuzzle.rule.usecase.EvaluateObject;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared wiring for the base-app unit tests. The collaborators that are cheap and worth exercising
 * for real — validator, guard, mapper — are built here rather than mocked, so a use-case test proves
 * the composition works and not merely that the calls happen.
 */
public final class AppTestFixtures {

    public static final String ORG_KEY = "my-org";
    public static final String APP_ID = "claims-app";
    public static final String PAGE_ID = "page-claims-list";
    public static final String NAV_ID = "nav-claims";

    private AppTestFixtures() {
    }

    /** The real structural validator, with no entity registry and no rule engine wired. */
    @SuppressWarnings("unchecked")
    public static AppDefinitionValidator structuralValidator() {
        ObjectProvider<EntityNameRegistry> entityRegistryProvider = mock(ObjectProvider.class);
        when(entityRegistryProvider.getIfAvailable()).thenReturn(null);
        ObjectProvider<EvaluateObject> evaluateObjectProvider = mock(ObjectProvider.class);
        when(evaluateObjectProvider.getIfAvailable()).thenReturn(null);
        return new AppDefinitionValidator(entityRegistryProvider, new AppRuleValidator(evaluateObjectProvider));
    }

    /** The guard a deployment without an access policy ends up with. */
    public static OrganizationGuard permissiveGuard() {
        return guardWith(new PermitAllOrganizationAccessPolicy());
    }

    /** A guard whose policy denies both membership and design rights. */
    public static OrganizationGuard denyingGuard() {
        return guardWith(new OrganizationAccessPolicy() {
            @Override
            public void requireAccess(String orgKey) {
                throw new OrganizationAccessDeniedException(orgKey);
            }

            @Override
            public void requireDesign(String orgKey) {
                throw new OrganizationAccessDeniedException(orgKey);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public static OrganizationGuard guardWith(OrganizationAccessPolicy policy) {
        ObjectProvider<OrganizationAccessPolicy> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique(any())).thenReturn(policy);
        return new OrganizationGuard(provider);
    }

    /**
     * A structurally valid input: one page, reached by one sidenav entry. Mutable throughout, so a
     * test can bend one part of it out of shape.
     */
    public static AppDefinitionInput validInput(String appId) {
        AppDefinitionInput input = new AppDefinitionInput(appId, "Claims Management");
        input.setPages(new ArrayList<>(List.of(new PageDefinition(PAGE_ID, "Claims", new ArrayList<>()))));

        NavItem nav = new NavItem(NAV_ID, "Claims");
        nav.setPageId(PAGE_ID);
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(new ArrayList<>(List.of(nav)));
        input.setRegions(new ArrayList<>(List.of(sidenav)));

        return input;
    }

    /** The domain equivalent of {@link #validInput(String)}. */
    public static AppGraph validGraph() {
        AppPage page = new AppPage(PAGE_ID, "Claims", null, List.of());
        NavNode nav = new NavNode(NAV_ID, "Claims", null, null, PAGE_ID, List.of(), List.of());
        return new AppGraph(null, null, List.of(new Region("sidenav", List.of(nav), List.of())), List.of(page));
    }

    /** A persisted, never-published definition holding {@link #validGraph()} as its draft. */
    public static AppDefinition storedDefinition() {
        return new AppDefinition(ORG_KEY, APP_ID, "Claims Management", "claims.app.name", "Handles claims.",
                validGraph());
    }
}
