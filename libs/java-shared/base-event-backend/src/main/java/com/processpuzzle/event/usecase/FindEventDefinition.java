package com.processpuzzle.event.usecase;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.usecase.exception.EventDefinitionNotFoundException;
import com.processpuzzle.shared.event.CatalogEventKind;
import java.util.List;
import java.util.Optional;
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

    /** The definition, or empty when the organization's catalog has none of that id. */
    public Optional<EventDefinition> lookup(String orgKey, String id) {
        return id == null ? Optional.empty() : repository.findByOrgKeyAndId(orgKey, id);
    }

    /**
     * The definition's kind as the shared contract names it, or empty when there is no such definition —
     * what a module that may not see this one's domain asks.
     */
    public Optional<CatalogEventKind> kindOf(String orgKey, String id) {
        return lookup(orgKey, id).map(definition -> CatalogEventKind.valueOf(definition.getKind().name()));
    }

    public boolean exists(String orgKey, String id) {
        return id != null && repository.existsByOrgKeyAndId(orgKey, id);
    }

    public List<EventDefinition> findAll(String orgKey) {
        return repository.findByOrgKeyOrderByIdAsc(orgKey);
    }
}
