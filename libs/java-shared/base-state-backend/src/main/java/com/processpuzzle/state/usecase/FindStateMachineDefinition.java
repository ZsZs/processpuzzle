package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindStateMachineDefinition {

    private final StateMachineDefinitionRepository repository;

    public FindStateMachineDefinition(StateMachineDefinitionRepository repository) {
        this.repository = repository;
    }

    public StateMachineDefinition execute(String orgKey, String entityName) {
        return repository.findByOrgKeyAndEntityName(orgKey, entityName)
                .orElseThrow(() -> new StateMachineNotFoundException(orgKey, entityName));
    }
}
