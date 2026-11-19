package com.processpuzzle.app.usecase.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppRoute;
import com.processpuzzle.app.domain.ModuleMount;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.domain.RouteTarget;
import com.processpuzzle.app.usecase.service.ModuleMountMerger.ModuleInfo;
import com.processpuzzle.core.definition.ModuleMountPort.Launcher;
import com.processpuzzle.core.definition.ModuleMountPort.MountHint;
import com.processpuzzle.core.definition.ModuleMountPort.Nav;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ModuleMountMergerTest {

    private static final Function<String, ModuleInfo> MODULES = key -> switch (key) {
        case "inv-stock" -> new ModuleInfo("Stock", "overview");
        case "inv-orders" -> new ModuleInfo("Orders", null);
        default -> null;
    };

    private static MountHint stock() {
        return new MountHint("inv-stock", "stock", new Nav("inventory", "nav.title"),
                new Launcher("module:launcher.svg", "launcher.title", "launcher.subtitle", List.of("launcher.content"), "overview"));
    }

    private static AppGraph shell() {
        return new AppGraph(null, null, List.of(
                new Region("header", List.of(), List.of()),
                new Region("sidenav", List.of(new NavNode("nav-home", "Home", null, "home", "home", List.of(), List.of())), List.of())),
                List.of(new AppRoute("home", "Home", null, null, List.of(), RouteTarget.ofWidgets(List.of()))), List.of());
    }

    private static List<NavNode> sidenav(AppGraph graph) {
        return graph.regions().stream().filter(region -> "sidenav".equals(region.type())).findFirst().orElseThrow().navItems();
    }

    @Test
    void mountsTheModuleWithItsLauncherAndAnEntryInTheSidenav() {
        ModuleMountMerger.Result result = ModuleMountMerger.mount(shell(), List.of(stock()), MODULES);

        assertThat(result.done()).containsExactly("inv-stock");
        assertThat(result.skipped()).isEmpty();
        assertThat(result.graph().modules()).singleElement().satisfies(mount -> {
            assertThat(mount.moduleKey()).isEqualTo("inv-stock");
            assertThat(mount.basePath()).isEqualTo("stock");
            assertThat(mount.launcher()).isEqualTo(new ModuleMount.Launcher("module:launcher.svg", "launcher.title", "launcher.subtitle",
                    List.of("launcher.content"), "overview"));
        });
        assertThat(sidenav(result.graph())).extracting(NavNode::id, NavNode::label, NavNode::icon, NavNode::routePath)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("nav-home", "Home", "home", "home"),
                        org.assertj.core.groups.Tuple.tuple("nav-inv-stock", "Stock", "inventory", "stock/overview"));
    }

    @Test
    void theEntryLeadsToTheModulesFirstRouteWhenTheHintNamesNone() {
        MountHint orders = new MountHint("inv-orders", "orders", new Nav("list", "nav.title"), null);
        MountHint stockWithoutLink = new MountHint("inv-stock", "stock", new Nav("inventory", "nav.title"),
                new Launcher(null, "launcher.title", null, null, null));

        AppGraph graph = ModuleMountMerger.mount(shell(), List.of(orders, stockWithoutLink), MODULES).graph();

        assertThat(sidenav(graph)).extracting(NavNode::routePath).containsExactly("home", "orders", "stock/overview");
    }

    @Test
    void nothingElseOfTheApplicationIsTouched() {
        AppGraph before = shell();

        AppGraph after = ModuleMountMerger.mount(before, List.of(stock()), MODULES).graph();

        assertThat(after.theme()).isEqualTo(before.theme());
        assertThat(after.routes()).isEqualTo(before.routes());
        assertThat(after.regions().getFirst()).isEqualTo(before.regions().getFirst());
        assertThat(sidenav(after).getFirst()).isEqualTo(sidenav(before).getFirst());
    }

    @Test
    void aHintThatCannotBeHonouredIsSkippedWithItsReasonAndTheRestIsMounted() {
        AppGraph withTaken = new AppGraph(null, null, shell().regions(), shell().routes(),
                List.of(new ModuleMount("inv-orders", "orders"), new ModuleMount("other", "taken")));
        List<MountHint> hints = List.of(
                new MountHint("inv-stock", "taken", null, null),     // path taken by another mount
                new MountHint("inv-orders", "orders-2", null, null), // mounted already
                new MountHint("missing", "missing", null, null),     // no such module
                new MountHint("inv-stock", "home", null, null),      // path taken by an app route
                stock());

        ModuleMountMerger.Result result = ModuleMountMerger.mount(withTaken, hints, MODULES);

        assertThat(result.done()).containsExactly("inv-stock");
        assertThat(result.skipped()).containsEntry("inv-orders", "the module is mounted already")
                .containsEntry("missing", "the module does not exist");
        assertThat(result.graph().modules()).extracting(ModuleMount::moduleKey).containsExactly("inv-orders", "other", "inv-stock");
    }

    @Test
    void aTakenPathIsReportedAndNothingIsMounted() {
        AppGraph withTaken = new AppGraph(null, null, shell().regions(), shell().routes(), List.of(new ModuleMount("other", "stock")));

        ModuleMountMerger.Result result = ModuleMountMerger.mount(withTaken, List.of(stock()), MODULES);

        assertThat(result.done()).isEmpty();
        assertThat(result.skipped()).containsEntry("inv-stock", "the path 'stock' is taken");
        assertThat(result.graph()).isEqualTo(withTaken);
    }

    @Test
    void anApplicationWithoutASidenavGetsTheMountButNoRegion() {
        AppGraph noSidenav = new AppGraph(null, null, List.of(new Region("header", List.of(), List.of())), List.of(), List.of());

        AppGraph after = ModuleMountMerger.mount(noSidenav, List.of(stock()), MODULES).graph();

        assertThat(after.modules()).hasSize(1);
        assertThat(after.regions()).extracting(Region::type).containsExactly("header");
    }

    @Test
    void aHintWithoutNavMakesNoSidenavEntry() {
        AppGraph after = ModuleMountMerger.mount(shell(), List.of(new MountHint("inv-stock", "stock", null, null)), MODULES).graph();

        assertThat(sidenav(after)).extracting(NavNode::id).containsExactly("nav-home");
    }

    @Test
    void anEntryTheCustomerAlreadyHasUnderTheSameIdIsNotDuplicated() {
        AppGraph first = ModuleMountMerger.mount(shell(), List.of(stock()), MODULES).graph();
        AppGraph withoutMount = new AppGraph(null, null, first.regions(), first.routes(), List.of());

        AppGraph again = ModuleMountMerger.mount(withoutMount, List.of(stock()), MODULES).graph();

        assertThat(sidenav(again)).extracting(NavNode::id).containsExactly("nav-home", "nav-inv-stock");
    }

    @Test
    void unmountingRemovesTheMountAndItsEntityAndNothingElse() {
        AppGraph mounted = ModuleMountMerger.mount(shell(), List.of(stock()), MODULES).graph();
        AppGraph withOthers = new AppGraph(null, null, mounted.regions(), mounted.routes(),
                List.of(mounted.modules().getFirst(), new ModuleMount("other", "other")));

        ModuleMountMerger.Result result = ModuleMountMerger.unmount(withOthers, List.of("inv-stock", "never-mounted"));

        assertThat(result.done()).containsExactly("inv-stock");
        assertThat(result.skipped()).isEqualTo(Map.of("never-mounted", "the module is not mounted"));
        assertThat(result.graph().modules()).extracting(ModuleMount::moduleKey).containsExactly("other");
        assertThat(sidenav(result.graph())).extracting(NavNode::id).containsExactly("nav-home");
    }

    @Test
    void unmountingReachesAnEntryTheCustomerMovedIntoAGroup() {
        AppGraph mounted = ModuleMountMerger.mount(shell(), List.of(stock()), MODULES).graph();
        NavNode entry = sidenav(mounted).get(1);
        Region grouped = new Region("sidenav", List.of(new NavNode("group", "Apps", null, null, null, List.of(), List.of(entry))), List.of());
        AppGraph moved = new AppGraph(null, null, List.of(grouped), mounted.routes(), mounted.modules());

        AppGraph after = ModuleMountMerger.unmount(moved, List.of("inv-stock")).graph();

        assertThat(sidenav(after)).singleElement().satisfies(group -> assertThat(group.children()).isEmpty());
    }
}
