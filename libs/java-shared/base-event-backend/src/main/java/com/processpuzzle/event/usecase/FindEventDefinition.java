package com.processpuzzle.event.usecase;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.usecase.exception.EventDefinitionNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindEventDefinition {

    private final EventDefinitionRepository repository;

    public FindEventDefinition(EventDefinitionRepository repository) {
        this.repository = repository;
    }

    public EventDefinition find(String orgKey, String id) {
        return repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new EventDefinitionNotFoundException(orgKey, id));
    }

    public boolean exists(String orgKey, String id) {
        return id != null && repository.existsByOrgKeyAndId(orgKey, id);
    }

    public List<EventDefinition> findAll(String orgKey) {
        return repository.findByOrgKeyOrderByIdAsc(orgKey);
    }
}
