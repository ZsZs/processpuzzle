package com.processpuzzle.state.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published post-commit when an EntityObject transitions to a new state.
 *
 * @param orgKey            the owning organization
 * @param entityName        the entity type
 * @param objectId          the object id
 * @param previousStateKey  the state before transition
 * @param newStateKey       the state after transition
 * @param transitionKey     the transition key that was executed
 * @param triggerKey        the trigger invoked
 * @param version           the object version after the state update
 * @param occurredAt        timestamp of the state change
 */
public record EntityObjectStateChangedEvent(
        String orgKey,
        String entityName,
        UUID objectId,
        String previousStateKey,
        String newStateKey,
        String transitionKey,
        String triggerKey,
        long version,
        Instant occurredAt
) {
}
