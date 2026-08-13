package com.processpuzzle.app.usecase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.processpuzzle.app.model.AppDefinitionInput;
import com.processpuzzle.app.model.LayoutDefinition;
import com.processpuzzle.app.model.NavItem;
import com.processpuzzle.app.model.PageDefinition;
import com.processpuzzle.app.model.RegionDefinition;
import com.processpuzzle.app.model.RegionType;
import com.processpuzzle.app.model.WidgetRef;
import com.processpuzzle.app.usecase.AppValidationProblem;
import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the object a PPCL expression actually sees against the rules ProcessPuzzle ships. The rules are
 * not restated here: they are read from {@code sample-rules/processpuzzle-rules.yaml} on the
 * base-rule-backend classpath and run through a real {@link RuleEngine}, so the assertions fail if
 * either side moves — a contract field renamed, an enum no longer serialized by its wire value, or a
 * rule expression rewritten against a shape the backend does not produce.
 *
 * <p>That is the failure mode worth a test: everything else about rule evaluation is covered inside
 * base-rule, and a mismatch here is silent. A rule reading {@code r.type === 'sidenav'} against a
 * region serialized as {@code SIDENAV} simply never fires.
 *
 * <p>The rule repository is mocked rather than backed by a database: what is under test is the shape of
 * the input and the expressions themselves, not JPA.
 */
class AppRuleValidatorTest {

    private static final String ORG = "my-org";
    private static final String SAMPLE_RULES = "/sample-rules/processpuzzle-rules.yaml";

    private RuleEngine ruleEngine;
    private AppRuleValidator ruleValidator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws IOException {
        RuleDefinitionRepository repository = mock(RuleDefinitionRepository.class);
        when(repository.findByOrgKeyAndContext(eq(ORG), eq(AppRuleValidator.RULE_CONTEXT)))
                .thenReturn(shippedAppDefinitionRules());

        ruleEngine = new RuleEngine();
        ObjectProvider<EvaluateObject> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new EvaluateObject(repository, ruleEngine));
        ruleValidator = new AppRuleValidator(provider);
    }

    @AfterEach
    void tearDown() {
        ruleEngine.close();
    }

    /** Guards against the inverse failure: rules that fire on everything are as useless as ones that never do. */
    @Test
    void definitionFollowingEveryConvention_violatesNothing() {
        assertThat(ruleValidator.validate(ORG, conformingInput())).isEmpty();
    }

    @Test
    void appIdThatIsNotRouteSafe_violatesAnErrorRule() {
        AppDefinitionInput input = conformingInput();
        input.setId("Claims App");

        List<AppValidationProblem> problems = ruleValidator.validate(ORG, input);

        assertThat(errorIds(problems)).containsExactly("rule.appDefinition.idIsRouteSafe");
        assertThat(AppValidationProblem.blocking(problems)).hasSize(1);
    }

    /** Proves page ids reach the expression through the nested {@code pages} array. */
    @Test
    void pageIdThatIsNotRouteSafe_violatesAnErrorRule() {
        AppDefinitionInput input = conformingInput();
        input.getPages().getFirst().setId("Claims_List");
        input.getRegions().getFirst().getNavItems().getFirst().setPageId("Claims_List");

        assertThat(errorIds(ruleValidator.validate(ORG, input)))
                .containsExactly("rule.appDefinition.pageIdsAreRouteSafe");
    }

    /** Proves {@code RegionType.SIDENAV} arrives as {@code 'sidenav'}, the value the rules compare against. */
    @Test
    void appWithoutAPopulatedSidenav_violatesAWarningRuleOnly() {
        AppDefinitionInput input = conformingInput();
        input.setRegions(List.of(new RegionDefinition(RegionType.CONTENT)));

        List<AppValidationProblem> problems = ruleValidator.validate(ORG, input);

        assertThat(errorIds(problems)).contains("rule.appDefinition.declaresAPopulatedSidenav");
        assertThat(severityOf(problems, "rule.appDefinition.declaresAPopulatedSidenav"))
                .isEqualTo(Severity.WARNING);
        assertThat(AppValidationProblem.blocking(problems)).isEmpty();
    }

    /** Proves a widget's free-form {@code props} map survives the conversion. */
    @Test
    void entityGridWithoutAnEntityName_violatesAnErrorRule() {
        AppDefinitionInput input = conformingInput();
        input.getPages().getFirst().setWidgets(List.of(new WidgetRef("widget-grid", "entity-grid")));

        assertThat(errorIds(ruleValidator.validate(ORG, input)))
                .contains("rule.appDefinition.entityWidgetsDeclareAnEntityName");
    }

    /** Proves a grid placed by a container — a sibling, not a child — is left alone when it conforms. */
    @Test
    void entityGridComposedByAContainer_isAcceptedWhenItNamesItsEntity() {
        AppDefinitionInput input = conformingInput();
        WidgetRef grid = new WidgetRef("widget-grid", "entity-grid");
        grid.setProps(Map.of("entityName", "Claim"));
        grid.setPlacement(WidgetRef.PlacementEnum.REFERENCED);
        WidgetRef container = new WidgetRef("widget-tabs", "tab-group");
        container.setProps(Map.of("childIds", List.of("widget-grid")));
        input.getPages().getFirst().setWidgets(List.of(container, grid));

        assertThat(ruleValidator.validate(ORG, input)).isEmpty();
    }

    /** Proves {@code layout} reaches the rules — it is not part of the structural validator's input. */
    @Test
    void contentMaxWidthWithoutAUnit_violatesAWarningRule() {
        AppDefinitionInput input = conformingInput();
        input.getLayout().setContentMaxWidth("1280");

        assertThat(errorIds(ruleValidator.validate(ORG, input)))
                .containsExactly("rule.appDefinition.contentMaxWidthIsACssLength");
    }

    @Test
    void noRuleOfAnotherContextIsEvaluated() {
        // The Organization rules in the same file must not be applied to an app definition: one of
        // them requires entity.key, which no app definition has.
        assertThat(errorIds(ruleValidator.validate(ORG, conformingInput())))
                .noneMatch(errorId -> errorId.startsWith("rule.organization."));
    }

    /**
     * The validator is called on the write paths, where a missing body is reported as a structural
     * problem — so there is nothing for a rule expression to read and no rule may fire.
     */
    @Test
    void nothingIsEvaluatedWhenThereIsNoDefinitionToEvaluate() {
        assertThat(ruleValidator.validate(ORG, null)).isEmpty();
    }

    // --- fixtures ------------------------------------------------------------------------

    /**
     * The App Definition rules exactly as shipped, read from base-rule-backend's own resource so this
     * test cannot drift from them.
     */
    @SuppressWarnings("unchecked")
    private static List<RuleDefinition> shippedAppDefinitionRules() throws IOException {
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        try (InputStream input = AppRuleValidatorTest.class.getResourceAsStream(SAMPLE_RULES)) {
            assertThat(input).as("bundled sample rules " + SAMPLE_RULES).isNotNull();
            Map<String, List<Map<String, Object>>> document = yaml.readValue(input, Map.class);
            List<RuleDefinition> rules = new ArrayList<>();
            for (Map<String, Object> entry : document.get("rules")) {
                if (!AppRuleValidator.RULE_CONTEXT.equals(entry.get("context"))) {
                    continue;
                }
                rules.add(new RuleDefinition(ORG,
                        (String) entry.get("id"),
                        (String) entry.get("name"),
                        (String) entry.get("description"),
                        (String) entry.get("context"),
                        (String) entry.get("expression"),
                        Severity.valueOf((String) entry.get("severity")),
                        (String) entry.get("message"),
                        (String) entry.get("translocoId"),
                        null, false, true));
            }
            assertThat(rules).as("App Definition rules in " + SAMPLE_RULES).isNotEmpty();
            return rules;
        }
    }

    /**
     * A definition that satisfies every shipped rule, including the INFO-level ones — mutable, so each
     * test above breaks exactly one convention.
     */
    private static AppDefinitionInput conformingInput() {
        AppDefinitionInput input = new AppDefinitionInput("claims-app", "Claims Management");
        input.setTranslocoId("claimsApp.name");

        PageDefinition page = new PageDefinition("page-claims-list", "Claims", new ArrayList<>());
        page.setTranslocoId("claimsApp.page.claimsList");
        input.setPages(new ArrayList<>(List.of(page)));

        NavItem nav = new NavItem("nav-claims", "Claims");
        nav.setPageId("page-claims-list");
        nav.setIcon("list");
        nav.setRoles(List.of("CLAIMS_ADJUSTER"));
        RegionDefinition sidenav = new RegionDefinition(RegionType.SIDENAV);
        sidenav.setNavItems(new ArrayList<>(List.of(nav)));
        input.setRegions(new ArrayList<>(List.of(sidenav)));

        LayoutDefinition layout = new LayoutDefinition();
        layout.setContentMaxWidth("1280px");
        input.setLayout(layout);

        return input;
    }

    private static List<String> errorIds(List<AppValidationProblem> problems) {
        return problems.stream().map(AppValidationProblem::errorId).toList();
    }

    private static Severity severityOf(List<AppValidationProblem> problems, String errorId) {
        return problems.stream().filter(problem -> problem.errorId().equals(errorId))
                .map(AppValidationProblem::severity).findFirst().orElseThrow();
    }
}
