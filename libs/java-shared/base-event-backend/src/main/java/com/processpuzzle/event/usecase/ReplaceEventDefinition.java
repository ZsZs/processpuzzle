package com.processpuzzle.event.usecase;

import com.processpuzzle.event.domain.EventDefinition;
import com.processpuzzle.event.domain.EventDefinitionRepository;
import com.processpuzzle.event.usecase.exception.EventDefinitionNotFoundException;
import com.processpuzzle.event.usecase.exception.InvalidEventDefinitionException;
import com.processpuzzle.event.usecase.exception.StaleEventDefinitionException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full replace, field by field onto the loaded row so the key and timestamps survive. A supplied
 * {@code version} that no longer matches is refused rather than overwritten; none overwrites
 * unconditionally.
 */
@Service
@Transactional
public class ReplaceEventDefinition {

    private final EventDefinitionRepository repository;

    public ReplaceEventDefinition(EventDefinitionRepository repository) {
        this.repository = repository;
    }

    public EventDefinition replace(String orgKey, String id, EventDefinition desiredState) {
        EventDefinition existing = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new EventDefinitionNotFoundException(orgKey, id));

        if (desiredState.getVersion() != null && !desiredState.getVersion().equals(existing.getVersion())) {
            throw new StaleEventDefinitionException(id);
        }

        existing.replaceWith(desiredState);
        List<String> errors = existing.validate();
        if (!errors.isEmpty()) {
            throw new InvalidEventDefinitionException(id, errors);
        }
        return repository.save(existing);
    }
}
