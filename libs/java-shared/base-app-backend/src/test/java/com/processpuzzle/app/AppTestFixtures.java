package com.processpuzzle.app;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.domain.Widget;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.ModuleDefinitionInput;
import com.processpuzzle.app.model.NavItem;
import com.processpuzzle.app.model.RouteDefinition;
import com.processpuzzle.app.model.RouteTarget;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.shared.model.WidgetInstance;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import com.processpuzzle.core.tenancy.OrganizationAccessDeniedException;
import com.processpuzzle.app.usecase.port.EntityNameRegistry;
import com.processpuzzle.core.tenancy.OrganizationAccessPolicy;
import com.processpuzzle.core.tenancy.PermitAllOrganizationAccessPolicy;
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
    public static final String ROUTE_PATH = "claims-list";
    public static final String NAV_ID = "nav-claims";
    public static final String MODULE_KEY = "claims-module";
    public static final String MODULE_ROUTE_PATH = "open";

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
     * A structurally valid input: one route, reached by one sidenav entry. Mutable throughout, so a
     * test can bend one part of it out of shape.
     */
    public static AppDefinitionInput validInput(String appId) {
        AppDefinitionInput input = new AppDefinitionInput(appId, "Claims Management");
        input.setRoutes(new ArrayList<>(List.of(routeDefinition(ROUTE_PATH, "Claims"))));

        NavItem nav = new NavItem(NAV_ID, "Claims");
        nav.setRoutePath(ROUTE_PATH);
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(new ArrayList<>(List.of(nav)));
        input.setRegions(new ArrayList<>(List.of(sidenav)));

        return input;
    }

    /** A model route rendering {@code widgets} in the content outlet. Mutable, like {@link #validInput}. */
    public static RouteDefinition routeDefinition(String path, String title, WidgetInstance... widgets) {
        return new RouteDefinition(path, title, widgetsTarget(widgets));
    }

    /**
     * The model {@code WIDGETS} target — the kind every route in these fixtures uses. Built over
     * {@link java.util.Arrays#asList} rather than {@code List.of} so a test can hand it a null entry
     * and check that the validator names its position instead of throwing.
     */
    public static RouteTarget widgetsTarget(WidgetInstance... widgets) {
        RouteTarget target = new RouteTarget(RouteTarget.KindEnum.WIDGETS);
        target.setWidgets(new ArrayList<>(java.util.Arrays.asList(widgets)));
        return target;
    }

    /** The domain equivalent of {@link #routeDefinition}. */
    public static AppRoute appRoute(String path, String title, Widget... widgets) {
        return new AppRoute(path, title, null, null, List.of(),
                com.processpuzzle.app.domain.RouteTarget.ofWidgets(List.of(widgets)));
    }

    /** The domain equivalent of {@link #validInput(String)}. */
    public static AppGraph validGraph() {
        NavNode nav = new NavNode(NAV_ID, "Claims", null, null, ROUTE_PATH, List.of(), List.of());
        return new AppGraph(null, null, List.of(new Region("sidenav", List.of(nav), List.of())),
                List.of(appRoute(ROUTE_PATH, "Claims")), List.of());
    }

    /** A structurally valid module: one route, and nothing an app has to know about. */
    public static ModuleDefinitionInput validModuleInput(String moduleKey) {
        ModuleDefinitionInput input = new ModuleDefinitionInput(moduleKey, "Claims");
        input.setRoutes(new ArrayList<>(List.of(routeDefinition(MODULE_ROUTE_PATH, "Open claims"))));
        return input;
    }

    /** The persisted counterpart of {@link #validModuleInput(String)}. */
    public static com.processpuzzle.app.domain.ModuleDefinition storedModule() {
        com.processpuzzle.app.domain.ModuleDefinition module =
                new com.processpuzzle.app.domain.ModuleDefinition(ORG_KEY, MODULE_KEY, "Claims");
        module.setRoutes(List.of(appRoute(MODULE_ROUTE_PATH, "Open claims")));
        return module;
    }

    /** A persisted, never-published definition holding {@link #validGraph()} as its draft. */
    public static AppDefinition storedDefinition() {
        return new AppDefinition(ORG_KEY, APP_ID, "Claims Management", "claims.app.name", "Handles claims.",
                validGraph());
    }
}
