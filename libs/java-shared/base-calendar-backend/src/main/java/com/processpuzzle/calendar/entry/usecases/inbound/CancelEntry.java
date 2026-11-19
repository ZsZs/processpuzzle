package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarAccessDeniedException;
import com.processpuzzle.calendar.entry.usecases.exception.EntryNotFoundException;
import com.processpuzzle.calendar.entry.usecases.inbound.ResolveCalendarAccess.ReadableCalendar;
import com.processpuzzle.core.security.CurrentPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cancels an entry. Allowed for its organizer, who still needs the booking role, and for the calendar's managers. */
@Service
@Transactional
public class CancelEntry {

    private final CalendarEntryRepository entries;
    private final ResolveCalendarAccess access;
    private final CurrentPrincipal principal;
    private final EntryPlatformFacts facts;

    public CancelEntry(CalendarEntryRepository entries, ResolveCalendarAccess access, CurrentPrincipal principal,
                       EntryPlatformFacts facts) {
        this.entries = entries;
        this.access = access;
        this.principal = principal;
        this.facts = facts;
    }

    public void cancel(String orgKey, String calendarKey, UUID entryId) {
        ReadableCalendar calendar = access.readable(orgKey, calendarKey);
        CalendarEntry entry = entries.findByIdAndOrgKeyAndCalendarKey(entryId, orgKey, calendarKey)
                .orElseThrow(EntryNotFoundException::new);
        boolean mine = principal.subject().map(entry::isOrganizedBy).orElse(false);
        if (!calendar.access().mayEdit(mine)) {
            throw new CalendarAccessDeniedException("You may not cancel this entry");
        }
        entries.delete(entry);
        facts.deleted(entry);
    }
}
