package com.processpuzzle.event.usecase;

import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.usecase.exception.EventDefinitionAlreadyExistsException;
import com.processpuzzle.event.usecase.exception.InvalidEventDefinitionException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateEventDefinition {

    private final EventDefinitionRepository repository;

    private final EventDefinitionValidator validator;

    @Autowired
    public CreateEventDefinition(EventDefinitionRepository repository, EventDefinitionValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    /** Without an entity catalog to check parameters against. */
    public CreateEventDefinition(EventDefinitionRepository repository) {
        this(repository, new EventDefinitionValidator(SubjectAttributePort.UNKNOWN));
    }

    public EventDefinition create(String orgKey, EventDefinition definition) {
        definition.setOrgKey(orgKey);
        definition.setVersion(null);
        definition.setChannel(Channel.DRAFT);
        List<String> errors = validator.validate(definition);
        if (!errors.isEmpty()) {
            throw new InvalidEventDefinitionException(definition.getId(), errors);
        }
        if (repository.existsByOrgKeyAndIdAndChannel(orgKey, definition.getId(), Channel.DRAFT)) {
            throw new EventDefinitionAlreadyExistsException(orgKey, definition.getId());
        }
        return repository.save(definition);
    }
}
