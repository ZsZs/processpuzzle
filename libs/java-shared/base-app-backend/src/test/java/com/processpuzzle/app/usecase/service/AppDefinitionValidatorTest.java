package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.NavItem;
import com.processpuzzle.app.model.PageDefinition;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.model.ThemeDefinition;
import com.processpuzzle.app.model.WidgetRef;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.port.EntityNameRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppDefinitionValidatorTest {

    private AppDefinitionValidator validator;
    private ObjectProvider<EntityNameRegistry> entityRegistryProvider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        entityRegistryProvider = mock(ObjectProvider.class);
        when(entityRegistryProvider.getIfAvailable()).thenReturn(null);
        validator = new AppDefinitionValidator(entityRegistryProvider);
    }

    @Test
    void wellFormedDefinition_hasNoProblems() {
        assertThat(validator.validate("my-org", validInput())).isEmpty();
    }

    @Test
    void freshlyProvisionedDefinition_isValid() {
        AppDefinitionInput input = new AppDefinitionInput("app", "My Org");
        input.setRegions(List.of(new RegionDefinition(RegionType.CONTENT)));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    @Test
    void nullInput_reportsMissingBody() {
        assertThat(errorIds(validator.validate("my-org", null)))
                .containsExactly("app.validation.missing-body");
    }

    @Test
    void missingIdAndName_areReported() {
        AppDefinitionInput input = new AppDefinitionInput("  ", null);

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.missing-id", "app.validation.missing-name");
    }

    @Test
    void navItemPointingAtUndeclaredPage_isReported() {
        AppDefinitionInput input = validInput();
        input.getRegions().getFirst().getNavItems().getFirst().setPageId("page-does-not-exist");

        List<AppValidationProblem> problems = validator.validate("my-org", input);

        assertThat(errorIds(problems)).contains("app.validation.unknown-page-reference");
        assertThat(problems.stream().map(AppValidationProblem::path))
                .contains("/regions/0/navItems/0/pageId");
    }

    @Test
    void pageNoNavItemReaches_isReportedAsOrphan() {
        AppDefinitionInput input = validInput();
        input.getPages().add(new PageDefinition("page-unreachable", "Nowhere", List.of()));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.orphan-page");
    }

    @Test
    void pageReachedOnlyThroughANestedNavItem_isNotAnOrphan() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims");
        input.setPages(List.of(new PageDefinition("page-deep", "Deep", List.of())));
        NavItem child = new NavItem("nav-child", "Child");
        child.setPageId("page-deep");
        NavItem group = new NavItem("nav-group", "Group");
        group.setChildren(List.of(child));
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(List.of(group));
        input.setRegions(List.of(sidenav));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    @Test
    void duplicatePageIds_areReported() {
        AppDefinitionInput input = validInput();
        input.getPages().add(new PageDefinition("page-claims-list", "Duplicate", List.of()));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.duplicate-page-id");
    }

    @Test
    void duplicateNavItemIds_areReportedAcrossNesting() {
        AppDefinitionInput input = validInput();
        NavItem duplicate = new NavItem("nav-claims", "Same id again");
        duplicate.setPageId("page-claims-list");
        input.getRegions().getFirst().getNavItems().add(duplicate);

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.duplicate-nav-item-id");
    }

    @Test
    void duplicateWidgetIdsWithinAPage_areReportedIncludingNestedChildren() {
        AppDefinitionInput input = validInput();
        WidgetRef nested = new WidgetRef("widget-grid", "entity-grid");
        WidgetRef container = new WidgetRef("widget-container", "tab-group");
        container.setChildren(List.of(nested));
        input.getPages().getFirst().setWidgets(List.of(new WidgetRef("widget-grid", "entity-grid"), container));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.duplicate-widget-id");
    }

    @Test
    void navItemWithNeitherPageNorChildren_isReportedAsDead() {
        AppDefinitionInput input = validInput();
        input.getRegions().getFirst().getNavItems().add(new NavItem("nav-dead", "Goes nowhere"));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.dead-nav-item");
    }

    @Test
    void twoRegionsOfTheSameType_areReported() {
        AppDefinitionInput input = validInput();
        input.getRegions().add(new RegionDefinition(RegionType.SIDENAV));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.duplicate-region");
    }

    @Test
    void navItemsOnANonSidenavRegion_areReported() {
        AppDefinitionInput input = validInput();
        RegionDefinition footer = new RegionDefinition(RegionType.FOOTER);
        NavItem stray = new NavItem("nav-stray", "Stray");
        stray.setPageId("page-claims-list");
        footer.setNavItems(List.of(stray));
        input.getRegions().add(footer);

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.nav-items-not-allowed");
    }

    @Test
    void widgetsOnAContentRegion_areReported() {
        AppDefinitionInput input = validInput();
        RegionDefinition content = new RegionDefinition(RegionType.CONTENT);
        content.setWidgets(List.of(new WidgetRef("widget-stray", "entity-grid")));
        input.getRegions().add(content);

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.widgets-not-allowed");
    }

    @Test
    void widgetsOnAHeaderRegion_areAllowed() {
        AppDefinitionInput input = validInput();
        RegionDefinition header = new RegionDefinition(RegionType.HEADER);
        header.setWidgets(List.of(new WidgetRef("widget-language", "language-selector")));
        input.getRegions().add(header);

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    @Test
    void unknownThemeToken_isReported() {
        AppDefinitionInput input = validInput();
        ThemeDefinition theme = new ThemeDefinition();
        theme.setTokenOverrides(Map.of("--pp-not-a-token", "#fff"));
        input.setTheme(theme);

        List<AppValidationProblem> problems = validator.validate("my-org", input);

        assertThat(errorIds(problems)).contains("app.validation.unknown-theme-token");
        assertThat(problems.stream().map(AppValidationProblem::path))
                .contains("/theme/tokenOverrides/--pp-not-a-token");
    }

    @Test
    void knownThemeTokens_areAccepted() {
        AppDefinitionInput input = validInput();
        ThemeDefinition theme = new ThemeDefinition();
        theme.setTokenOverrides(Map.of(
                "--pp-surface-sidenav", "#0d1b2a",
                "--pp-color-light-green", "#a8e6cf"));
        input.setTheme(theme);

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    @Test
    void unknownEntityName_isReportedWhenARegistryIsAvailable() {
        EntityNameRegistry registry = mock(EntityNameRegistry.class);
        when(registry.isKnownEntity(any(), any())).thenReturn(false);
        when(entityRegistryProvider.getIfAvailable()).thenReturn(registry);

        AppDefinitionInput input = validInput();
        WidgetRef grid = new WidgetRef("widget-grid", "entity-grid");
        grid.setProps(Map.of("entityName", "Claim"));
        input.getPages().getFirst().setWidgets(List.of(grid));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.unknown-entity-name");
    }

    @Test
    void entityNameIsNotCheckedWhenNoRegistryIsAvailable() {
        AppDefinitionInput input = validInput();
        WidgetRef grid = new WidgetRef("widget-grid", "entity-grid");
        grid.setProps(Map.of("entityName", "NoSuchEntity"));
        input.getPages().getFirst().setWidgets(List.of(grid));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    /** Mutable on purpose — every test above bends one part of it out of shape. */
    private static AppDefinitionInput validInput() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");

        PageDefinition page = new PageDefinition("page-claims-list", "Claims", new java.util.ArrayList<>());
        input.setPages(new java.util.ArrayList<>(List.of(page)));

        NavItem nav = new NavItem("nav-claims", "Claims");
        nav.setPageId("page-claims-list");
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(new java.util.ArrayList<>(List.of(nav)));
        input.setRegions(new java.util.ArrayList<>(List.of(sidenav)));

        return input;
    }

    private static List<String> errorIds(List<AppValidationProblem> problems) {
        return problems.stream().map(AppValidationProblem::errorId).toList();
    }
}
