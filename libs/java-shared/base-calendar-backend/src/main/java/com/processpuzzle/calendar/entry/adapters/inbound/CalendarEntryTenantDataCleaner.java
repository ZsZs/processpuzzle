package com.processpuzzle.calendar.entry.adapters.inbound;

import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.shared.event.OrganizationDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes the entries of an erased tenant, in the erasing transaction (BEFORE_COMMIT, as every feature cleaner), so a
 * tenant that is erased leaves no booking behind.
 */
@Component("calendarEntryTenantDataCleaner")
public class CalendarEntryTenantDataCleaner {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarEntryTenantDataCleaner.class);

    private final CalendarEntryRepository entries;

    public CalendarEntryTenantDataCleaner(CalendarEntryRepository entries) {
        this.entries = entries;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        int erased = entries.eraseByOrgKey(event.orgKey());

        LOG.info("Organization {} erased: {} calendar entries deleted.", event.orgKey(), erased);
    }
}
