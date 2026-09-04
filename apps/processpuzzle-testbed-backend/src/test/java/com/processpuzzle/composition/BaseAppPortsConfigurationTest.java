package com.processpuzzle.composition;

import com.processpuzzle.app.usecase.Severity;
import com.processpuzzle.app.usecase.port.RuleEvaluator;
import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.EvaluationOutcome;
import com.processpuzzle.rule.usecase.RuleViolation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The adapter that introduces base-app to base-rule.
 *
 * <p>It carries the only knowledge in the system that those two features are deployed together, so
 * the translation it performs is the seam that has to be right: base-app must receive its own types
 * and nothing more. A regression here would reintroduce, one field at a time, exactly the coupling
 * the port removed.
 *
 * <p>Three tests for a second adapter stood here — {@code TenantDirectory} over platform-admin's
 * {@code OrganizationRepository}, covering existence, the two-field projection and the absent case.
 * They went with the adapter when platform-admin became something this application may not name.
 * base-app's port is untouched and still has its own tests; what is no longer covered anywhere is
 * the <em>translation</em>, and it will need covering again wherever the bean is next declared —
 * {@code processpuzzle-admin-backend}, which unlike this application does have a tenant registry.
 */
class BaseAppPortsConfigurationTest {

    private final BaseAppPortsConfiguration configuration = new BaseAppPortsConfiguration();

    /**
     * Severity is translated by name across two structurally identical enums, one per feature. That
     * is the point rather than the cost: a constant added on one side surfaces here as a failure to
     * map rather than as a compile-time coupling nobody chose.
     */
    @Test
    void theRuleEvaluatorTranslatesViolationsIntoBaseAppsOwnTypes() {
        EvaluateObject evaluateObject = mock(EvaluateObject.class);
        when(evaluateObject.execute(any(), any(), any())).thenReturn(new EvaluationOutcome(false, List.of(
                new RuleViolation("app-id-is-route-safe", "App id is route-safe",
                        com.processpuzzle.rule.domain.Severity.ERROR, "An app id is lowercase.", "rule.app.id"),
                new RuleViolation("sidenav-is-populated", "Sidenav is populated",
                        com.processpuzzle.rule.domain.Severity.WARNING, "No sidenav navigation.", null))));

        List<RuleEvaluator.Violation> violations = configuration.ruleEvaluator(evaluateObject)
                .evaluate("my-org", "App Definition", Map.of());

        assertThat(violations).containsExactly(
                new RuleEvaluator.Violation("app-id-is-route-safe", "An app id is lowercase.",
                        "rule.app.id", Severity.ERROR),
                new RuleEvaluator.Violation("sidenav-is-populated", "No sidenav navigation.",
                        null, Severity.WARNING));
    }

    /** A violation without a severity is treated as blocking: the safe direction for a governance check. */
    @Test
    void aViolationWithoutASeverityIsAnError() {
        EvaluateObject evaluateObject = mock(EvaluateObject.class);
        when(evaluateObject.execute(any(), any(), any())).thenReturn(new EvaluationOutcome(false,
                List.of(new RuleViolation("r", "R", null, "Broken.", null))));

        assertThat(configuration.ruleEvaluator(evaluateObject).evaluate("my-org", "App Definition", Map.of()))
                .singleElement()
                .satisfies(violation -> assertThat(violation.severity()).isEqualTo(Severity.ERROR));
    }

    @Test
    void anOutcomeCarryingNoViolationsIsAnEmptyList() {
        EvaluateObject evaluateObject = mock(EvaluateObject.class);

        when(evaluateObject.execute(any(), any(), any())).thenReturn(new EvaluationOutcome(true, null));
        assertThat(configuration.ruleEvaluator(evaluateObject).evaluate("my-org", "c", Map.of())).isEmpty();

        when(evaluateObject.execute(any(), any(), any())).thenReturn(null);
        assertThat(configuration.ruleEvaluator(evaluateObject).evaluate("my-org", "c", Map.of())).isEmpty();
    }

    /** The tenant's own rules, and only its own: one tenant's expressions never judge another's app. */
    @Test
    void theRuleEvaluatorPassesTheTenantAndContextThrough() {
        EvaluateObject evaluateObject = mock(EvaluateObject.class);
        when(evaluateObject.execute("my-org", "App Definition", Map.of("id", "claims-app")))
                .thenReturn(new EvaluationOutcome(true, List.of()));

        assertThat(configuration.ruleEvaluator(evaluateObject)
                .evaluate("my-org", "App Definition", Map.of("id", "claims-app"))).isEmpty();
    }
}
