package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.domain.CalendarAccess;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.EntryView;
import com.processpuzzle.core.security.CurrentPrincipal;
import org.springframework.stereotype.Component;

/**
 * Turns a stored entry into what the current caller may see of it. The one place that applies the busy rule, so a
 * single entry, a list and an occurrence window cannot disagree about it.
 */
@Component
public class EntryViews {

    private final CurrentPrincipal principal;

    public EntryViews(CurrentPrincipal principal) {
        this.principal = principal;
    }

    public EntryView of(CalendarEntry entry, CalendarAccess access) {
        boolean mine = principal.subject().map(entry::isOrganizedBy).orElse(false);
        return new EntryView(entry, access.seesBusyOnly(mine, entry.getVisibility()), mine, access.mayEdit(mine));
    }
}
