package com.processpuzzle.composition;

import com.processpuzzle.app.usecase.port.RuleEvaluator;
import com.processpuzzle.rule.usecase.EvaluateObject;
import com.processpuzzle.rule.usecase.EvaluationOutcome;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Wires base-app's outbound ports to the features that answer them.
 *
 * <p><b>This is the composition root, and that is the whole point.</b> base-app declares what it
 * needs — what do this tenant's governance rules say — without naming base-rule, which never learns
 * that base-app exists. The knowledge that both are deployed together lives here, in the
 * application, which is the only place that legitimately knows it.
 *
 * <p>The adapter used to be a direct call inside base-app: an injected {@code EvaluateObject}. That
 * made base-app undeployable without base-rule on the classpath, and made every evaluation a call
 * that would silently become a cross-service round-trip the day these Modulith modules were split.
 * Behind a port, splitting them is a change to this file — swap the adapter for one built on
 * {@code RestClient} — and nothing in any feature library moves.
 *
 * <h2>There was a second adapter, and its absence is deliberate</h2>
 *
 * <p>{@code TenantDirectory} was wired here too, over platform-admin's {@code OrganizationRepository},
 * answering "does this tenant exist" and "what is its locale". It is gone because platform-admin is
 * leaving for a private repository and this application must not name it — see
 * {@code docs/platform-admin-extraction.md}. The <em>port</em> remains in base-app, with its
 * permissive defaults; only this adapter went.
 *
 * <p>So in this deployment base-app no longer refuses an unknown {@code orgKey} on create, import or
 * module creation, and {@code GetAppLayout} reports no tenant locale. Neither is a hole:
 * {@code OrganizationGuard} already gates those endpoints, and with realm name equal to organization
 * key a caller can only write into its own tenant — the existence check was redundant once
 * membership had been established. An application deployed beside a tenant registry, which
 * {@code processpuzzle-admin-backend} will be, can declare the bean again and get both behaviours
 * back without any change to base-app.
 *
 * <p>The remaining adapter is not conditional. This application composes base-rule, so the provider
 * is always present; an application that omitted it would simply not declare the bean, and base-app
 * would fall back to the port's permissive default.
 */
@Configuration
public class BaseAppPortsConfiguration {

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
