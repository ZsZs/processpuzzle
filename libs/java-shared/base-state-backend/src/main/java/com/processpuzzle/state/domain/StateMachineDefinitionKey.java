package com.processpuzzle.state.domain;

import com.processpuzzle.core.definition.Channel;
import java.io.Serializable;

/**
 * Composite primary key of {@link StateMachineDefinition}: {@code entityName} is unique only
 * within its organization, mirroring {@code base-entity}'s {@code EntityDefinition.entityName}
 * and, structurally, {@code base-rule}'s {@code RuleDefinitionKey} — and within each channel,
 * since every machine has a draft and a released row. See {@link EntityScopedKey}.
 *
 * <p>Intentionally a plain mutable class and not a record — JPA requires an {@code @IdClass} to
 * be public, {@link Serializable}, and instantiable through a public no-arg constructor, which a
 * record cannot provide.
 */
public class StateMachineDefinitionKey extends EntityScopedKey {

    private static final long serialVersionUID = 2L;

    public StateMachineDefinitionKey() {
        // required by JPA
    }

    public StateMachineDefinitionKey(String orgKey, String entityName, Channel channel) {
        super(orgKey, entityName, channel);
    }
}
