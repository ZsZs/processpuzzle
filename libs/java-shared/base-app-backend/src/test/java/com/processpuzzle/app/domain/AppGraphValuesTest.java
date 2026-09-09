package com.processpuzzle.app.domain;

import com.processpuzzle.app.domain.RouteTarget;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The graph records normalize {@code null} collections away in their compact constructors. That is
 * not tidiness: the records are deserialized from a JSON column where an absent field arrives as
 * {@code null}, and every caller — mapper, validator, role filter — walks the collections without a
 * null check.
 */
class AppGraphValuesTest {

    @Test
    void anAbsentRegionOrPageListReadsBackAsEmpty() {
        AppGraph graph = new AppGraph(null, null, null, null, List.of());

        assertThat(graph.regions()).isEmpty();
        assertThat(graph.routes()).isEmpty();
        assertThat(graph.theme()).isNull();
        assertThat(graph.layout()).isNull();
        assertThat(graph).isEqualTo(AppGraph.empty());
    }

    @Test
    void aFreshlyProvisionedGraphHasNothingConfigured() {
        assertThat(AppGraph.empty().regions()).isEmpty();
        assertThat(AppGraph.empty().routes()).isEmpty();
    }

    @Test
    void replacingRegionsKeepsEverythingElse() {
        AppGraph graph = new AppGraph(new Theme("rose-red", null, null, null, null),
                new Layout("sidenav-left", null, null, null, null), List.of(), List.of(route("route-1")), List.of());

        AppGraph filtered = graph.withRegions(List.of(new Region("sidenav", List.of(), List.of())));

        assertThat(filtered.theme()).isEqualTo(graph.theme());
        assertThat(filtered.layout()).isEqualTo(graph.layout());
        assertThat(filtered.routes()).isEqualTo(graph.routes());
        assertThat(filtered.regions()).hasSize(1);
    }

    @Test
    void findingAPageAnswersNullRatherThanThrowingWhenThereIsNoMatch() {
        AppGraph graph = new AppGraph(null, null, List.of(), List.of(route("route-1")), List.of());

        assertThat(graph.findRoute("route-1")).isNotNull();
        assertThat(graph.findRoute("route-nope")).isNull();
        assertThat(graph.findRoute(null)).isNull();
        assertThat(AppGraph.empty().findRoute("route-1")).isNull();
    }

    @Test
    void aRegionWithoutNavItemsOrWidgetsCarriesEmptyLists() {
        Region region = new Region("content", null, null);

        assertThat(region.navItems()).isEmpty();
        assertThat(region.widgets()).isEmpty();
    }

    @Test
    void replacingANavTreeKeepsTheRegionsTypeAndWidgets() {
        Region header = new Region("header", List.of(), List.of(widget("widget-logo")));

        Region filtered = header.withNavItems(List.of(navNode("nav-1")));

        assertThat(filtered.type()).isEqualTo("header");
        assertThat(filtered.widgets()).isEqualTo(header.widgets());
        assertThat(filtered.navItems()).extracting(NavNode::id).containsExactly("nav-1");
    }

    @Test
    void aNavNodeWithoutRolesOrChildrenCarriesEmptyLists() {
        NavNode node = new NavNode("nav-1", "Claims", null, null, "route-1", null, null);

        assertThat(node.roles()).isEmpty();
        assertThat(node.children()).isEmpty();
    }

    @Test
    void replacingChildrenKeepsEverythingThatIdentifiesTheEntry() {
        NavNode node = new NavNode("nav-1", "Claims", "claims.nav", "list_alt", "route-1",
                List.of("CLAIMS_ADJUSTER"), List.of(navNode("nav-child")));

        NavNode filtered = node.withChildren(List.of());

        assertThat(filtered.id()).isEqualTo("nav-1");
        assertThat(filtered.label()).isEqualTo("Claims");
        assertThat(filtered.translocoId()).isEqualTo("claims.nav");
        assertThat(filtered.icon()).isEqualTo("list_alt");
        assertThat(filtered.routePath()).isEqualTo("route-1");
        assertThat(filtered.roles()).containsExactly("CLAIMS_ADJUSTER");
        assertThat(filtered.children()).isEmpty();
    }

    @Test
    void aWidgetWithoutPropsOrPlacementRendersStandaloneWithNoConfiguration() {
        Widget widget = new Widget("widget-1", "entity-grid", null, null);

        assertThat(widget.props()).isEmpty();
        assertThat(widget.placement()).isEqualTo(WidgetPlacement.STANDALONE);
        assertThat(widget.isReferenced()).isFalse();
    }

    /** The flag the renderer and {@code AppDefinitionValidator} branch on, so it is worth pinning. */
    @Test
    void aReferencedWidgetReportsItself() {
        assertThat(new Widget("widget-1", "entity-grid", null, WidgetPlacement.REFERENCED).isReferenced()).isTrue();
    }

    /**
     * {@code props} is passed through to the frontend untouched, so the backend must neither reorder
     * it nor let a caller mutate it after the fact.
     */
    @Test
    void widgetPropsKeepTheirDeclarationOrderAndCannotBeMutatedThroughTheRecord() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("entityName", "Claim");
        props.put("pageSize", 20);
        Widget widget = new Widget("widget-1", "entity-grid", props, WidgetPlacement.STANDALONE);

        props.put("addedLater", true);

        assertThat(widget.props()).containsExactly(Map.entry("entityName", "Claim"),
                Map.entry("pageSize", 20));
        assertThatThrownBy(() -> widget.props().put("addedLater", true))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aThemeWithoutTokenOverridesCarriesAnEmptyMap() {
        Theme theme = new Theme("rose-red", "dark", null, null, null);

        assertThat(theme.tokenOverrides()).isEmpty();
        assertThat(theme.logoUrl()).isNull();
        assertThat(theme.faviconUrl()).isNull();
    }

    @Test
    void themeTokenOverridesAreDetachedFromTheMapTheyCameFrom() {
        Map<String, String> overrides = new HashMap<>(Map.of("--pp-surface-sidenav", "#0d1b2a"));
        Theme theme = new Theme(null, null, overrides, null, null);

        overrides.clear();

        assertThat(theme.tokenOverrides()).containsEntry("--pp-surface-sidenav", "#0d1b2a");
    }

    @Test
    void aPageWithoutWidgetsCarriesAnEmptyList() {
        assertThat(new AppRoute("route-1", "One", null, null, List.of(), RouteTarget.ofWidgets(null)).target().widgets()).isEmpty();
    }

    /** {@code Layout} holds only scalars, so it has nothing to normalize. */
    @Test
    void aLayoutCarriesItsScalarsVerbatim() {
        Layout layout = new Layout("sidenav-left", "side", true, false, "1280px");

        assertThat(layout.preset()).isEqualTo("sidenav-left");
        assertThat(layout.sidenavMode()).isEqualTo("side");
        assertThat(layout.sidenavCollapsible()).isTrue();
        assertThat(layout.sidenavOpenByDefault()).isFalse();
        assertThat(layout.contentMaxWidth()).isEqualTo("1280px");
        assertThat(new Layout(null, null, null, null, null).sidenavCollapsible()).isNull();
    }

    private static AppRoute route(String id) {
        return new AppRoute(id, "Page", null, null, List.of(), RouteTarget.ofWidgets(List.of()));
    }

    private static NavNode navNode(String id) {
        return new NavNode(id, "Entry", null, null, "route-1", List.of(), List.of());
    }

    private static Widget widget(String id) {
        return new Widget(id, "markdown", Map.of(), WidgetPlacement.STANDALONE);
    }
}
