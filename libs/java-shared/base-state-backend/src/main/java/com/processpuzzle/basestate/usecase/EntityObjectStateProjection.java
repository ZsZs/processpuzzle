package com.processpuzzle.basestate.usecase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The outcome of {@code GetEntityObjectState}: an object's current state plus the dry-run
 * transitions available from it.
 *
 * @param objectId              the object's id
 * @param entityName            the entity type
 * @param currentStateKey       the value currently held in the state machine's
 *                              {@code stateAttributeKey}
 * @param isFinal               whether {@code currentStateKey} resolves to a {@code State} with
 *                              {@code isFinal} true
 * @param availableTransitions  see {@link AvailableTransitionProjection}
 */
public record EntityObjectStateProjection(
        UUID objectId,
        String entityName,
        String currentStateKey,
        boolean isFinal,
        Instant enteredStateAt,
        List<AvailableTransitionProjection> availableTransitions
) {
}
