package com.processpuzzle.event.usecase;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.usecase.exception.EventDefinitionNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a definition. Not refused while a workflow names it: base-event does not know its
 * subscribers, and a start event naming a deleted definition simply stops firing.
 */
@Service
@Transactional
public class DeleteEventDefinition {

    private final EventDefinitionRepository repository;

    public DeleteEventDefinition(EventDefinitionRepository repository) {
        this.repository = repository;
    }

    public void delete(String orgKey, String id) {
        EventDefinition existing = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new EventDefinitionNotFoundException(orgKey, id));
        repository.delete(existing);
    }
}
