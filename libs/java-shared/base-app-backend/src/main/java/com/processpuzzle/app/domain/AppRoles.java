package com.processpuzzle.app.domain;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Every role name an app definition or a module refers to — on its routes and on every navigation
 * item, at any depth — so the role catalog can make sure each exists. See
 * {@code OrganizationGuard#declareRoles}.
 */
public final class AppRoles {

    /** What the role catalog tells an administrator who finds such a role in the realm. */
    public static final String DESCRIPTION = "Referenced by application navigation and routes";

    private AppRoles() {
    }

    public static List<String> of(AppGraph graph) {
        Stream<String> onRoutes = graph.routes().stream().flatMap(route -> route.roles().stream());
        Stream<String> onNavigation = graph.regions().stream()
                .flatMap(region -> region.navItems().stream())
                .flatMap(AppRoles::within);
        return distinct(Stream.concat(onRoutes, onNavigation));
    }

    public static List<String> of(ModuleDefinition module) {
        List<AppRoute> routes = module.getRoutes() == null ? List.of() : module.getRoutes();
        return distinct(routes.stream().flatMap(route -> route.roles().stream()));
    }

    private static Stream<String> within(NavNode node) {
        return Stream.concat(node.roles().stream(), node.children().stream().flatMap(AppRoles::within));
    }

    private static List<String> distinct(Stream<String> roles) {
        return roles.filter(Objects::nonNull).distinct().toList();
    }
}
