package com.processpuzzle.rule.port;

import java.util.Optional;

/**
 * The entity a rule is written for, as the rule's {@code entity} shows it: which attributes it has and of what kind.
 * What the authoring assist tells the model a rule may refer to, and what it checks a candidate against.
 */
public interface RuleContextDirectory {

    /** Knows no entity; what a host without an entity catalog gets, so the assist can only check a rule's syntax. */
    RuleContextDirectory UNKNOWN = (orgKey, context) -> Optional.empty();

    /**
     * The entity a rule's {@code context} names — its display name — in the organization, with embedded components
     * already expanded. Empty for an unknown entity.
     */
    Optional<RuleContext> find(String orgKey, String context);
}
