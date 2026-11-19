package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.ModuleMount;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.core.definition.ModuleMountPort.Launcher;
import com.processpuzzle.core.definition.ModuleMountPort.MountHint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Adds a domain starter's module mounts to an application graph, and takes them out again — the pure part of
 * {@code ModuleMountPort}, with no repository in it (business-starters design §11.3).
 *
 * <p>Only what the hint asks for is added: a {@link ModuleMount} carrying the launcher card, and a sidenav entry named
 * for the module. Theme, routes, other regions and every other mount are copied as they are, so a customer's design is
 * never rewritten. A hint that cannot be honoured is skipped with its reason, not refused: the starter's modules are
 * installed either way.
 */
public final class ModuleMountMerger {

    /** What the merger needs to know of a module: its name, and the path of its first route. */
    public record ModuleInfo(String name, String landingPath) {
    }

    public record Result(AppGraph graph, List<String> done, Map<String, String> skipped) {
    }

    private ModuleMountMerger() {
    }

    /** The id of the sidenav entry made for a mount; unmounting removes exactly that. */
    public static String navIdOf(String moduleKey) {
        return "nav-" + moduleKey;
    }

    /** The first route of a module as {@link ModuleInfo} wants it. */
    public static ModuleInfo infoOf(String name, List<AppRoute> routes) {
        return new ModuleInfo(name, routes == null || routes.isEmpty() ? null : routes.getFirst().path());
    }

    /** @param modules the module of a key as the draft holds it, or null when there is none */
    public static Result mount(AppGraph graph, List<MountHint> hints, Function<String, ModuleInfo> modules) {
        List<ModuleMount> mounts = new ArrayList<>(graph.modules());
        Set<String> mountedKeys = new LinkedHashSet<>();
        Set<String> takenPaths = new LinkedHashSet<>();
        mounts.forEach(mount -> {
            mountedKeys.add(mount.moduleKey());
            takenPaths.add(mount.basePath());
        });
        graph.routes().forEach(route -> takenPaths.add(route.path()));

        List<NavNode> navNodes = new ArrayList<>();
        List<String> done = new ArrayList<>();
        Map<String, String> skipped = new LinkedHashMap<>();
        for (MountHint hint : hints) {
            ModuleInfo module = modules.apply(hint.moduleKey());
            if (module == null) {
                skipped.put(hint.moduleKey(), "the module does not exist");
            } else if (mountedKeys.contains(hint.moduleKey())) {
                skipped.put(hint.moduleKey(), "the module is mounted already");
            } else if (takenPaths.contains(hint.basePath())) {
                skipped.put(hint.moduleKey(), "the path '" + hint.basePath() + "' is taken");
            } else {
                mounts.add(new ModuleMount(hint.moduleKey(), hint.basePath(), launcherOf(hint.launcher())));
                mountedKeys.add(hint.moduleKey());
                takenPaths.add(hint.basePath());
                if (hint.nav() != null) {
                    navNodes.add(new NavNode(navIdOf(hint.moduleKey()), module.name(), null, hint.nav().icon(),
                            routePathOf(hint, module), List.of(), List.of()));
                }
                done.add(hint.moduleKey());
                skipped.remove(hint.moduleKey());
            }
        }
        List<Region> regions = withNavNodes(graph.regions(), navNodes);
        return new Result(new AppGraph(graph.theme(), graph.layout(), regions, graph.routes(), mounts, graph.contexts()), done, skipped);
    }

    /** Removes the mounts of {@code moduleKeys} and their sidenav entries; a key that is not mounted is skipped. */
    public static Result unmount(AppGraph graph, Collection<String> moduleKeys) {
        Set<String> mounted = new LinkedHashSet<>();
        graph.modules().forEach(mount -> mounted.add(mount.moduleKey()));
        List<String> done = new ArrayList<>();
        Map<String, String> skipped = new LinkedHashMap<>();
        for (String key : moduleKeys) {
            if (mounted.contains(key)) {
                done.add(key);
            } else {
                skipped.put(key, "the module is not mounted");
            }
        }
        Set<String> navIds = new LinkedHashSet<>();
        done.forEach(key -> navIds.add(navIdOf(key)));
        List<ModuleMount> mounts = graph.modules().stream().filter(mount -> !done.contains(mount.moduleKey())).toList();
        List<Region> regions = graph.regions().stream()
                .map(region -> region.withNavItems(without(region.navItems(), navIds)))
                .toList();
        return new Result(new AppGraph(graph.theme(), graph.layout(), regions, graph.routes(), mounts, graph.contexts()), done, skipped);
    }

    private static ModuleMount.Launcher launcherOf(Launcher launcher) {
        return launcher == null ? null
                : new ModuleMount.Launcher(launcher.image(), launcher.titleKey(), launcher.subtitleKey(), launcher.contentKeys(), launcher.link());
    }

    /** The app path the entry leads to: the mount, then the hint's landing route or else the module's first one. */
    private static String routePathOf(MountHint hint, ModuleInfo module) {
        String landing = hint.launcher() != null && hint.launcher().link() != null ? hint.launcher().link() : module.landingPath();
        return landing == null || landing.isBlank() ? hint.basePath() : hint.basePath() + "/" + landing;
    }

    /** The sidenav gets the entries; an application without one — the customer removed it — gets none, and no region is made. */
    private static List<Region> withNavNodes(List<Region> regions, List<NavNode> navNodes) {
        if (navNodes.isEmpty()) {
            return regions;
        }
        return regions.stream().map(region -> {
            if (!"sidenav".equals(region.type())) {
                return region;
            }
            Set<String> present = new LinkedHashSet<>();
            collectIds(region.navItems(), present);
            List<NavNode> items = new ArrayList<>(region.navItems());
            navNodes.stream().filter(node -> !present.contains(node.id())).forEach(items::add);
            return region.withNavItems(items);
        }).toList();
    }

    private static void collectIds(List<NavNode> nodes, Set<String> ids) {
        for (NavNode node : nodes) {
            ids.add(node.id());
            collectIds(node.children(), ids);
        }
    }

    private static List<NavNode> without(List<NavNode> nodes, Set<String> ids) {
        return nodes.stream().filter(node -> !ids.contains(node.id()))
                .map(node -> node.withChildren(without(node.children(), ids)))
                .toList();
    }
}
