package com.processpuzzle.calendar.definition.adapters.inbound;

import com.processpuzzle.calendar.definition.domain.CalendarDefinitionRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes the calendar definitions of an erased tenant, in the erasing transaction ({@code BEFORE_COMMIT}, as
 * every feature's cleaner — see the entry cleaner).
 */
@Component("calendarDefinitionTenantDataCleaner")
public class CalendarDefinitionTenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarDefinitionTenantDataCleaner.class);

    private final CalendarDefinitionRepository definitions;

    public CalendarDefinitionTenantDataCleaner(CalendarDefinitionRepository definitions) {
        this.definitions = definitions;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        int erased = definitions.eraseByOrgKey(event.orgKey());

        LOG.info("Organization '{}' erased: {} calendar definition(s) deleted.", event.orgKey(), erased);
    }
}
