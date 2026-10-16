package com.processpuzzle.workflow.definition.usecases.outbound;

import java.util.List;
import java.util.Optional;

/**
 * Reads the state machine an artifact's lifecycle follows — its draft, the one a release is about to publish —
 * so the release can tell whether a workflow handles every transition its tasks leave open.
 *
 * <p>The machines are base-state's. {@code BaseStateMachineCatalogAdapter} answers from base-state's draft
 * definitions when that library is deployed; without it there is no machine to read, and the check has nothing to
 * check.
 */
public interface StateMachineCatalogPort {

    /** The draft machine keyed {@code stateMachineId} — base-state keys its machines by entity name. */
    Optional<Machine> draftOf(String orgKey, String stateMachineId);

    /** A state machine as far as the check reads one: its entity, its state keys and its transitions. */
    record Machine(String entityName, List<String> stateKeys, List<Transition> transitions) {

        public Machine {
            stateKeys = stateKeys == null ? List.of() : List.copyOf(stateKeys);
            transitions = transitions == null ? List.of() : List.copyOf(transitions);
        }

        public boolean hasState(String stateKey) {
            return stateKeys.stream().anyMatch(key -> key.equalsIgnoreCase(stateKey));
        }

        public List<Transition> transitionsFrom(String stateKey) {
            return transitions.stream().filter(transition -> transition.sourceStateKey().equalsIgnoreCase(stateKey)).toList();
        }
    }

    /** One transition, named for the designer reading the release problem. */
    record Transition(String key, String name, String sourceStateKey, String targetStateKey) {}
}
