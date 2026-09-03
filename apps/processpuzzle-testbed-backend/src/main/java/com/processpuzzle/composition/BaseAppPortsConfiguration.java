package com.processpuzzle.composition;

import com.processpuzzle.app.usecase.port.RuleEvaluator;
import com.processpuzzle.app.usecase.port.TenantDirectory;
import com.processpuzzle.platformadmin.domain.OrganizationRepository;
import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.EvaluationOutcome;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wires base-app's outbound ports to the features that answer them.
 *
 * <p><b>This is the composition root, and that is the whole point.</b> base-app declares what it
 * needs — does this tenant exist, what is its locale, what do this tenant's governance rules say —
 * without naming platform-admin or base-rule. Those two modules never learn that base-app exists.
 * The knowledge that all three are deployed together lives here, in the application, which is the
 * only place that legitimately knows it.
 *
 * <p>Both adapters used to be direct calls inside base-app: an injected
 * {@code OrganizationRepository} and an injected {@code EvaluateObject}. That made base-app
 * undeployable without both modules on the classpath, and made every {@code existsById} a call that
 * would silently become a cross-service round-trip the day these Modulith modules were split. Behind
 * a port, splitting them is a change to this file — swap each adapter for one built on
 * {@code RestClient} — and nothing in any feature library moves.
 *
 * <p>Neither adapter is conditional. This application composes the full platform, so both providers
 * are always present; an application that omitted one would simply not declare that bean, and
 * base-app would fall back to the port's permissive default.
 */
@Configuration
public class BaseAppPortsConfiguration {

    /**
     * Answers base-app's tenant questions from platform-admin's own repository.
     *
     * <p>The projection is narrow on purpose — an {@code orgKey} and a locale, not the aggregate.
     * Handing base-app an {@code Organization} would put platform-admin's type back in its signature
     * and undo the separation, and base-app has no use for a status, a contact address or a
     * subscription.
     */
    @Bean
    public TenantDirectory tenantDirectory(OrganizationRepository organizations) {
        return new TenantDirectory() {
            @Override
            public boolean exists(String orgKey) {
                return organizations.existsById(orgKey);
            }

            @Override
            public Optional<Tenant> find(String orgKey) {
                return organizations.findById(orgKey)
                        .map(organization -> new Tenant(
                                organization.getKey(), organization.getDefaultLocale()));
            }
        };
    }

    /**
     * Answers base-app's rule questions from base-rule's {@code usecase} named interface.
     *
     * <p>The severity is translated by name across two structurally identical enums. That is not
     * ceremony: each feature owns its own scale, so a constant added on one side shows up here as a
     * failure to map rather than as a compile-time coupling nobody chose.
     */
    @Bean
    public RuleEvaluator ruleEvaluator(EvaluateObject evaluateObject) {
        return new RuleEvaluator() {
            @Override
            public List<Violation> evaluate(String orgKey, String context, Map<String, Object> candidate) {
                return toViolations(evaluateObject.execute(orgKey, context, candidate));
            }
        };
    }

    private static List<RuleEvaluator.Violation> toViolations(EvaluationOutcome outcome) {
        if (outcome == null || outcome.violations() == null) {
            return List.of();
        }
        return outcome.violations().stream()
                .map(violation -> new RuleEvaluator.Violation(
                        violation.ruleId(),
                        violation.message(),
                        violation.translocoId(),
                        violation.severity() == null
                                ? com.processpuzzle.app.usecase.Severity.ERROR
                                : com.processpuzzle.app.usecase.Severity.valueOf(violation.severity().name())))
                .toList();
    }
}
