package com.processpuzzle.app.usecase.service;

import com.processpuzzle.app.usecase.port.RuleEvaluator;
import com.processpuzzle.app.AppTestFixtures;
import com.processpuzzle.app.model.AppDefinition;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.NavItem;
import com.processpuzzle.app.model.RouteDefinition;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.model.ThemeDefinition;
import com.processpuzzle.shared.model.WidgetInstance;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.app.usecase.port.EntityNameRegistry;
import com.processpuzzle.app.usecase.Severity;
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
    private ObjectProvider<RuleEvaluator> ruleEvaluatorProvider;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        entityRegistryProvider = mock(ObjectProvider.class);
        when(entityRegistryProvider.getIfAvailable()).thenReturn(null);
        ruleEvaluatorProvider = AppTestFixtures.noRuleEvaluator();
        validator = new AppDefinitionValidator(entityRegistryProvider,
                new AppRuleValidator(ruleEvaluatorProvider));
    }

    @Test
    void wellFormedDefinition_hasNoProblems() {
        assertThat(validator.validate("my-org", validInput())).isEmpty();
    }

    @Test
    void freshlyProvisionedDefinition_isValid() {
        AppDefinitionInput input = new AppDefinitionInput("app", "My Org");
        input.setRegions(List.of(new RegionDefinition(RegionType.HEADER)));

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
        input.getRegions().getFirst().getNavItems().getFirst().setRoutePath("route-does-not-exist");

        List<AppValidationProblem> problems = validator.validate("my-org", input);

        assertThat(errorIds(problems)).contains("app.validation.unknown-route-reference");
        assertThat(problems.stream().map(AppValidationProblem::path))
                .contains("/regions/0/navItems/0/routePath");
    }

    @Test
    void pageNoNavItemReaches_isReportedAsOrphan() {
        AppDefinitionInput input = validInput();
        input.getRoutes().add(route("route-unreachable", "Nowhere"));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.orphan-route");
    }

    @Test
    void pageReachedOnlyThroughANestedNavItem_isNotAnOrphan() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims");
        input.setRoutes(List.of(route("route-deep", "Deep")));
        NavItem child = new NavItem("nav-child", "Child");
        child.setRoutePath("route-deep");
        NavItem group = new NavItem("nav-group", "Group");
        group.setChildren(List.of(child));
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(List.of(group));
        input.setRegions(List.of(sidenav));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    @Test
    void duplicateRoutePaths_areReported() {
        AppDefinitionInput input = validInput();
        input.getRoutes().add(route("claims-list", "Duplicate"));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.duplicate-route-path");
    }

    @Test
    void duplicateNavItemIds_areReportedAcrossNesting() {
        AppDefinitionInput input = validInput();
        NavItem duplicate = new NavItem("nav-claims", "Same id again");
        duplicate.setRoutePath("claims-list");
        input.getRegions().getFirst().getNavItems().add(duplicate);

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.duplicate-nav-item-id");
    }

    @Test
    void duplicateWidgetIdsWithinAPage_areReported() {
        AppDefinitionInput input = validInput();
        input.getRoutes().getFirst().setTarget(AppTestFixtures.widgetsTarget(new WidgetInstance("widget-grid", "entity-grid"),
                new WidgetInstance("widget-grid", "entity-grid")));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.duplicate-widget-id");
    }

    @Test
    void containerWidgetComposingReferencedSiblings_isAccepted() {
        AppDefinitionInput input = validInput();
        input.getRoutes().getFirst().setTarget(AppTestFixtures.widgetsTarget(tabGroup("widget-grid"), referencedGrid("widget-grid")));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    @Test
    void childIdNamingNoWidgetAtAll_isReported() {
        AppDefinitionInput input = validInput();
        input.getRoutes().getFirst().setTarget(AppTestFixtures.widgetsTarget(tabGroup("widget-absent")));

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
        input.getRoutes().getFirst().setTarget(AppTestFixtures.widgetsTarget(tabGroup("widget-grid"),
                new WidgetInstance("widget-grid", "entity-grid")));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.dangling-child-id");
    }

    /** A declared-but-unplaced widget is a half-finished draft, so it must not reject the write. */
    @Test
    void referencedWidgetNothingPointsAt_isAWarningRatherThanAnError() {
        AppDefinitionInput input = validInput();
        input.getRoutes().getFirst().setTarget(AppTestFixtures.widgetsTarget(referencedGrid("widget-grid")));

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
        input.getRoutes().getFirst().setTarget(AppTestFixtures.widgetsTarget(container));

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
        stray.setRoutePath("claims-list");
        footer.setNavItems(List.of(stray));
        input.getRegions().add(footer);

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.nav-items-not-allowed");
    }

    @Test
    void widgetsOnASidenavRegion_areReported() {
        AppDefinitionInput input = validInput();
        input.getRegions().getFirst().setWidgets(List.of(new WidgetInstance("widget-stray", "entity-grid")));

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
        input.getRoutes().getFirst().setTarget(AppTestFixtures.widgetsTarget(grid));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.unknown-entity-name");
    }

    @Test
    void entityNameIsNotCheckedWhenNoRegistryIsAvailable() {
        AppDefinitionInput input = validInput();
        WidgetInstance grid = new WidgetInstance("widget-grid", "entity-grid");
        grid.setProps(Map.of("entityName", "NoSuchEntity"));
        input.getRoutes().getFirst().setTarget(AppTestFixtures.widgetsTarget(grid));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    // --- rules of the organization -------------------------------------------------------

    @Test
    void violatedErrorRule_isReportedAndBlocksPersisting() {
        givenViolations(new RuleEvaluator.Violation("app-id-is-route-safe", "An app id is lowercase letters, digits and single hyphens.", "rule.appDefinition.idIsRouteSafe", Severity.ERROR));

        List<AppValidationProblem> problems = validator.validate("my-org", validInput());

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst().errorId()).isEqualTo("rule.appDefinition.idIsRouteSafe");
        assertThat(problems.getFirst().path()).isEqualTo("/");
        assertThat(AppValidationProblem.blocking(problems)).hasSize(1);
    }

    @Test
    void violatedWarningRule_isReportedButDoesNotBlockPersisting() {
        givenViolations(new RuleEvaluator.Violation("app-declares-a-populated-sidenav", "This app declares no sidenav navigation.", null, Severity.WARNING));

        List<AppValidationProblem> problems = validator.validate("my-org", validInput());

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst().severity()).isEqualTo(Severity.WARNING);
        assertThat(AppValidationProblem.blocking(problems)).isEmpty();
    }

    /** A rule author who declares no Transloco key still gets a stable, rule-specific identifier. */
    @Test
    void violationWithoutTranslocoId_getsAnErrorIdDerivedFromTheRuleId() {
        givenViolations(new RuleEvaluator.Violation("titles-are-translatable", "Give every route title a Transloco id.", "  ", Severity.INFO));

        assertThat(errorIds(validator.validate("my-org", validInput())))
                .containsExactly("app.validation.rule.titles-are-translatable");
    }

    /** The stored-definition path publishing uses must consult the rules too. */
    @Test
    void storedDefinition_isAlsoEvaluatedAgainstTheRules() {
        givenViolations(new RuleEvaluator.Violation("route-ids-are-route-safe", "Every route id must be lowercase.", null, Severity.ERROR));

        AppDefinition stored = new AppDefinition("claims-app", "Claims Management");
        stored.setRoutes(List.of(route("Page_One", "Claims")));

        assertThat(errorIds(validator.validateStored("my-org", stored)))
                .contains("app.validation.rule.route-ids-are-route-safe");
    }

    @Test
    void rulesAreNotConsultedWhenNoRuleEngineIsWired() {
        assertThat(validator.validate("my-org", validInput())).isEmpty();
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
        input.setRoutes(null);

        assertThat(validator.validate("my-org", input)).isEmpty();

        NavItem nav = navItem("nav-claims", "claims-list");
        nav.setChildren(null);
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(null);
        RegionDefinition header = new RegionDefinition(RegionType.HEADER);
        header.setWidgets(null);
        WidgetInstance propless = new WidgetInstance("widget-1", "markdown");
        propless.setProps(null);
        input.setRegions(List.of(sidenav, header));
        input.setRoutes(List.of(route("claims-list", "Claims", propless)));

        // The route is now unreachable — the sidenav declares no navigation at all — which is the only
        // thing that may be reported here.
        assertThat(errorIds(validator.validate("my-org", input)))
                .containsExactly("app.validation.orphan-route");

        sidenav.setNavItems(List.of(nav));

        assertThat(validator.validate("my-org", input)).isEmpty();
    }

    /** JSON and YAML can both produce a {@code null} list entry; each one names its own position. */
    @Test
    void aNullEntryInAnyCollection_isReportedAtItsOwnPath() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setRoutes(java.util.Arrays.asList(null, route("claims-list", "Claims", widget("w-1"), null)));
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(java.util.Arrays.asList(navItem("nav-claims", "claims-list"), null));
        input.setRegions(java.util.Arrays.asList(null, sidenav));

        List<AppValidationProblem> problems = validator.validate("my-org", input);

        assertThat(problems).anySatisfy(problem -> {
            assertThat(problem.errorId()).isEqualTo("app.validation.null-route");
            assertThat(problem.path()).isEqualTo("/routes/0");
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
            assertThat(problem.path()).isEqualTo("/routes/1/target/widgets/1");
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
    void aRouteWithoutAPathOrATitle_isReported() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setRoutes(List.of(route(" ", null)));

        assertThat(errorIds(validator.validate("my-org", input)))
                .contains("app.validation.missing-route-path", "app.validation.missing-route-title");
    }

    /** A route with no path cannot be referenced, so it must not also be reported as an orphan. */
    @Test
    void aRouteWithoutAPath_isNotAlsoReportedAsUnreachable() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setRoutes(List.of(route(null, "Claims")));

        assertThat(errorIds(validator.validate("my-org", input)))
                .doesNotContain("app.validation.orphan-route");
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
        nameless.setRoutePath("claims-list");
        sidenav.setNavItems(List.of(nameless));
        input.setRegions(List.of(sidenav));
        input.setRoutes(List.of(route("claims-list", "Claims")));

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

    private static RouteDefinition route(String id, String title, WidgetInstance... widgets) {
        return AppTestFixtures.routeDefinition(id, title, widgets);
    }

    private static NavItem navItem(String id, String routePath) {
        NavItem item = new NavItem(id, "Claims");
        item.setRoutePath(routePath);
        return item;
    }

    private static WidgetInstance widget(String id) {
        WidgetInstance widget = new WidgetInstance(id, "entity-grid");
        widget.setProps(Map.of("entityName", "Claim"));
        return widget;
    }

    private void givenViolations(RuleEvaluator.Violation... violations) {
        // Built before the stubbing call: AppTestFixtures.ruleEvaluator() itself mocks and stubs, and
        // Mockito rejects that nested inside a when(...) argument as an unfinished stubbing.
        RuleEvaluator evaluator = AppTestFixtures.ruleEvaluator(violations).getIfAvailable();
        when(ruleEvaluatorProvider.getIfAvailable()).thenReturn(evaluator);
    }

    /** Mutable on purpose — every test above bends one part of it out of shape. */
    private static AppDefinitionInput validInput() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");

        RouteDefinition route = AppTestFixtures.routeDefinition("claims-list", "Claims");
        input.setRoutes(new java.util.ArrayList<>(List.of(route)));

        NavItem nav = new NavItem("nav-claims", "Claims");
        nav.setRoutePath("claims-list");
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(new java.util.ArrayList<>(List.of(nav)));
        input.setRegions(new java.util.ArrayList<>(List.of(sidenav)));

        return input;
    }

    private static List<String> errorIds(List<AppValidationProblem> problems) {
        return problems.stream().map(AppValidationProblem::errorId).toList();
    }
}
