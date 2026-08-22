package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.StateMachineDefinition;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.exception.StateMachineAlreadyExistsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateStateMachineDefinition {

    private final StateMachineDefinitionRepository repository;
    private final StateMachineTopologyValidator validator;

    public CreateStateMachineDefinition(StateMachineDefinitionRepository repository,
                                        StateMachineTopologyValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    /**
     * An explicit existence check rather than relying on {@code save()}: with an assigned
     * composite id, {@code save()} merges, so a create would silently overwrite instead of
     * conflicting — same reasoning as {@code CreateRule}.
     */
    public StateMachineDefinition execute(StateMachineDefinition definition) {
        if (repository.existsByOrgKeyAndEntityName(definition.getOrgKey(), definition.getEntityName())) {
            throw new StateMachineAlreadyExistsException(definition.getOrgKey(), definition.getEntityName());
        }
        validator.validate(definition.getEntityName(), definition.getStateAttributeKey(),
                definition.getInitialStateKey(), definition.getStates(), definition.getTransitions());

        return repository.save(definition);
    }
}
