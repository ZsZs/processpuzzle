package com.processpuzzle.app.domain;

import java.util.List;

/**
 * The metadata graph of an application — theme, layout, regions, routes and module mounts — as one
 * immutable value.
 *
 * <p>It is persisted as a single JSON document in one column (see {@code AppGraphConverter}), not
 * normalized into tables. That follows the precedent {@code RuleDefinition} set for its
 * {@code extendsRuleId} tree: keep graph shapes out of the relational model and validate
 * referential integrity in the service layer. Nothing queries inside the graph — the list endpoint
 * returns header-only summaries — so separate columns would buy nothing while costing a recursive
 * {@code @OneToMany} mapping with ordering columns.
 *
 * <p>Bundling the parts into one record also keeps the converter's target type concrete rather than
 * parameterized, which is what lets Hibernate resolve it at all.
 *
 * <p><b>Routes are flat.</b> {@link AppRoute} has no children; depth lives in its {@code path}
 * string, and decomposition happens through {@link #modules()}. A module's own routes are <em>not</em>
 * copied in here — they belong to its {@link ModuleDefinition} and are composed in at runtime, which
 * is what lets a module be loaded, authored and versioned independently of the app that mounts it.
 *
 * @param theme the tenant's look; {@code null} until the designer picks one
 * @param layout region arrangement; {@code null} means the frontend defaults apply
 * @param regions declared shell regions; empty on a freshly provisioned app
 * @param routes the app's own routes, flat; empty on a freshly provisioned app
 * @param modules modules this app mounts, and where
 */
public record AppGraph(
        Theme theme,
        Layout layout,
        List<Region> regions,
        List<AppRoute> routes,
        List<ModuleMount> modules) {

    public AppGraph {
        regions = regions == null ? List.of() : List.copyOf(regions);
        routes = routes == null ? List.of() : List.copyOf(routes);
        modules = modules == null ? List.of() : List.copyOf(modules);
    }

    /** The graph of a freshly provisioned application: no theme, layout, regions, routes or mounts. */
    public static AppGraph empty() {
        return new AppGraph(null, null, List.of(), List.of(), List.of());
    }

    /** Returns a copy of this graph with {@code regions} replaced — used by role filtering. */
    public AppGraph withRegions(List<Region> replacement) {
        return new AppGraph(theme, layout, replacement, routes, modules);
    }

    /**
     * Finds a route by its exact path, or {@code null} when this graph declares no such route.
     *
     * <p>Exact match, deliberately: a request for {@code claims} must not resolve {@code claims/open}
     * just because one is a prefix of the other. Prefix relationships matter only to the frontend's
     * route builder, which uses them to derive Angular's nesting.
     */
    public AppRoute findRoute(String path) {
        if (path == null) {
            return null;
        }
        return routes.stream().filter(route -> path.equals(route.path())).findFirst().orElse(null);
    }
}
