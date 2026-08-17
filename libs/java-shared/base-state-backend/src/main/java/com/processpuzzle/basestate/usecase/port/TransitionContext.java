package com.processpuzzle.basestate.usecase.port;

import com.processpuzzle.basestate.domain.State;

import java.util.Map;
import java.util.UUID;

/**
 * Everything a {@link TransitionGuard} or {@link TransitionAction} bean sees when
 * {@code StateMachineEngine} evaluates or fires a transition.
 *
 * @param orgKey            the organization the object belongs to
 * @param entityObjectId    the object's id
 * @param entityName        the entity type
 * @param sourceState       the {@link State} the object is transitioning from
 * @param targetState       the {@link State} the object is transitioning to
 * @param entityObject      the object's current payload, before this transition is applied — see
 *                          {@link EntityObjectSnapshot}
 * @param guardParams       the firing {@link com.processpuzzle.basestate.domain.GuardRef#params()}
 *                          or {@link com.processpuzzle.basestate.domain.ActionRef#params()}, static
 *                          configuration declared on the transition itself
 * @param requestContext    caller-supplied data — e.g. an approval comment, or identifiers
 *                          base-workflow-backend passes through from the activity that triggered
 *                          this call; may be {@code null}
 */
public record TransitionContext(
        String orgKey,
        UUID entityObjectId,
        String entityName,
        State sourceState,
        State targetState,
        EntityObjectSnapshot entityObject,
        Map<String, Object> guardParams,
        Map<String, Object> requestContext
) {
}
