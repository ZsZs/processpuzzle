package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.domain.AppDefinition;
import com.processpuzzle.app.domain.AppDefinitionRepository;
import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.ModuleDefinition;
import com.processpuzzle.app.domain.ModuleDefinitionRepository;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.RoleReference;
import com.processpuzzle.core.tenancy.RoleReferenceContributor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The roles an application's routes and navigation items, and its modules' routes, are restricted to,
 * so that the organization's role catalog can tell an administrator what still depends on a role: delete
 * it and the entry is visible to nobody.
 *
 * <p>Both the draft and the published graph are read, and both channels of a module, because the
 * running application keeps using what it last released after the designer has moved on. The same
 * entry on both is reported once.
 */
@Component
public class AppRoleReferences implements RoleReferenceContributor {

    static final String FEATURE = "app";
    static final String ROUTE = "route";
    static final String NAV_ITEM = "nav-item";
    static final String MODULE_ROUTE = "module-route";

    private final AppDefinitionRepository apps;
    private final ModuleDefinitionRepository modules;

    public AppRoleReferences(AppDefinitionRepository apps, ModuleDefinitionRepository modules) {
        this.apps = apps;
        this.modules = modules;
    }

    @Override
    public Map<String, List<RoleReference>> referencedRoles(String orgKey) {
        Map<String, Set<RoleReference>> referenced = new LinkedHashMap<>();
        for (AppDefinition app : apps.findByOrgKey(orgKey)) {
            collect(referenced, app.getDraftGraph(), app);
            collect(referenced, app.getPublishedGraph(), app);
        }
        for (Channel channel : Channel.values()) {
            for (ModuleDefinition module : modules.findByOrgKeyAndChannel(orgKey, channel)) {
                for (AppRoute route : routesOf(module)) {
                    route.roles().forEach(role -> add(referenced, role, MODULE_ROUTE,
                            module.getKey() + route.path(), module.getName() + ": " + route.title()));
                }
            }
        }
        return referenced.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue()), (left, right) -> left, LinkedHashMap::new));
    }

    private static List<AppRoute> routesOf(ModuleDefinition module) {
        return module.getRoutes() == null ? List.of() : module.getRoutes();
    }

    private static void collect(Map<String, Set<RoleReference>> referenced, AppGraph graph, AppDefinition app) {
        if (graph == null) {
            return;
        }
        for (AppRoute route : graph.routes()) {
            route.roles().forEach(role -> add(referenced, role, ROUTE, app.getId() + route.path(),
                    app.getName() + ": " + route.title()));
        }
        graph.regions().forEach(region -> region.navItems().forEach(node -> collect(referenced, node, app)));
    }

    private static void collect(Map<String, Set<RoleReference>> referenced, NavNode node, AppDefinition app) {
        node.roles().forEach(role -> add(referenced, role, NAV_ITEM, app.getId() + "/" + node.id(),
                app.getName() + ": " + node.label()));
        node.children().forEach(child -> collect(referenced, child, app));
    }

    private static void add(Map<String, Set<RoleReference>> referenced, String role, String kind, String id, String title) {
        if (role != null) {
            referenced.computeIfAbsent(role, name -> new LinkedHashSet<>()).add(new RoleReference(FEATURE, kind, id, title));
        }
    }
}
