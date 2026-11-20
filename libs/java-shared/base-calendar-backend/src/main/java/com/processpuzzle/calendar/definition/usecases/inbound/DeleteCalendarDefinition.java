package com.processpuzzle.calendar.definition.usecases.inbound;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarDefinitionRepository;
import com.processpuzzle.calendar.definition.usecases.exception.CalendarDefinitionNotFoundException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes the draft of a calendar definition. The released row stays until the next release, which removes it — an
 * entry of a definition that is no longer released simply cannot be reached, like the calendar itself. Not refused
 * while entries exist: they are people's bookings, kept until the tenant is erased or a person's data is.
 */
@Service
@Transactional
public class DeleteCalendarDefinition {

    private final CalendarDefinitionRepository repository;
    private final OrganizationGuard guard;

    public DeleteCalendarDefinition(CalendarDefinitionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    public void delete(String orgKey, String definitionKey) {
        guard.requireDesign(orgKey);
        CalendarDefinition draft = repository.findByOrgKeyAndDefinitionKeyAndChannel(orgKey, definitionKey, Channel.DRAFT)
                .orElseThrow(() -> new CalendarDefinitionNotFoundException(orgKey, definitionKey));
        repository.delete(draft);
    }
}
