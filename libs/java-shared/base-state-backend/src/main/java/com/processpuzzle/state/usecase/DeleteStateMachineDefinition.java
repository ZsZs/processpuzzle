package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a state machine definition. Existing {@code EntityObject}s keep whatever value is
 * already stored in their state attribute — base-state simply stops governing further changes to
 * it, per base-state-api.yaml's note on this endpoint.
 */
@Service
@Transactional
public class DeleteStateMachineDefinition {

    private final StateMachineDefinitionRepository repository;

    public DeleteStateMachineDefinition(StateMachineDefinitionRepository repository) {
        this.repository = repository;
    }

    public void execute(String orgKey, String entityName) {
        if (!repository.existsByOrgKeyAndEntityName(orgKey, entityName)) {
            throw new StateMachineNotFoundException(orgKey, entityName);
        }
        repository.deleteById(new com.processpuzzle.state.domain.StateMachineDefinitionKey(orgKey, entityName));
    }
}
