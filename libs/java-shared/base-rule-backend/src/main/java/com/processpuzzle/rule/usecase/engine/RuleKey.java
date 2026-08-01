package com.processpuzzle.rule.usecase.engine;

import java.util.Objects;

/**
 * Identity of a compiled rule inside {@link RuleEngine}: the rule id <em>plus</em> the
 * organization that owns it.
 *
 * <p>The engine's caches were once keyed by the bare rule id, which silently shared compiled
 * expressions across tenants — if two organizations both defined {@code max-pages}, whichever
 * registered first won for both and the other evaluated a foreign expression. A dedicated value
 * type makes that collision unrepresentable rather than relying on every caller to remember to
 * concatenate.
 *
 * <p>Deliberately separate from {@code RuleDefinitionKey}: that one is a JPA {@code @IdClass} and
 * must be mutable with a no-arg constructor, which is exactly what a hash-map key should not be.
 */
public record RuleKey(String orgKey, String ruleId) {

    public RuleKey {
        Objects.requireNonNull(orgKey, "orgKey is required");
        Objects.requireNonNull(ruleId, "ruleId is required");
    }

    public static RuleKey of(String orgKey, String ruleId) {
        return new RuleKey(orgKey, ruleId);
    }

    /**
     * Name given to the GraalJS {@link org.graalvm.polyglot.Source}, so a failing expression
     * names its tenant in the stack trace.
     */
    public String asSourceName() {
        return orgKey + "/" + ruleId + ".js";
    }

    @Override
    public String toString() {
        return orgKey + "/" + ruleId;
    }
}
