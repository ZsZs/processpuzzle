package com.processpuzzle.state.domain;

import java.util.Map;

/**
 * A single, flat, mutually exclusive value of a {@link StateMachineDefinition}'s state attribute.
 * No parallel or nested states in this version — see {@link StateMachineDefinition}.
 *
 * <p>Persisted as an element of {@link StateMachineDefinition#getStates()}, JSON-serialized via
 * {@link StatesConverter} rather than mapped as its own JPA entity: states have no identity or
 * lifecycle independent of the state machine that declares them, and a whole-document replace
 * (see {@code UpdateStateMachineDefinition}) is the only way they ever change.
 *
 * @param key         Unique within the state machine; the literal value written to the entity
 *                    object's state attribute.
 * @param name        Display name.
 * @param description Optional longer description.
 * @param isFinal     No {@link Transition} may declare this state as its {@code sourceStateKey} —
 *                    enforced by {@code StateMachineTopologyValidator}.
 * @param isLocked    When {@code true}, only the state attribute itself may change on an
 *                    {@code EntityObject} sitting in this state — every other attribute update is
 *                    expected to be rejected while the object is in this state. All-or-nothing for
 *                    now; an attribute-level allow-list is a deliberate follow-up, same as
 *                    parallel/nested states. Enforcing this is outside {@code base-state}'s own
 *                    write path — it constrains base-entity's operation-layer update, which does
 *                    not exist in code yet — so today this flag is honored only by
 *                    {@code EntityObjectGateway} implementations that choose to read it.
 * @param metadata    UI hints only — e.g. color, icon — for rendering the state machine graph.
 *                    Opaque to base-state-backend.
 */
public record State(
        String key,
        String name,
        String description,
        boolean isFinal,
        boolean isLocked,
        Map<String, Object> metadata
) {

    public State {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("State.key must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("State.name must not be blank");
        }
    }
}
