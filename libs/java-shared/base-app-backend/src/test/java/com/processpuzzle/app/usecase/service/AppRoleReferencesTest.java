package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.RoleReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppRoleReferencesTest {

    private static final String ORG = "acme";

    private final AppDefinitionRepository apps = mock(AppDefinitionRepository.class);
    private final ModuleDefinitionRepository modules = mock(ModuleDefinitionRepository.class);
    private final AppRoleReferences references = new AppRoleReferences(apps, modules);

    @Test
    void routesAndNavigationItemsAtAnyDepthReportTheirRoles() {
        NavNode child = new NavNode("child", "Audit", null, null, "/audit", List.of("auditor"), List.of());
        NavNode group = new NavNode("group", "Finance", null, null, null, List.of("finance"), List.of(child));
        AppGraph graph = new AppGraph(null, null,
                List.of(new Region("sidenav", List.of(group), List.of())),
                List.of(new AppRoute("/audit", "Audit", null, null, List.of("auditor"), null)),
                List.of());
        AppDefinition app = app("app-1", "Shop", graph, AppGraph.empty());
        when(apps.findByOrgKey(ORG)).thenReturn(List.of(app));
        when(modules.findByOrgKeyAndChannel(eq(ORG), any())).thenReturn(List.of());

        Map<String, List<RoleReference>> referenced = references.referencedRoles(ORG);

        assertThat(referenced).containsOnlyKeys("auditor", "finance");
        assertThat(referenced.get("auditor")).containsExactlyInAnyOrder(
                new RoleReference("app", "route", "app-1/audit", "Shop: Audit"),
                new RoleReference("app", "nav-item", "app-1/child", "Shop: Audit"));
        assertThat(referenced.get("finance")).containsExactly(
                new RoleReference("app", "nav-item", "app-1/group", "Shop: Finance"));
    }

    /** The running application keeps what it released, so the same entry on both graphs is one reference. */
    @Test
    void anEntryOnTheDraftAndThePublishedGraphIsReportedOnce() {
        AppGraph graph = new AppGraph(null, null, List.of(),
                List.of(new AppRoute("/audit", "Audit", null, null, List.of("auditor"), null)), List.of());
        AppDefinition app = app("app-1", "Shop", graph, graph);
        when(apps.findByOrgKey(ORG)).thenReturn(List.of(app));
        when(modules.findByOrgKeyAndChannel(eq(ORG), any())).thenReturn(List.of());

        assertThat(references.referencedRoles(ORG).get("auditor")).hasSize(1);
    }

    @Test
    void aModulesRoutesAreReportedFromBothChannels() {
        when(apps.findByOrgKey(ORG)).thenReturn(List.of());
        ModuleDefinition draft = module("sales", "Sales", new AppRoute("/orders", "Orders", null, null, List.of("clerk"), null));
        ModuleDefinition released = module("sales", "Sales", new AppRoute("/orders", "Orders", null, null, List.of("clerk"), null));
        when(modules.findByOrgKeyAndChannel(ORG, Channel.DRAFT)).thenReturn(List.of(draft));
        when(modules.findByOrgKeyAndChannel(ORG, Channel.RELEASED)).thenReturn(List.of(released));

        assertThat(references.referencedRoles(ORG)).containsOnlyKeys("clerk");
        assertThat(references.referencedRoles(ORG).get("clerk")).containsExactly(
                new RoleReference("app", "module-route", "sales/orders", "Sales: Orders"));
    }

    @Test
    void anApplicationWithoutAPublishedGraphIsTolerated() {
        AppDefinition app = app("app-1", "Shop", AppGraph.empty(), null);
        when(apps.findByOrgKey(ORG)).thenReturn(List.of(app));
        when(modules.findByOrgKeyAndChannel(eq(ORG), any())).thenReturn(List.of());

        assertThat(references.referencedRoles(ORG)).isEmpty();
    }

    private static AppDefinition app(String id, String name, AppGraph draft, AppGraph published) {
        AppDefinition app = mock(AppDefinition.class);
        when(app.getId()).thenReturn(id);
        when(app.getName()).thenReturn(name);
        when(app.getDraftGraph()).thenReturn(draft);
        when(app.getPublishedGraph()).thenReturn(published);
        return app;
    }

    private static ModuleDefinition module(String key, String name, AppRoute... routes) {
        ModuleDefinition module = mock(ModuleDefinition.class);
        when(module.getKey()).thenReturn(key);
        when(module.getName()).thenReturn(name);
        when(module.getRoutes()).thenReturn(List.of(routes));
        return module;
    }
}
