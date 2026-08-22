package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.DiagramDefinitionKey;
import com.processpuzzle.state.domain.DiagramDefinitionRepository;
import com.processpuzzle.state.usecase.exception.DiagramDefinitionNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Discards a diagram layout, resetting the machine to an automatic one. The state machine
 * definition itself is untouched — and deleting <em>that</em> discards its layout too (see
 * {@link DeleteStateMachineDefinition}), so this use case serves the "re-arrange from scratch"
 * gesture rather than cleanup.
 */
@Service
@Transactional
public class DeleteDiagramDefinition {

    private final DiagramDefinitionRepository repository;

    public DeleteDiagramDefinition(DiagramDefinitionRepository repository) {
        this.repository = repository;
    }

    public void execute(String orgKey, String entityName) {
        if (!repository.existsByOrgKeyAndEntityName(orgKey, entityName)) {
            throw new DiagramDefinitionNotFoundException(orgKey, entityName);
        }
        repository.deleteById(new DiagramDefinitionKey(orgKey, entityName));
    }
}
