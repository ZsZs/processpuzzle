package com.processpuzzle.workflow.definition.adapters.outbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.state.domain.State;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.workflow.definition.usecases.outbound.StateMachineCatalogPort;
import java.util.Optional;

/**
 * Reads base-state's draft machines for the release's state-coverage check, through {@code state :: domain} —
 * the dependency this module already declares.
 *
 * <p>The draft, not the released machine: base-state releases before base-workflow in the same release, so the
 * draft is what the workflow will run against once the release has gone through.
 */
public class BaseStateMachineCatalogAdapter implements StateMachineCatalogPort {

    private final StateMachineDefinitionRepository machines;

    public BaseStateMachineCatalogAdapter(StateMachineDefinitionRepository machines) {
        this.machines = machines;
    }

    @Override
    public Optional<Machine> draftOf(String orgKey, String stateMachineId) {
        if (stateMachineId == null || stateMachineId.isBlank()) {
            return Optional.empty();
        }
        return machines.findByOrgKeyAndEntityNameAndChannel(orgKey, stateMachineId, Channel.DRAFT)
                .map(machine -> new Machine(machine.getEntityName(),
                        machine.getStates().stream().map(State::key).toList(),
                        machine.getTransitions().stream()
                                .map(transition -> new Transition(transition.key(), transition.name(),
                                        transition.sourceStateKey(), transition.targetStateKey()))
                                .toList()));
    }
}
