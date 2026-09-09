package com.processpuzzle.state.usecase;

import com.processpuzzle.baseentity.api.EntityAttributeKind;
import com.processpuzzle.baseentity.api.EntityAttributeQuery;
import com.processpuzzle.state.domain.ActionRef;
import com.processpuzzle.state.domain.GuardRef;
import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.Transition;
import com.processpuzzle.state.usecase.service.GuardActionResolver;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates a candidate {@code states}/{@code transitions} topology before it is persisted, the
 * knowledge-layer counterpart of {@code RuleExtendsValidator}. Every rejection is an {@link
 * IllegalArgumentException}, which core's generic {@code ApiExceptionHandler} already maps to
 * {@code 400} — no feature-specific exception type needed, since these are all structurally
 * invalid requests rather than named business refusals.
 *
 * <p>Also the place the "only base-entity-managed entity types may have a state machine"
 * restriction is actually enforced: {@code entityName} must be an entity definition base-entity
 * knows, and {@code stateAttributeKey} must name a TEXT- or ENUM-valued attribute of it. Checking
 * here rather than at write time is what turns a typo, or an attribute removed from the definition
 * since, into a 400 on the save that introduced it instead of a machine that fails the first time
 * it is fired.
 */
@Component
public class StateMachineTopologyValidator {

    /**
     * The kinds a state attribute may have. ENUM is the normal choice and what both seeded machines
     * use; TEXT is allowed because a definition may legitimately hold the state as free text before
     * its enumeration is settled. Everything else — a number, a date, a reference — cannot hold a
     * state key without lying about its own type.
     */
    private static final Set<EntityAttributeKind> STATE_ATTRIBUTE_KINDS =
            EnumSet.of(EntityAttributeKind.TEXT, EntityAttributeKind.ENUM);

    private final GuardActionResolver guardActionResolver;
    private final EntityAttributeQuery entityAttributeQuery;

    public StateMachineTopologyValidator(GuardActionResolver guardActionResolver,
                                         EntityAttributeQuery entityAttributeQuery) {
        this.guardActionResolver = guardActionResolver;
        this.entityAttributeQuery = entityAttributeQuery;
    }

    public void validate(String entityName, String stateAttributeKey, String initialStateKey,
                         List<State> states, List<Transition> transitions) {
        validateStateAttribute(entityName, stateAttributeKey);
        Set<String> stateKeys = uniqueStateKeys(states);
        requireKnownState(initialStateKey, stateKeys, "initialStateKey");
        validateTransitions(states, stateKeys, transitions);
    }

    private void validateStateAttribute(String entityName, String stateAttributeKey) {
        if (entityName == null || entityName.isBlank()) {
            throw new IllegalArgumentException("entityName is required");
        }
        if (stateAttributeKey == null || stateAttributeKey.isBlank()) {
            throw new IllegalArgumentException("stateAttributeKey is required");
        }
        if (!entityAttributeQuery.entityTypeExists(entityName)) {
            throw new IllegalArgumentException(
                    "entityName '" + entityName + "' is not an entity type base-entity manages, so it cannot "
                            + "have a state machine");
        }

        EntityAttributeKind kind = entityAttributeQuery.attributeKind(entityName, stateAttributeKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "stateAttributeKey '" + stateAttributeKey + "' is not an attribute of '" + entityName + "'"));

        if (!STATE_ATTRIBUTE_KINDS.contains(kind)) {
            throw new IllegalArgumentException(
                    "stateAttributeKey '" + stateAttributeKey + "' on '" + entityName + "' is " + kind
                            + "; a state attribute must be TEXT or ENUM");
        }
    }

    private void validateTransitions(List<State> states, Set<String> stateKeys, List<Transition> transitions) {
        if (transitions == null) {
            return;
        }
        Set<String> transitionKeys = new HashSet<>();
        Set<String> sourceTriggerPairs = new HashSet<>();
        for (Transition transition : transitions) {
            validateTransition(transition, stateKeys, states, transitionKeys, sourceTriggerPairs);
        }
    }

    private void validateTransition(Transition transition, Set<String> stateKeys, List<State> states,
                                    Set<String> transitionKeys, Set<String> sourceTriggerPairs) {
        if (!transitionKeys.add(transition.key())) {
            throw new IllegalArgumentException("Duplicate transition key: '" + transition.key() + "'");
        }
        requireKnownState(transition.sourceStateKey(), stateKeys, "transition '" + transition.key() + "'.sourceStateKey");
        requireKnownState(transition.targetStateKey(), stateKeys, "transition '" + transition.key() + "'.targetStateKey");

        State source = stateOf(states, transition.sourceStateKey());
        if (source.isFinal()) {
            throw new IllegalArgumentException(
                    "Transition '" + transition.key() + "' sources from final state '" + source.key() + "'");
        }

        String pair = transition.sourceStateKey() + "::" + transition.triggerKey();
        if (!sourceTriggerPairs.add(pair)) {
            throw new IllegalArgumentException(
                    "Ambiguous trigger: two transitions from state '" + transition.sourceStateKey()
                            + "' share the trigger '" + transition.triggerKey() + "'");
        }

        validateGuards(transition);
        validateActions(transition);
    }

    private void validateGuards(Transition transition) {
        for (GuardRef guard : transition.guards()) {
            if (!guardActionResolver.isKnownGuard(guard.beanName())) {
                throw new IllegalArgumentException(
                        "Transition '" + transition.key() + "' references unknown guard bean '" + guard.beanName() + "'");
            }
        }
    }

    private void validateActions(Transition transition) {
        for (ActionRef action : transition.actions()) {
            if (!guardActionResolver.isKnownAction(action.beanName())) {
                throw new IllegalArgumentException(
                        "Transition '" + transition.key() + "' references unknown action bean '" + action.beanName() + "'");
            }
        }
    }

    private Set<String> uniqueStateKeys(List<State> states) {
        if (states == null || states.isEmpty()) {
            throw new IllegalArgumentException("A state machine must declare at least one state");
        }
        Set<String> keys = new HashSet<>();
        for (State state : states) {
            if (!keys.add(state.key())) {
                throw new IllegalArgumentException("Duplicate state key: '" + state.key() + "'");
            }
        }
        return keys;
    }

    private void requireKnownState(String key, Set<String> stateKeys, String fieldDescription) {
        if (!stateKeys.contains(key)) {
            throw new IllegalArgumentException(fieldDescription + " '" + key + "' does not resolve to a declared state");
        }
    }

    private State stateOf(List<State> states, String key) {
        return states.stream().filter(s -> s.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unreachable: '" + key + "' was already validated as known"));
    }
}
