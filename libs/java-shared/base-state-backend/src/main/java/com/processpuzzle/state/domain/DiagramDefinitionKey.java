package com.processpuzzle.state.domain;

import com.processpuzzle.core.definition.Channel;
import java.io.Serializable;

/**
 * Composite primary key of {@link DiagramDefinition}, identical in shape to
 * {@link StateMachineDefinitionKey} because a diagram is addressed by exactly the same triple: the
 * layout of the state machine of {@code entityName}, within one organization and one channel.
 *
 * <p>A plain mutable class and not a record, for the same reason
 * {@link StateMachineDefinitionKey} is: JPA requires an {@code @IdClass} to be public,
 * {@link Serializable}, and instantiable through a public no-arg constructor.
 */
public class DiagramDefinitionKey extends EntityScopedKey {

    private static final long serialVersionUID = 2L;

    public DiagramDefinitionKey() {
        // required by JPA
    }

    public DiagramDefinitionKey(String orgKey, String entityName, Channel channel) {
        super(orgKey, entityName, channel);
    }
}
