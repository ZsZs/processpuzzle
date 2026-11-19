package com.processpuzzle.calendar.definition.usecases.inbound;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarDefinitionRepository;
import com.processpuzzle.calendar.definition.usecases.exception.InvalidCalendarDefinitionException;
import com.processpuzzle.calendar.definition.usecases.exception.StaleCalendarDefinitionException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the draft of a calendar definition when its key is new, replaces it otherwise. A supplied
 * {@code version} that no longer matches is refused rather than overwritten; none overwrites
 * unconditionally. Authoring a definition is designing metadata, so it needs the design right, and the
 * roles the definition names are announced so the organization's role catalog contains them.
 */
@Service
@Transactional
public class SaveCalendarDefinition {

    private final CalendarDefinitionRepository repository;
    private final OrganizationGuard guard;

    public SaveCalendarDefinition(CalendarDefinitionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public CalendarDefinition save(String orgKey, String definitionKey, CalendarDefinition desiredState) {
        guard.requireDesign(orgKey);
        CalendarDefinition definition = repository.findByOrgKeyAndDefinitionKeyAndChannel(orgKey, definitionKey, Channel.DRAFT)
                .map(existing -> replace(existing, desiredState))
                .orElseGet(() -> create(orgKey, definitionKey, desiredState));
        List<String> errors = definition.validate();
        if (!errors.isEmpty()) {
            throw new InvalidCalendarDefinitionException(definitionKey, errors);
        }
        CalendarDefinition saved = repository.save(definition);
        guard.declareRoles(orgKey, "calendar definition '%s'".formatted(definitionKey), saved.allRoles());
        return saved;
    }

    private static CalendarDefinition replace(CalendarDefinition existing, CalendarDefinition desiredState) {
        if (desiredState.getVersion() != null && !desiredState.getVersion().equals(existing.getVersion())) {
            throw new StaleCalendarDefinitionException(existing.getDefinitionKey());
        }
        existing.replaceWith(desiredState);
        return existing;
    }

    private static CalendarDefinition create(String orgKey, String definitionKey, CalendarDefinition desiredState) {
        desiredState.setOrgKey(orgKey);
        desiredState.setDefinitionKey(definitionKey);
        desiredState.setChannel(Channel.DRAFT);
        desiredState.setVersion(null);
        return desiredState;
    }
}
