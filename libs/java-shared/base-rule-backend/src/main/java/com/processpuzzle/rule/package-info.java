/**
 * Base Rule: authoring and evaluation of business rules. Interprets persisted {@code RuleDefinition}
 * records — expression, context and severity — so that adding a business rule is a database row
 * rather than a release.
 *
 * <p>What other modules may use is limited to two named interfaces:
 *
 * <ul>
 *   <li>{@code rule :: usecase} — {@link com.processpuzzle.rule.usecase.EvaluateObject} and the
 *       result types it returns. The evaluation entry point for any feature that wants a candidate
 *       object checked against an organization's rules.
 *   <li>{@code rule :: domain} — {@link com.processpuzzle.rule.domain.Severity} alone, because
 *       {@link com.processpuzzle.rule.usecase.RuleViolation} exposes it. The repository and the
 *       entity stay internal.
 * </ul>
 *
 * <p>The rule engine itself ({@code usecase.engine}) is deliberately not exposed: callers evaluate
 * through the use case, which resolves and registers the organization's rules for them.
 */
@ApplicationModule(displayName = "Base Rule", allowedDependencies = {"core", "shared"})
package com.processpuzzle.rule;

import org.springframework.modulith.ApplicationModule;
