package com.processpuzzle.rule.domain;

import java.util.List;

/**
 * Builds a {@link RuleDefinition} in the state it would have <em>after</em> a flush, without
 * needing a persistence context. {@code @PrePersist} is package-private, so only a helper living
 * in this package can trigger it — which is why this sits here rather than next to its callers.
 */
public final class RuleDefinitionTestFactory {

    private RuleDefinitionTestFactory() {
    }

    /** A minimal rule whose {@code createdAt}/{@code updatedAt} are already stamped. */
    public static RuleDefinition persisted(String orgKey, String id) {
        RuleDefinition rule = new RuleDefinition(orgKey, id, id, null, "Order", "true",
                Severity.ERROR, null, null, null, false, true, List.of());
        rule.onCreate();
        return rule;
    }
}
