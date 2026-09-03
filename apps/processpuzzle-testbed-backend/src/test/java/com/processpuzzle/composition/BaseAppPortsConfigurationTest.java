package com.processpuzzle.composition;

import com.processpuzzle.app.usecase.Severity;
import com.processpuzzle.app.usecase.port.RuleEvaluator;
import com.processpuzzle.app.usecase.port.TenantDirectory;
import com.processpuzzle.platformadmin.domain.Organization;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.platformadmin.domain.OrganizationStatus;
import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.EvaluationOutcome;
import com.processpuzzle.rule.usecase.RuleViolation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The adapters that introduce base-app to platform-admin and base-rule.
 *
 * <p>They carry the only knowledge in the system that those three features are deployed together, so
 * the translation each performs is the seam that has to be right: base-app must receive its own
 * types and nothing more. A regression here would reintroduce, one field at a time, exactly the
 * coupling the ports removed.
 */
class BaseAppPortsConfigurationTest {

    private final BaseAppPortsConfiguration configuration = new BaseAppPortsConfiguration();

    @Test
    void theTenantDirectoryReportsWhetherAnOrganizationExists() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        when(organizations.existsById("my-org")).thenReturn(true);
        when(organizations.existsById("nope")).thenReturn(false);

        TenantDirectory directory = configuration.tenantDirectory(organizations);

        assertThat(directory.exists("my-org")).isTrue();
        assertThat(directory.exists("nope")).isFalse();
    }

    /**
     * The projection is two fields, not the aggregate. Handing base-app an {@code Organization} would
     * put platform-admin's type back into its signature and undo the separation; a status, a contact
     * address or a subscription is nothing base-app could use.
     */
    @Test
    void theTenantDirectoryProjectsOnlyTheKeyAndLocale() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        when(organizations.findById("my-org")).thenReturn(Optional.of(new Organization(
                "my-org", "My Organization Ltd.", "Insurance.", "ops@my-org.example", "en-GB",
                OrganizationStatus.ACTIVE)));

        Optional<TenantDirectory.Tenant> tenant = configuration.tenantDirectory(organizations).find("my-org");

        assertThat(tenant).contains(new TenantDirectory.Tenant("my-org", "en-GB"));
    }

    @Test
    void anUnknownTenantIsAbsentRatherThanNull() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        when(organizations.findById(anyString())).thenReturn(Optional.empty());

        assertThat(configuration.tenantDirectory(organizations).find("nope")).isEmpty();
    }

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
