package com.processpuzzle.workflow.definition.usecases.outbound;

import java.util.Optional;

/** Knows no state machine — used when base-state is not deployed, so the release has no transition to check. */
public class NoStateMachineCatalogPort implements StateMachineCatalogPort {

    @Override
    public Optional<Machine> draftOf(String orgKey, String stateMachineId) {
        return Optional.empty();
    }
}
