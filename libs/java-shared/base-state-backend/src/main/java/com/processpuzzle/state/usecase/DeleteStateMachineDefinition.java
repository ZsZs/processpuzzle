package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.DiagramDefinitionRepository;
import com.processpuzzle.state.domain.StateMachineDefinitionRepository;
import com.processpuzzle.state.usecase.exception.StateMachineNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a state machine definition. Existing {@code EntityObject}s keep whatever value is
 * already stored in their state attribute — base-state simply stops governing further changes to
 * it, per base-state-api.yaml's note on this endpoint.
 *
 * <p>The machine's diagram layout goes with it. That is a genuine cascade rather than tidiness:
 * {@link SaveDiagramDefinition} refuses to write a layout for an {@code entityName} that has no
 * state machine, so a layout left behind here could be read and deleted but never updated.
 */
@Service
@Transactional
public class DeleteStateMachineDefinition {

    private final StateMachineDefinitionRepository repository;
    private final DiagramDefinitionRepository diagramRepository;

    public DeleteStateMachineDefinition(StateMachineDefinitionRepository repository,
                                        DiagramDefinitionRepository diagramRepository) {
        this.repository = repository;
        this.diagramRepository = diagramRepository;
    }

    public void execute(String orgKey, String entityName) {
        if (!repository.existsByOrgKeyAndEntityName(orgKey, entityName)) {
            throw new StateMachineNotFoundException(orgKey, entityName);
        }
        diagramRepository.findByOrgKeyAndEntityName(orgKey, entityName).ifPresent(diagramRepository::delete);
        repository.deleteById(new com.processpuzzle.state.domain.StateMachineDefinitionKey(orgKey, entityName));
    }
}
