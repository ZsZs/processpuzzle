package com.processpuzzle.event.usecase;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.usecase.exception.EventDefinitionAlreadyExistsException;
import com.processpuzzle.event.usecase.exception.InvalidEventDefinitionException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateEventDefinition {

    private final EventDefinitionRepository repository;

    public CreateEventDefinition(EventDefinitionRepository repository) {
        this.repository = repository;
    }

    public EventDefinition create(String orgKey, EventDefinition definition) {
        definition.setOrgKey(orgKey);
        definition.setVersion(null);
        definition.setChannel(Channel.DRAFT);
        List<String> errors = definition.validate();
        if (!errors.isEmpty()) {
            throw new InvalidEventDefinitionException(definition.getId(), errors);
        }
        if (repository.existsByOrgKeyAndIdAndChannel(orgKey, definition.getId(), Channel.DRAFT)) {
            throw new EventDefinitionAlreadyExistsException(orgKey, definition.getId());
        }
        return repository.save(definition);
    }
}
