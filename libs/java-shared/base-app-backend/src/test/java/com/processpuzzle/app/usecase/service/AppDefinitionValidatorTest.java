package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.model.AppDefinition;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.NavItem;
import com.processpuzzle.app.model.PageDefinition;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.model.ThemeDefinition;
import com.processpuzzle.shared.model.WidgetInstance;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.port.EntityNameRegistry;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.EvaluationOutcome;
import com.processpuzzle.rule.usecase.RuleViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppDefinitionValidatorTest {

    private AppDefinitionValidator validator;
    private ObjectProvider<EntityNameRegistry> entityRegistryProvider;
    private ObjectProvider<EvaluateObject> evaluateObjectProvider;
    private EvaluateObject evaluateObject;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        entityRegistryProvider = mock(ObjectProvider.class);
        when(entityRegistryProvider.getIfAvailable()).thenReturn(null);
        evaluateObjectProvider = mock(ObjectProvider.class);
        when(evaluateObjectProvider.getIfAvailable()).thenReturn(null);
        evaluateObject = mock(EvaluateObject.class);
        validator = new AppDefinitionValidator(entityRegistryProvider,
                new AppRuleValidator(evaluateObjectProvider));
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
    void duplicateWidgetIdsWithinAPage_areReported() {
        AppDefinitionInput input = validInput();
        input.getPages().getFirst().setWidgets(List.of(new WidgetInstance("widget-grid", "entity-grid"),
                new WidgetInstance("widget-grid", "entity-grid")));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.duplicate-widget-id");
    }

    @Test
    void containerWidgetComposingReferencedSiblings_isAccepted() {
        AppDefinitionInput input = validInput();
        input.getPages().getFirst().setWidgets(List.of(tabGroup("widget-grid"), referencedGrid("widget-grid")));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    @Test
    void childIdNamingNoWidgetAtAll_isReported() {
        AppDefinitionInput input = validInput();
        input.getPages().getFirst().setWidgets(List.of(tabGroup("widget-absent")));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.dangling-child-id");
    }

    /**
     * A STANDALONE target is as wrong as a missing one: it renders at its own position, so placing it
     * in a container too would show it twice.
     */
    @Test
    void childIdNamingAStandaloneWidget_isReported() {
        AppDefinitionInput input = validInput();
        input.getPages().getFirst().setWidgets(List.of(tabGroup("widget-grid"),
                new WidgetInstance("widget-grid", "entity-grid")));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.dangling-child-id");
    }

    /** A declared-but-unplaced widget is a half-finished draft, so it must not reject the write. */
    @Test
    void referencedWidgetNothingPointsAt_isAWarningRatherThanAnError() {
        AppDefinitionInput input = validInput();
        input.getPages().getFirst().setWidgets(List.of(referencedGrid("widget-grid")));

        List<AppValidationProblem> problems = validator.validate("my-org", input);

        assertThat(errorIds(problems)).containsExactly("app.validation.orphan-widget");
        assertThat(AppValidationProblem.blocking(problems)).isEmpty();
    }

    /**
     * {@code props} is an open map, so {@code childIds} may hold anything at all. Whatever is not an
     * id is the widget type's own problem, not a referential-integrity failure.
     */
    @Test
    void childIdsThatIsNotAListOfIds_isLeftToTheWidgetType() {
        AppDefinitionInput input = validInput();
        WidgetInstance container = new WidgetInstance("widget-container", "tab-group");
        container.setProps(Map.of("childIds", "widget-grid"));
        input.getPages().getFirst().setWidgets(List.of(container));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    private static WidgetInstance tabGroup(String... childIds) {
        WidgetInstance container = new WidgetInstance("widget-container", "tab-group");
        container.setProps(Map.of("childIds", List.of(childIds)));
        return container;
    }

    private static WidgetInstance referencedGrid(String id) {
        WidgetInstance grid = new WidgetInstance(id, "entity-grid");
        grid.setPlacement(WidgetInstance.PlacementEnum.REFERENCED);
        return grid;
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
        content.setWidgets(List.of(new WidgetInstance("widget-stray", "entity-grid")));
        input.getRegions().add(content);

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.widgets-not-allowed");
    }

    @Test
    void widgetsOnAHeaderRegion_areAllowed() {
        AppDefinitionInput input = validInput();
        RegionDefinition header = new RegionDefinition(RegionType.HEADER);
        header.setWidgets(List.of(new WidgetInstance("widget-language", "language-selector")));
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
        WidgetInstance grid = new WidgetInstance("widget-grid", "entity-grid");
        grid.setProps(Map.of("entityName", "Claim"));
        input.getPages().getFirst().setWidgets(List.of(grid));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.unknown-entity-name");
    }

    @Test
    void entityNameIsNotCheckedWhenNoRegistryIsAvailable() {
        AppDefinitionInput input = validInput();
        WidgetInstance grid = new WidgetInstance("widget-grid", "entity-grid");
        grid.setProps(Map.of("entityName", "NoSuchEntity"));
        input.getPages().getFirst().setWidgets(List.of(grid));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    // --- rules of the organization -------------------------------------------------------

    @Test
    void violatedErrorRule_isReportedAndBlocksPersisting() {
        givenViolations(new RuleViolation("app-id-is-route-safe", "App id is route-safe",
                Severity.ERROR, "An app id is lowercase letters, digits and single hyphens.",
                "rule.appDefinition.idIsRouteSafe"));

        List<AppValidationProblem> problems = validator.validate("my-org", validInput());

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst().errorId()).isEqualTo("rule.appDefinition.idIsRouteSafe");
        assertThat(problems.getFirst().path()).isEqualTo("/");
        assertThat(AppValidationProblem.blocking(problems)).hasSize(1);
    }

    @Test
    void violatedWarningRule_isReportedButDoesNotBlockPersisting() {
        givenViolations(new RuleViolation("app-declares-a-populated-sidenav", "App has navigation",
                Severity.WARNING, "This app declares no sidenav navigation.", null));

        List<AppValidationProblem> problems = validator.validate("my-org", validInput());

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst().severity()).isEqualTo(Severity.WARNING);
        assertThat(AppValidationProblem.blocking(problems)).isEmpty();
    }

    /** A rule author who declares no Transloco key still gets a stable, rule-specific identifier. */
    @Test
    void violationWithoutTranslocoId_getsAnErrorIdDerivedFromTheRuleId() {
        givenViolations(new RuleViolation("titles-are-translatable", "Titles are translatable",
                Severity.INFO, "Give every page title a Transloco id.", "  "));

        assertThat(errorIds(validator.validate("my-org", validInput())))
                .containsExactly("app.validation.rule.titles-are-translatable");
    }

    /** The stored-definition path publishing uses must consult the rules too. */
    @Test
    void storedDefinition_isAlsoEvaluatedAgainstTheRules() {
        givenViolations(new RuleViolation("page-ids-are-route-safe", "Page ids are route-safe",
                Severity.ERROR, "Every page id must be lowercase.", null));

        AppDefinition stored = new AppDefinition("claims-app", "Claims Management");
        stored.setPages(List.of(new PageDefinition("Page_One", "Claims", List.of())));

        assertThat(errorIds(validator.validateStored("my-org", stored)))
                .contains("app.validation.rule.page-ids-are-route-safe");
    }

    @Test
    void rulesAreNotConsultedWhenNoRuleEngineIsWired() {
        assertThat(validator.validate("my-org", validInput())).isEmpty();
        verifyNoInteractions(evaluateObject);
    }

    /** Publishing validates what is stored, so the stored path needs the same missing-body answer. */
    @Test
    void nullStoredDefinition_reportsMissingBody() {
        assertThat(errorIds(validator.validateStored("my-org", null)))
                .containsExactly("app.validation.missing-body");
    }

    /**
     * A definition with nothing declared at all is what the very first save looks like. It has to pass
     * every collection walk without a null check firing.
     */
    @Test
    void aDefinitionDeclaringNoRegionsPagesOrOverrides_isValid() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setTheme(new ThemeDefinition());

        assertThat(validator.validate("my-org", input)).isEmpty();
        assertThat(validator.validateStored("my-org", new AppDefinition("claims-app", "Claims Management")))
                .isEmpty();
    }

    /**
     * A JSON body may state {@code "regions": null} rather than omitting the field, and a hand-written
     * YAML file routinely does. Every collection walk has to survive that, at every depth.
     */
    @Test
    void explicitlyNullCollectionsAreWalkedWithoutAProblemBeingInvented() {
        ThemeDefinition theme = new ThemeDefinition();
        theme.setTokenOverrides(null);
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setTheme(theme);
        input.setRegions(null);
        input.setPages(null);

        assertThat(validator.validate("my-org", input)).isEmpty();

        NavItem nav = navItem("nav-claims", "page-claims-list");
        nav.setChildren(null);
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(null);
        RegionDefinition header = new RegionDefinition(RegionType.HEADER);
        header.setWidgets(null);
        WidgetInstance propless = new WidgetInstance("widget-1", "markdown");
        propless.setProps(null);
        input.setRegions(List.of(sidenav, header));
        input.setPages(List.of(page("page-claims-list", "Claims", propless)));

        // The page is now unreachable — the sidenav declares no navigation at all — which is the only
        // thing that may be reported here.
        assertThat(errorIds(validator.validate("my-org", input)))
                .containsExactly("app.validation.orphan-page");

        sidenav.setNavItems(List.of(nav));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    /** JSON and YAML can both produce a {@code null} list entry; each one names its own position. */
    @Test
    void aNullEntryInAnyCollection_isReportedAtItsOwnPath() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setPages(java.util.Arrays.asList(null, page("page-claims-list", "Claims", widget("w-1"), null)));
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(java.util.Arrays.asList(navItem("nav-claims", "page-claims-list"), null));
        input.setRegions(java.util.Arrays.asList(null, sidenav));

        List<AppValidationProblem> problems = validator.validate("my-org", input);

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.errorId()).isEqualTo("app.validation.null-page");
            assertThat(problem.path()).isEqualTo("/pages/0");
        });
        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.errorId()).isEqualTo("app.validation.null-region");
            assertThat(problem.path()).isEqualTo("/regions/0");
        });
        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.errorId()).isEqualTo("app.validation.null-nav-item");
            assertThat(problem.path()).isEqualTo("/regions/1/navItems/1");
        });
        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.errorId()).isEqualTo("app.validation.null-widget");
            assertThat(problem.path()).isEqualTo("/pages/1/widgets/1");
        });
    }

    /** A region with no type cannot be placed at all, so nothing inside it is inspected either. */
    @Test
    void aRegionWithoutAType_isReportedAndItsContentsAreNotInspected() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        RegionDefinition untyped = new RegionDefinition();
        untyped.setNavItems(List.of(navItem("  ", null)));
        input.setRegions(List.of(untyped));

        assertThat(errorIds(validator.validate("my-org", input)))
                .containsExactly("app.validation.missing-region-type");
    }

    @Test
    void aPageWithoutAnIdOrATitle_isReported() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setPages(List.of(page(" ", null)));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.missing-page-id", "app.validation.missing-page-title");
    }

    /** A page with no id cannot be referenced, so it must not also be reported as an orphan. */
    @Test
    void aPageWithoutAnId_isNotAlsoReportedAsUnreachable() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setPages(List.of(page(null, "Claims")));

        assertThat(errorIds(validator.validate("my-org", input)))
                .doesNotContain("app.validation.orphan-page");
    }

    @Test
    void aWidgetWithoutAnIdOrATypeIsReported() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        RegionDefinition header = new RegionDefinition(RegionType.HEADER);
        header.setWidgets(List.of(new WidgetInstance(" ", null)));
        input.setRegions(List.of(header));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.missing-widget-id", "app.validation.missing-widget-type");
    }

    @Test
    void aNavItemWithoutAnIdOrALabelIsReported() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        NavItem nameless = new NavItem(" ", "  ");
        nameless.setPageId("page-claims-list");
        sidenav.setNavItems(List.of(nameless));
        input.setRegions(List.of(sidenav));
        input.setPages(List.of(page("page-claims-list", "Claims")));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.missing-nav-item-id", "app.validation.missing-nav-item-label");
    }

    /**
     * Only the {@code entityName} cross-reference is checked, and only when there is something to
     * check it against — a widget whose props say nothing about an entity is not the validator's
     * business.
     */
    @Test
    void widgetPropsWithoutAUsableEntityName_areNotCrossReferenced() {
        EntityNameRegistry registry = mock(EntityNameRegistry.class);
        when(registry.isKnownEntity(any(), any())).thenReturn(false);
        when(entityRegistryProvider.getIfAvailable()).thenReturn(registry);

        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        RegionDefinition header = new RegionDefinition(RegionType.HEADER);
        WidgetInstance withoutProps = new WidgetInstance("widget-1", "markdown");
        WidgetInstance withOtherProps = new WidgetInstance("widget-2", "markdown");
        withOtherProps.setProps(Map.of("content", "# Welcome"));
        WidgetInstance withBlankEntityName = new WidgetInstance("widget-3", "entity-grid");
        withBlankEntityName.setProps(Map.of("entityName", "  "));
        WidgetInstance withNonTextEntityName = new WidgetInstance("widget-4", "entity-grid");
        withNonTextEntityName.setProps(Map.<String, Object>of("entityName", 42));
        header.setWidgets(List.of(withoutProps, withOtherProps, withBlankEntityName, withNonTextEntityName));
        input.setRegions(List.of(header));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    /**
     * The port defaults to accepting every name, so a registry that implements nothing must not start
     * rejecting perfectly good definitions.
     */
    @Test
    void aRegistryThatImplementsNothing_acceptsEveryEntityName() {
        when(entityRegistryProvider.getIfAvailable()).thenReturn(new EntityNameRegistry() { });

        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        RegionDefinition header = new RegionDefinition(RegionType.HEADER);
        header.setWidgets(List.of(widget("widget-grid")));
        input.setRegions(List.of(header));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    /** A theme token key is arbitrary text from the designer, including, in a hand-edited file, none. */
    @Test
    void anAbsentThemeTokenName_isReportedAsUnknownRatherThanCrashing() {
        Map<String, String> overrides = new java.util.HashMap<>();
        overrides.put(null, "#0d1b2a");
        ThemeDefinition theme = new ThemeDefinition();
        theme.setTokenOverrides(overrides);
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setTheme(theme);

        assertThat(errorIds(validator.validate("my-org", input)))
                .containsExactly("app.validation.unknown-theme-token");
        assertThat(PpThemeTokens.isKnown(null)).isFalse();
    }

    private static PageDefinition page(String id, String title, WidgetInstance... widgets) {
        return new PageDefinition(id, title, java.util.Arrays.asList(widgets));
    }

    private static NavItem navItem(String id, String pageId) {
        NavItem item = new NavItem(id, "Claims");
        item.setPageId(pageId);
        return item;
    }

    private static WidgetInstance widget(String id) {
        WidgetInstance widget = new WidgetInstance(id, "entity-grid");
        widget.setProps(Map.of("entityName", "Claim"));
        return widget;
    }

    private void givenViolations(RuleViolation... violations) {
        when(evaluateObjectProvider.getIfAvailable()).thenReturn(evaluateObject);
        boolean passed = java.util.Arrays.stream(violations).noneMatch(v -> v.severity() == Severity.ERROR);
        when(evaluateObject.execute(any(), any(), any()))
                .thenReturn(new EvaluationOutcome(passed, List.of(violations)));
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
