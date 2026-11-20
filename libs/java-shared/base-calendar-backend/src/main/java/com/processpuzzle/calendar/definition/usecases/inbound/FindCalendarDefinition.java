package com.processpuzzle.calendar.definition.usecases.inbound;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarDefinitionRepository;
import com.processpuzzle.calendar.definition.usecases.exception.CalendarDefinitionNotFoundException;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FindCalendarDefinition {

    private final CalendarDefinitionRepository repository;
    private final OrganizationGuard guard;

    public FindCalendarDefinition(CalendarDefinitionRepository repository, OrganizationGuard guard) {
        this.repository = repository;
        this.guard = guard;
    }

    /**
     * A designer's question, about the draft or the released row. Reading the draft is designing, so it
     * needs the design right; the released row only membership.
     */
    public CalendarDefinition find(String orgKey, String definitionKey, Channel channel) {
        requireRead(orgKey, channel);
        return repository.findByOrgKeyAndDefinitionKeyAndChannel(orgKey, definitionKey, channel)
                .orElseThrow(() -> new CalendarDefinitionNotFoundException(orgKey, definitionKey));
    }

    public List<CalendarDefinition> findAll(String orgKey, Channel channel) {
        requireRead(orgKey, channel);
        return repository.findByOrgKeyAndChannelOrderByDefinitionKeyAsc(orgKey, channel);
    }

    /**
     * The released definition, or empty when the organization released none of that key: what the running
     * application reads. For callers that already checked the caller's membership.
     */
    public Optional<CalendarDefinition> released(String orgKey, String definitionKey) {
        return definitionKey == null
                ? Optional.empty()
                : repository.findByOrgKeyAndDefinitionKeyAndChannel(orgKey, definitionKey, Channel.RELEASED);
    }

    /**
     * Locks the released row of a calendar until the transaction ends, for a booking that must count the bookings already
     * in place. Callers must already have decided that the caller may book.
     */
    public void lockReleased(String orgKey, String definitionKey) {
        repository.lockReleased(orgKey, definitionKey);
    }

    private void requireRead(String orgKey, Channel channel) {
        if (channel == Channel.DRAFT) {
            guard.requireDesign(orgKey);
        } else {
            guard.requireAccess(orgKey);
        }
    }
}
