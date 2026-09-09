package com.processpuzzle.app.usecase.port;

import com.processpuzzle.app.usecase.Severity;

import java.util.List;
import java.util.Map;

/**
 * Outbound port evaluating an organization's own governance rules against a candidate app definition.
 *
 * <p>This is what makes governance of an app definition configuration rather than code: the
 * conventions {@code AppDefinitionValidator} deliberately leaves out — id shapes, navigation depth,
 * role naming, CSS units, translatability — are expressions a tenant can add, disable or override
 * without a release.
 *
 * <p><b>Stated as a port because base-rule is a separate feature.</b> {@code AppRuleValidator} used
 * to call {@code rule.usecase.EvaluateObject} directly, which cost base-app a compile dependency on
 * base-rule-backend for one method call and an enum. The adapter that answers this port lives in the
 * composition root, next to the {@code TenantDirectory} one, so base-app is deployable with or
 * without a rule engine and neither library knows the other exists.
 *
 * <p>Unimplemented, the port reports no violations. A host that wires Base App without Base Rule
 * keeps the structural checks and silently skips the rule pass rather than failing to start — the
 * same direction as {@link EntityNameRegistry} and {@link TenantDirectory}.
 *
 * @see Violation
 */
public interface RuleEvaluator {

    /**
     * Evaluates {@code orgKey}'s enabled rules for {@code context} against {@code candidate}.
     *
     * @param orgKey the tenant whose rules apply; one tenant's expressions never judge another's app
     * @param context the rule context to select, an entity name in the designer's own vocabulary
     * @param candidate the app definition as plain JSON, exactly the shape it has over REST
     * @return one violation per broken rule, empty when the definition satisfies all of them
     */
    default List<Violation> evaluate(String orgKey, String context, Map<String, Object> candidate) {
        return List.of();
    }

    /**
     * One broken rule, in base-app's own vocabulary.
     *
     * <p>Deliberately not base-rule's {@code RuleViolation}: a port that spoke the provider's types
     * would leave the compile dependency exactly where it was. The adapter translates, which is what
     * it would have to do anyway if base-rule answered over HTTP.
     *
     * @param ruleId identifier of the rule that was broken
     * @param message human-readable explanation in the service's default language
     * @param translocoId the rule author's own Transloco key, or {@code null}
     * @param severity whether the violation blocks the write
     */
    record Violation(String ruleId, String message, String translocoId, Severity severity) {
    }
}
