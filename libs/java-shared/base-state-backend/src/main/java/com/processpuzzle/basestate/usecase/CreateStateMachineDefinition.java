package com.processpuzzle.basestate.usecase;

import com.processpuzzle.basestate.domain.State;
import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.domain.StateMachineDefinitionRepository;
import com.processpuzzle.basestate.domain.Transition;
import com.processpuzzle.basestate.usecase.exception.StateMachineAlreadyExistsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public StateMachineDefinition execute(String orgKey, String entityName, String name, String description,
                                          String stateAttributeKey, String initialStateKey,
                                          List<State> states, List<Transition> transitions) {
        if (repository.existsByOrgKeyAndEntityName(orgKey, entityName)) {
            throw new StateMachineAlreadyExistsException(orgKey, entityName);
        }
        validator.validate(initialStateKey, states, transitions);

        StateMachineDefinition definition = new StateMachineDefinition(
                orgKey, entityName, name, description, stateAttributeKey, initialStateKey, states, transitions);
        return repository.save(definition);
    }
}
