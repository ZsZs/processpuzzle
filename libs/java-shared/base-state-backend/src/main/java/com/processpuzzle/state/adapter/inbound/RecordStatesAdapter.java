package com.processpuzzle.state.adapter.inbound;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.definition.RecordStates;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Answers where a record's state is kept from the type's released state machine. */
@Component
@Transactional(readOnly = true)
public class RecordStatesAdapter implements RecordStates {

    private final StateMachineDefinitionRepository repository;

    public RecordStatesAdapter(StateMachineDefinitionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<StateTracking> trackingOf(String orgKey, String entityDefinitionCode) {
        return repository.findByOrgKeyAndEntityNameAndChannel(orgKey, entityDefinitionCode, Channel.RELEASED)
            .map(machine -> new StateTracking(machine.getStateAttributeKey(), machine.getInitialStateKey()));
    }
}
