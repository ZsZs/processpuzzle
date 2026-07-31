package com.processpuzzle.app.adapter.inbound;

import com.processpuzzle.app.domain.AppGraph;
import com.processpuzzle.app.domain.AppPage;
import com.processpuzzle.app.domain.Layout;
import com.processpuzzle.app.domain.NavNode;
import com.processpuzzle.app.domain.Region;
import com.processpuzzle.app.domain.Theme;
import com.processpuzzle.app.domain.Widget;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.AppDefinitionStatus;
import com.processpuzzle.app.model.AppLayout;
import com.processpuzzle.app.model.ColorScheme;
import com.processpuzzle.app.model.LayoutDefinition;
import com.processpuzzle.app.model.LayoutPreset;
import com.processpuzzle.app.model.MaterialTheme;
import com.processpuzzle.app.model.NavItem;
import com.processpuzzle.app.model.PageDefinition;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.model.SidenavMode;
import com.processpuzzle.app.model.ThemeDefinition;
import com.processpuzzle.app.model.WidgetRef;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.ImportOutcome;
import com.processpuzzle.app.usecase.KeyCheckOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppMapperTest {

    private final AppMapper mapper = new AppMapper();

    @Test
    void inputToDomainAndBack_preservesTheWholeGraph() {
        AppGraph graph = mapper.toDomainGraph(fullInput());

        assertThat(graph.theme().materialTheme()).isEqualTo("rose-red");
        assertThat(graph.theme().colorScheme()).isEqualTo("dark");
        assertThat(graph.theme().tokenOverrides()).containsEntry("--pp-surface-sidenav", "#0d1b2a");
        assertThat(graph.layout().preset()).isEqualTo("sidenav-right");
        assertThat(graph.layout().sidenavMode()).isEqualTo("over");

        NavNode group = graph.regions().getFirst().navItems().getFirst();
        assertThat(group.pageId()).isNull();
        assertThat(group.children()).extracting(NavNode::id).containsExactly("nav-child");
        assertThat(group.children().getFirst().roles()).containsExactly("CLAIMS_ADJUSTER");

        Widget container = graph.pages().getFirst().widgets().getFirst();
        assertThat(container.children().getFirst().props()).containsEntry("entityName", "Claim");

        com.processpuzzle.app.domain.AppDefinition entity = new com.processpuzzle.app.domain.AppDefinition(
                "my-org", "claims-app", "Claims", "claims.app.name", "desc", graph);
        com.processpuzzle.app.model.AppDefinition model = mapper.toModel(entity);

        assertThat(model.getTheme().getMaterialTheme()).isEqualTo(MaterialTheme.ROSE_RED);
        assertThat(model.getTheme().getColorScheme()).isEqualTo(ColorScheme.DARK);
        assertThat(model.getLayout().getPreset()).isEqualTo(LayoutPreset.SIDENAV_RIGHT);
        assertThat(model.getLayout().getSidenavMode()).isEqualTo(SidenavMode.OVER);
        assertThat(model.getRegions()).extracting(RegionDefinition::getType)
                .containsExactly(RegionType.SIDENAV);
        assertThat(model.getRegions().getFirst().getNavItems().getFirst().getChildren())
                .extracting(NavItem::getId).containsExactly("nav-child");
        assertThat(model.getPages()).extracting(PageDefinition::getId).containsExactly("page-claims-list");
        assertThat(model.getOrgKey()).isEqualTo("my-org");
    }

    @Test
    void statusIsDerivedFromTheRevisionCountersRatherThanStored() {
        com.processpuzzle.app.domain.AppDefinition entity = new com.processpuzzle.app.domain.AppDefinition(
                "my-org", "claims-app", "Claims", null, null, AppGraph.empty());

        assertThat(mapper.toModelStatus(entity)).isEqualTo(AppDefinitionStatus.DRAFT);
        assertThat(mapper.toModel(entity).getPublishedVersion()).isNull();

        entity.publish();
        assertThat(mapper.toModelStatus(entity)).isEqualTo(AppDefinitionStatus.PUBLISHED);
        assertThat(mapper.toModel(entity).getVersion())
                .isEqualTo(mapper.toModel(entity).getPublishedVersion());

        entity.replaceDraft("Claims", null, null, AppGraph.empty());
        assertThat(mapper.toModelStatus(entity)).isEqualTo(AppDefinitionStatus.DRAFT);
        assertThat(mapper.toSummary(entity).getVersion()).isEqualTo(2L);
        assertThat(mapper.toSummary(entity).getPublishedVersion()).isEqualTo(1L);
    }

    @Test
    void aStaleEnumValueInAPersistedBlobIsDroppedRatherThanFailingTheWholeRead() {
        AppGraph graph = new AppGraph(
                new Theme("theme-that-no-longer-exists", "light", Map.of(), null, null),
                new Layout("preset-gone", "side", null, null, null),
                List.of(new Region("region-type-gone", List.of(), List.of())),
                List.of());
        com.processpuzzle.app.domain.AppDefinition entity = new com.processpuzzle.app.domain.AppDefinition(
                "my-org", "claims-app", "Claims", null, null, graph);

        com.processpuzzle.app.model.AppDefinition model = mapper.toModel(entity);

        assertThat(model.getTheme().getMaterialTheme()).isNull();
        assertThat(model.getTheme().getColorScheme()).isEqualTo(ColorScheme.LIGHT);
        assertThat(model.getLayout().getPreset()).isNull();
        assertThat(model.getLayout().getSidenavMode()).isEqualTo(SidenavMode.SIDE);
        assertThat(model.getRegions()).isEmpty();
    }

    @Test
    void layoutCarriesTheFilteredGraphAndTheOrganizationsLocale() {
        AppGraph stored = mapper.toDomainGraph(fullInput());
        com.processpuzzle.app.domain.AppDefinition entity = new com.processpuzzle.app.domain.AppDefinition(
                "my-org", "claims-app", "Claims", "claims.app.name", null, stored);
        AppGraph filtered = stored.withRegions(List.of());

        AppLayout layout = mapper.toLayout(entity, filtered, "de-DE");

        assertThat(layout.getOrgKey()).isEqualTo("my-org");
        assertThat(layout.getTranslocoId()).isEqualTo("claims.app.name");
        assertThat(layout.getDefaultLocale()).isEqualTo("de-DE");
        assertThat(layout.getVersion()).isEqualTo(1L);
        assertThat(layout.getRegions()).isEmpty();
        assertThat(layout.getTheme().getMaterialTheme()).isEqualTo(MaterialTheme.ROSE_RED);
    }

    @Test
    void validationResultIsValidOnlyWhenThereAreNoProblems() {
        assertThat(mapper.toModel(List.<AppValidationProblem>of()).getValid()).isTrue();

        var result = mapper.toModel(List.of(new AppValidationProblem("/pages/0", "app.x", "Broken")));
        assertThat(result.getValid()).isFalse();
        assertThat(result.getProblems()).singleElement().satisfies(problem -> {
            assertThat(problem.getPath()).isEqualTo("/pages/0");
            assertThat(problem.getErrorId()).isEqualTo("app.x");
        });
    }

    @Test
    void outcomesMapOntoTheirContractShapes() {
        var importResult = mapper.toModel(new ImportOutcome(2, 1, List.of()));
        assertThat(importResult.getCreated()).isEqualTo(2);
        assertThat(importResult.getUpdated()).isEqualTo(1);

        var availability = mapper.toModel(
                KeyCheckOutcome.unavailable("api", "organization.key.reserved", List.of("api-app")));
        assertThat(availability.getAvailable()).isFalse();
        assertThat(availability.getErrorId()).isEqualTo("organization.key.reserved");
        assertThat(availability.getSuggestions()).containsExactly("api-app");
    }

    @Test
    void emptyGraphMapsToEmptyCollectionsNotNulls() {
        com.processpuzzle.app.domain.AppDefinition entity = new com.processpuzzle.app.domain.AppDefinition(
                "my-org", "app", "My Org", null, null, AppGraph.empty());

        com.processpuzzle.app.model.AppDefinition model = mapper.toModel(entity);

        assertThat(model.getRegions()).isEmpty();
        assertThat(model.getPages()).isEmpty();
        assertThat(model.getTheme()).isNull();
        assertThat(model.getLayout()).isNull();
    }

    @Test
    void pageMapsWithItsNestedWidgets() {
        AppPage page = new AppPage("page-1", "Page One", "page.one",
                List.of(new Widget("w-1", "entity-grid", Map.of("entityName", "Claim"), List.of())));

        PageDefinition model = mapper.toModel(page);

        assertThat(model.getId()).isEqualTo("page-1");
        assertThat(model.getTranslocoId()).isEqualTo("page.one");
        assertThat(model.getWidgets()).singleElement()
                .satisfies(widget -> assertThat(widget.getProps()).containsEntry("entityName", "Claim"));
    }

    private static AppDefinitionInput fullInput() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setTranslocoId("claims.app.name");

        ThemeDefinition theme = new ThemeDefinition();
        theme.setMaterialTheme(MaterialTheme.ROSE_RED);
        theme.setColorScheme(ColorScheme.DARK);
        theme.setTokenOverrides(Map.of("--pp-surface-sidenav", "#0d1b2a"));
        input.setTheme(theme);

        LayoutDefinition layout = new LayoutDefinition();
        layout.setPreset(LayoutPreset.SIDENAV_RIGHT);
        layout.setSidenavMode(SidenavMode.OVER);
        layout.setContentMaxWidth("1280px");
        input.setLayout(layout);

        WidgetRef grid = new WidgetRef("widget-grid", "entity-grid");
        grid.setProps(Map.of("entityName", "Claim"));
        WidgetRef container = new WidgetRef("widget-tabs", "tab-group");
        container.setChildren(List.of(grid));
        input.setPages(List.of(new PageDefinition("page-claims-list", "Claims", List.of(container))));

        NavItem child = new NavItem("nav-child", "Child");
        child.setPageId("page-claims-list");
        child.setRoles(List.of("CLAIMS_ADJUSTER"));
        NavItem group = new NavItem("nav-group", "Claims");
        group.setChildren(List.of(child));
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(List.of(group));
        input.setRegions(List.of(sidenav));

        return input;
    }
}
