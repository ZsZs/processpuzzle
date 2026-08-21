package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whole-document replace of a state machine's topology. Optimistic locking is Hibernate's own
 * {@code @Version} column, detected on flush against the entity loaded by this method — the same
 * shape as {@code UpdateDocument} — rather than a client-supplied version compared in code.
 */
@Service
@Transactional
public class UpdateStateMachineDefinition {

    private final StateMachineDefinitionRepository repository;
    private final StateMachineTopologyValidator validator;

    public UpdateStateMachineDefinition(StateMachineDefinitionRepository repository,
                                        StateMachineTopologyValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public StateMachineDefinition execute(String orgKey, String entityName, StateMachineDefinition updated) {
        StateMachineDefinition definition = repository.findByOrgKeyAndEntityName(orgKey, entityName)
                .orElseThrow(() -> new StateMachineNotFoundException(orgKey, entityName));
        validator.validate(updated.getInitialStateKey(), updated.getStates(), updated.getTransitions());

        definition.replaceTopology(
                updated.getName(), updated.getDescription(), updated.getStateAttributeKey(),
                updated.getInitialStateKey(), updated.getStates(), updated.getTransitions());
        return repository.save(definition);
    }
}
