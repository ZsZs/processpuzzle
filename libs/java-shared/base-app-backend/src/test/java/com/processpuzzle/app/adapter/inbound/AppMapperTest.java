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
import com.processpuzzle.app.model.OrganizationStatus;
import com.processpuzzle.app.model.PageDefinition;
import com.processpuzzle.app.model.PageOfAppDefinition;
import com.processpuzzle.app.model.ProvisioningResult;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.model.SidenavMode;
import com.processpuzzle.app.model.ThemeDefinition;
import com.processpuzzle.app.model.WidgetRef;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.ImportOutcome;
import com.processpuzzle.app.usecase.KeyCheckOutcome;
import com.processpuzzle.rule.domain.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
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
        assertThat(mapper.toModel(entity).getVersion()).isEqualTo(2L);
        assertThat(mapper.toModel(entity).getPublishedVersion()).isEqualTo(1L);
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

    /**
     * A JSON body may state {@code "regions": null} rather than omitting the field, and a hand-written
     * YAML file routinely does. Each null collection has to become an empty one, not propagate.
     */
    @Test
    void explicitlyNullCollectionsMapToEmptyOnes() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        NavItem nav = new NavItem("nav-claims", "Claims");
        nav.setPageId("page-claims-list");
        nav.setChildren(null);
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(null);
        sidenav.setWidgets(null);
        input.setRegions(null);
        input.setPages(null);

        AppGraph graph = mapper.toDomainGraph(input);

        assertThat(graph.regions()).isEmpty();
        assertThat(graph.pages()).isEmpty();

        sidenav.setNavItems(List.of(nav));
        input.setRegions(List.of(sidenav));
        input.setPages(List.of(new PageDefinition("page-claims-list", "Claims", null)));

        AppGraph populated = mapper.toDomainGraph(input);

        assertThat(populated.regions().getFirst().navItems().getFirst().children()).isEmpty();
        assertThat(populated.regions().getFirst().widgets()).isEmpty();
        assertThat(populated.pages().getFirst().widgets()).isEmpty();
    }

    /** The designer saves early and often, so most of the graph is absent most of the time. */
    @Test
    void anInputWithNothingConfiguredMapsToTheEmptyGraphRatherThanNulls() {
        assertThat(mapper.toDomainGraph(null)).isEqualTo(AppGraph.empty());

        AppGraph graph = mapper.toDomainGraph(new AppDefinitionInput("claims-app", "Claims Management"));

        assertThat(graph.theme()).isNull();
        assertThat(graph.layout()).isNull();
        assertThat(graph.regions()).isEmpty();
        assertThat(graph.pages()).isEmpty();
    }

    /**
     * The contract gives {@code materialTheme}, {@code colorScheme}, {@code preset} and
     * {@code sidenavMode} defaults, so a theme or layout the designer never touched arrives populated.
     */
    @Test
    void contractDefaultsReachTheGraphRatherThanBeingLostAsNulls() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setTheme(new ThemeDefinition());
        input.setLayout(new LayoutDefinition());

        AppGraph graph = mapper.toDomainGraph(input);

        assertThat(graph.theme().materialTheme()).isEqualTo("azure-blue");
        assertThat(graph.theme().colorScheme()).isEqualTo("light");
        assertThat(graph.layout().preset()).isEqualTo("sidenav-left");
        assertThat(graph.layout().sidenavMode()).isEqualTo("side");
    }

    /** Explicitly cleared enum fields must survive as nulls rather than throwing on {@code getValue}. */
    @Test
    void clearedEnumValuedFieldsSurviveAsNullsInsteadOfFailingTheMapping() {
        ThemeDefinition theme = new ThemeDefinition();
        theme.setMaterialTheme(null);
        theme.setColorScheme(null);
        theme.setLogoUrl("/logo.png");
        LayoutDefinition layout = new LayoutDefinition();
        layout.setPreset(null);
        layout.setSidenavMode(null);
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setTheme(theme);
        input.setLayout(layout);

        AppGraph graph = mapper.toDomainGraph(input);

        assertThat(graph.theme().materialTheme()).isNull();
        assertThat(graph.theme().colorScheme()).isNull();
        assertThat(graph.theme().logoUrl()).isEqualTo("/logo.png");
        assertThat(graph.layout().preset()).isNull();
        assertThat(graph.layout().sidenavMode()).isNull();
        assertThat(graph.layout().sidenavCollapsible()).isTrue();
    }

    /**
     * Reading back an all-unset theme, layout and region must not throw either: {@code fromValue}
     * would reject {@code null}, so the mapper has to hand it straight back.
     */
    @Test
    void unsetEnumValuedFieldsSurviveTheReadBack() {
        AppGraph graph = new AppGraph(new Theme(null, null, Map.of(), null, null),
                new Layout(null, null, null, null, null),
                List.of(new Region(null, List.of(), List.of())), List.of());
        com.processpuzzle.app.domain.AppDefinition entity = new com.processpuzzle.app.domain.AppDefinition(
                "my-org", "claims-app", "Claims", null, null, graph);

        com.processpuzzle.app.model.AppDefinition model = mapper.toModel(entity);

        assertThat(model.getTheme().getMaterialTheme()).isNull();
        assertThat(model.getTheme().getColorScheme()).isNull();
        assertThat(model.getLayout().getPreset()).isNull();
        assertThat(model.getLayout().getSidenavMode()).isNull();
        assertThat(model.getRegions()).isEmpty();
        assertThat(model.getCreatedAt()).isNull();
        assertThat(model.getUpdatedAt()).isNull();
    }

    /**
     * A colour scheme or sidenav mode that a later release removed is dropped for the same reason a
     * stale theme name is: one retired value must not make the whole definition unreadable.
     */
    @Test
    void everyStaleEnumValueIsDroppedIndividually() {
        AppGraph graph = new AppGraph(new Theme("rose-red", "scheme-gone", Map.of(), null, null),
                new Layout("sidenav-left", "mode-gone", null, null, null),
                List.of(new Region("sidenav", List.of(), List.of())), List.of());
        com.processpuzzle.app.domain.AppDefinition entity = new com.processpuzzle.app.domain.AppDefinition(
                "my-org", "claims-app", "Claims", null, null, graph);

        com.processpuzzle.app.model.AppDefinition model = mapper.toModel(entity);

        assertThat(model.getTheme().getMaterialTheme()).isEqualTo(MaterialTheme.ROSE_RED);
        assertThat(model.getTheme().getColorScheme()).isNull();
        assertThat(model.getLayout().getPreset()).isEqualTo(LayoutPreset.SIDENAV_LEFT);
        assertThat(model.getLayout().getSidenavMode()).isNull();
        assertThat(model.getRegions()).extracting(RegionDefinition::getType).containsExactly(RegionType.SIDENAV);
    }

    /**
     * The input arrives from JSON or YAML, both of which can produce a {@code null} list entry. One
     * would otherwise become a {@code NullPointerException} deep in the validator.
     */
    @Test
    void nullEntriesAndUntypedRegionsAreDroppedRatherThanCarriedIntoTheGraph() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        NavItem nav = new NavItem("nav-claims", "Claims");
        nav.setPageId("page-claims-list");
        nav.setChildren(Arrays.asList(nav(), null));
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(Arrays.asList(nav, null));
        WidgetRef container = new WidgetRef("widget-tabs", "tab-group");
        container.setChildren(Arrays.asList(new WidgetRef("widget-grid", "entity-grid"), null));
        RegionDefinition header = new RegionDefinition(RegionType.HEADER);
        header.setWidgets(Arrays.asList(container, null));
        input.setRegions(Arrays.asList(sidenav, header, new RegionDefinition(), null));
        input.setPages(Arrays.asList(new PageDefinition("page-claims-list", "Claims", null), null));

        AppGraph graph = mapper.toDomainGraph(input);

        assertThat(graph.regions()).extracting(Region::type).containsExactly("sidenav", "header");
        assertThat(graph.regions().getFirst().navItems()).hasSize(1);
        assertThat(graph.regions().getFirst().navItems().getFirst().children()).hasSize(1);
        assertThat(graph.regions().getLast().widgets()).hasSize(1);
        assertThat(graph.regions().getLast().widgets().getFirst().children()).hasSize(1);
        assertThat(graph.pages()).extracting(AppPage::id).containsExactly("page-claims-list");
        assertThat(graph.pages().getFirst().widgets()).isEmpty();
    }

    /**
     * {@code toLayout} is handed the role-filtered projection, which is {@code null} for an app with
     * no published revision — the shell still needs a renderable answer rather than an exception.
     */
    @Test
    void aLayoutWithoutAGraphStillCarriesTheDefinitionsHeader() {
        com.processpuzzle.app.domain.AppDefinition entity = new com.processpuzzle.app.domain.AppDefinition(
                "my-org", "claims-app", "Claims", "claims.app.name", null, AppGraph.empty());

        AppLayout layout = mapper.toLayout(entity, null, "en");

        assertThat(layout.getId()).isEqualTo("claims-app");
        assertThat(layout.getName()).isEqualTo("Claims");
        assertThat(layout.getRegions()).isEmpty();
        assertThat(layout.getTheme()).isNull();
        assertThat(layout.getLayout()).isNull();
        assertThat(mapper.toModelRegions(null)).isEmpty();
    }

    @Test
    void anOrganizationMapsWithItsStatusAndDescriptiveFields() {
        com.processpuzzle.app.domain.Organization organization = new com.processpuzzle.app.domain.Organization(
                "my-org", "My Organization Ltd.", "Insurance.", "ops@my-org.example", "en-GB",
                com.processpuzzle.app.domain.OrganizationStatus.SUSPENDED);

        com.processpuzzle.app.model.Organization model = mapper.toModel(organization);

        assertThat(model.getKey()).isEqualTo("my-org");
        assertThat(model.getName()).isEqualTo("My Organization Ltd.");
        assertThat(model.getDescription()).isEqualTo("Insurance.");
        assertThat(model.getContactEmail()).isEqualTo("ops@my-org.example");
        assertThat(model.getDefaultLocale()).isEqualTo("en-GB");
        assertThat(model.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
        assertThat(model.getCreatedAt()).isNull();
    }

    @Test
    void provisioningAnswersTheTenantAndItsStarterAppTogether() {
        com.processpuzzle.app.domain.Organization organization = new com.processpuzzle.app.domain.Organization(
                "my-org", "My Organization Ltd.", null, null, null, null);
        com.processpuzzle.app.domain.AppDefinition starter = new com.processpuzzle.app.domain.AppDefinition(
                "my-org", "app", "My Organization Ltd.", null, null, AppGraph.empty());

        ProvisioningResult result = mapper.toModel(organization, starter);

        assertThat(result.getOrganization().getKey()).isEqualTo("my-org");
        assertThat(result.getOrganization().getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(result.getAppDefinition().getId()).isEqualTo("app");
    }

    @Test
    void aPagedListCarriesTheSpringPageMetadata() {
        com.processpuzzle.app.domain.AppDefinition entity = new com.processpuzzle.app.domain.AppDefinition(
                "my-org", "claims-app", "Claims", "claims.app.name", "Handles claims.", AppGraph.empty());

        PageOfAppDefinition page = mapper.toModel(
                new PageImpl<>(List.of(entity), PageRequest.of(1, 5), 8L));

        assertThat(page.getContent())
                .extracting(com.processpuzzle.app.model.AppDefinition::getId)
                .containsExactly("claims-app");
        assertThat(page.getContent().getFirst().getDescription()).isEqualTo("Handles claims.");
        // PageImpl caps the total at offset + content size, so the second page of five holding one
        // element reports six rather than the eight the query claimed.
        assertThat(page.getTotalElements()).isEqualTo(6L);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(5);
    }

    /**
     * A page entry is mapped by the same {@code toModel} the single-GET uses, so it carries the
     * whole graph. The designer edits out of the list rather than re-fetching by id; a projection
     * that dropped theme, layout, regions or pages would be written back as empty by the next
     * full-replacement PUT.
     */
    @Test
    void aPagedEntryCarriesTheWholeGraphRatherThanHeaderFieldsOnly() {
        AppGraph graph = new AppGraph(
                new Theme("rose-red", "dark", Map.of(), null, null),
                new Layout("top-nav", "over", null, null, "1280px"),
                List.of(new Region("sidenav", List.of(), List.of())),
                List.of(new AppPage("order-list", "Orders", null, List.of())));
        com.processpuzzle.app.domain.AppDefinition entity = new com.processpuzzle.app.domain.AppDefinition(
                "my-org", "claims-app", "Claims", null, null, graph);

        PageOfAppDefinition page = mapper.toModel(new PageImpl<>(List.of(entity)));

        assertThat(page.getContent()).singleElement().satisfies(definition -> {
            assertThat(definition.getTheme().getMaterialTheme()).isEqualTo(MaterialTheme.ROSE_RED);
            assertThat(definition.getLayout().getContentMaxWidth()).isEqualTo("1280px");
            assertThat(definition.getRegions()).extracting(RegionDefinition::getType)
                    .containsExactly(RegionType.SIDENAV);
            assertThat(definition.getPages()).extracting(PageDefinition::getId).containsExactly("order-list");
        });
    }

    /** Blocking is what {@code valid} means, so a rejected write with no problems still maps. */
    @Test
    void aProblemListThatIsAbsentBlocksNothing() {
        assertThat(AppValidationProblem.blocking(null)).isEmpty();
        assertThat(new AppValidationProblem("/", "app.x", "Advice.", Severity.INFO).blocksPersisting())
                .isFalse();
        assertThat(mapper.toModel(new ImportOutcome(0, 0, null)).getErrors()).isEmpty();
        assertThat(mapper.toModel(new KeyCheckOutcome("api", false, "organization.key.reserved", null))
                .getSuggestions()).isEmpty();
    }

    private static NavItem nav() {
        NavItem child = new NavItem("nav-child", "Child");
        child.setPageId("page-claims-list");
        return child;
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
