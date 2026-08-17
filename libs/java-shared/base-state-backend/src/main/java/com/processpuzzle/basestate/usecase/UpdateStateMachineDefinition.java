package com.processpuzzle.basestate.usecase;

import com.processpuzzle.basestate.domain.State;
import com.processpuzzle.basestate.domain.StateMachineDefinition;
import com.processpuzzle.basestate.domain.StateMachineDefinitionRepository;
import com.processpuzzle.basestate.domain.Transition;
import com.processpuzzle.basestate.usecase.exception.StateMachineNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public StateMachineDefinition execute(String orgKey, String entityName, String name, String description,
                                          String stateAttributeKey, String initialStateKey,
                                          List<State> states, List<Transition> transitions) {
        StateMachineDefinition definition = repository.findByOrgKeyAndEntityName(orgKey, entityName)
                .orElseThrow(() -> new StateMachineNotFoundException(orgKey, entityName));
        validator.validate(initialStateKey, states, transitions);

        definition.replaceTopology(name, description, stateAttributeKey, initialStateKey, states, transitions);
        return repository.save(definition);
    }
}
