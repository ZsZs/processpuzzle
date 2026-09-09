package com.processpuzzle.state.usecase;

import com.processpuzzle.state.domain.DiagramDefinition;
import com.processpuzzle.state.domain.DiagramDefinitionRepository;
import com.processpuzzle.state.usecase.exception.DiagramDefinitionNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the diagram layout of one entity type's state machine.
 *
 * <p>Throws rather than answering an empty layout when nothing has been arranged yet: the modeler
 * acts on the difference — it falls back to an automatic layout — so "never arranged" has to stay
 * distinguishable from "arranged, then emptied". This is the opposite call from
 * {@code StateTranslationEndpoint}'s, where the loader cannot act on the difference and an
 * unseeded bundle is therefore a {@code 200} with an empty object.
 */
@Service
@Transactional(readOnly = true)
public class FindDiagramDefinition {

    private final DiagramDefinitionRepository repository;

    public FindDiagramDefinition(DiagramDefinitionRepository repository) {
        this.repository = repository;
    }

    public DiagramDefinition execute(String orgKey, String entityName) {
        return repository.findByOrgKeyAndEntityName(orgKey, entityName)
                .orElseThrow(() -> new DiagramDefinitionNotFoundException(orgKey, entityName));
    }
}
