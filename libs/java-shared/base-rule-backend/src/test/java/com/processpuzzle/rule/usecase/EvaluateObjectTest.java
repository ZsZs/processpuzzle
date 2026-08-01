package com.processpuzzle.rule.usecase;

import com.processpuzzle.rule.domain.RuleDefinition;
import com.processpuzzle.rule.domain.RuleDefinitionRepository;
import com.processpuzzle.rule.domain.Severity;
import com.processpuzzle.rule.usecase.engine.RuleEngine;
import com.processpuzzle.rule.usecase.engine.RuleKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Complements {@link RuleTenantIsolationTest}, which drives the same use case against GraalJS. */
class EvaluateObjectTest {

    private static final Map<String, Object> ENTITY = Map.of("quantity", 3);
    private static final RuleKey KEY = RuleKey.of("demo", "max-quantity");

    private RuleDefinitionRepository repository;
    private RuleEngine ruleEngine;
    private EvaluateObject evaluateObject;

    @BeforeEach
    void setUp() {
        repository = mock(RuleDefinitionRepository.class);
        ruleEngine = mock(RuleEngine.class);
        evaluateObject = new EvaluateObject(repository, ruleEngine);
    }

    @Test
    void rejectsAMissingContextBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> evaluateObject.execute("demo", null, ENTITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("context is required");
        assertThatThrownBy(() -> evaluateObject.execute("demo", "  ", ENTITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("context is required");

        verifyNoInteractions(repository, ruleEngine);
    }

    @Test
    void rejectsAMissingEntity() {
        assertThatThrownBy(() -> evaluateObject.execute("demo", "Order", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("entity is required");

        verifyNoInteractions(repository, ruleEngine);
    }

    @Test
    void passesWhenNoRuleIsDefinedForTheContext() {
        when(repository.findByOrgKeyAndContext("demo", "Order")).thenReturn(List.of());

        EvaluationOutcome outcome = evaluateObject.execute("demo", "Order", ENTITY);

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.violations()).isEmpty();
    }

    @Test
    void registersARuleTheEngineHasNotSeenYet() {
        // The path a freshly-restarted instance takes before its engine state was re-synced.
        stubRules(rule("max-quantity", Severity.ERROR, "too many", null, true));
        when(ruleEngine.isRegistered(KEY)).thenReturn(false);
        when(ruleEngine.evaluate(eq(KEY), any())).thenReturn(true);

        evaluateObject.execute("demo", "Order", ENTITY);

        verify(ruleEngine).registerRule(KEY, "entity.quantity <= 5");
    }

    @Test
    void reusesAnAlreadyRegisteredRule() {
        stubRules(rule("max-quantity", Severity.ERROR, "too many", null, true));
        when(ruleEngine.isRegistered(KEY)).thenReturn(true);
        when(ruleEngine.evaluate(eq(KEY), any())).thenReturn(true);

        evaluateObject.execute("demo", "Order", ENTITY);

        verify(ruleEngine, never()).registerRule(any(RuleKey.class), anyString());
    }

    @Test
    void skipsDisabledRulesEntirely() {
        stubRules(rule("max-quantity", Severity.ERROR, "too many", null, false));

        EvaluationOutcome outcome = evaluateObject.execute("demo", "Order", ENTITY);

        assertThat(outcome.passed()).isTrue();
        verifyNoInteractions(ruleEngine);
    }

    @Test
    void aFailingErrorRuleFailsTheWholeEvaluation() {
        stubRules(rule("max-quantity", Severity.ERROR, "too many", "rule.max", true));
        when(ruleEngine.isRegistered(KEY)).thenReturn(true);
        when(ruleEngine.evaluate(eq(KEY), any())).thenReturn(false);

        EvaluationOutcome outcome = evaluateObject.execute("demo", "Order", ENTITY);

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.ruleId()).isEqualTo("max-quantity");
            assertThat(violation.ruleName()).isEqualTo("max-quantity");
            assertThat(violation.severity()).isEqualTo(Severity.ERROR);
            assertThat(violation.message()).isEqualTo("too many");
            assertThat(violation.translocoId()).isEqualTo("rule.max");
        });
    }

    @Test
    void aFailingWarningIsReportedWithoutFailingTheEvaluation() {
        stubRules(rule("max-quantity", Severity.WARNING, "consider fewer", null, true));
        when(ruleEngine.isRegistered(KEY)).thenReturn(true);
        when(ruleEngine.evaluate(eq(KEY), any())).thenReturn(false);

        EvaluationOutcome outcome = evaluateObject.execute("demo", "Order", ENTITY);

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.violations()).extracting(RuleViolation::severity).containsExactly(Severity.WARNING);
    }

    @Test
    void aBrokenExpressionBecomesAnErrorViolationInsteadOfPropagating() {
        stubRules(rule("max-quantity", Severity.WARNING, "consider fewer", null, true));
        when(ruleEngine.isRegistered(KEY)).thenReturn(true);
        when(ruleEngine.evaluate(eq(KEY), any())).thenThrow(new IllegalStateException("not a boolean"));

        EvaluationOutcome outcome = evaluateObject.execute("demo", "Order", ENTITY);

        // Escalated to ERROR even though the rule itself is a WARNING: a rule that cannot be
        // evaluated must not silently pass.
        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.severity()).isEqualTo(Severity.ERROR);
            assertThat(violation.message()).isEqualTo("Evaluation failed: not a boolean");
        });
    }

    @Test
    void fallsBackFromMessageToDescriptionToName() {
        RuleDefinition blankMessage = rule("max-quantity", Severity.ERROR, "  ", null, true);
        blankMessage.setDescription("described");
        stubRules(blankMessage);
        when(ruleEngine.isRegistered(KEY)).thenReturn(true);
        when(ruleEngine.evaluate(eq(KEY), any())).thenReturn(false);

        assertThat(evaluateObject.execute("demo", "Order", ENTITY).violations())
                .extracting(RuleViolation::message).containsExactly("described");

        RuleDefinition blankDescription = rule("max-quantity", Severity.ERROR, null, null, true);
        blankDescription.setDescription("   ");
        stubRules(blankDescription);

        assertThat(evaluateObject.execute("demo", "Order", ENTITY).violations())
                .extracting(RuleViolation::message).containsExactly("max-quantity");

        stubRules(rule("max-quantity", Severity.ERROR, null, null, true));

        assertThat(evaluateObject.execute("demo", "Order", ENTITY).violations())
                .extracting(RuleViolation::message).containsExactly("max-quantity");
    }

    private void stubRules(RuleDefinition... rules) {
        when(repository.findByOrgKeyAndContext("demo", "Order")).thenReturn(List.of(rules));
    }

    private static RuleDefinition rule(String id, Severity severity, String message,
                                       String translocoId, boolean enabled) {
        return new RuleDefinition("demo", id, id, null, "Order", "entity.quantity <= 5",
                severity, message, translocoId, null, false, enabled, List.of());
    }
}
