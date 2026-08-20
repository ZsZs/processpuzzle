package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.ActionRef;
import com.processpuzzle.state.domain.GuardRef;
import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.Transition;
import com.processpuzzle.state.usecase.service.GuardActionResolver;
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
 */
@Component
public class StateMachineTopologyValidator {

    private final GuardActionResolver guardActionResolver;

    public StateMachineTopologyValidator(GuardActionResolver guardActionResolver) {
        this.guardActionResolver = guardActionResolver;
    }

    public void validate(String initialStateKey, List<State> states, List<Transition> transitions) {
        Set<String> stateKeys = uniqueStateKeys(states);
        requireKnownState(initialStateKey, stateKeys, "initialStateKey");
        validateTransitions(states, stateKeys, transitions);
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
