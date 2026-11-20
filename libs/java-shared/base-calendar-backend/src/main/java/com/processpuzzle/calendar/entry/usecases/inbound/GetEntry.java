package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.domain.EntryView;
import com.processpuzzle.calendar.entry.usecases.exception.EntryNotFoundException;
import com.processpuzzle.calendar.entry.usecases.inbound.ResolveCalendarAccess.ReadableCalendar;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One entry at the detail the caller may see. Open to anonymous callers on a calendar that is public. */
@Service
@Transactional(readOnly = true)
public class GetEntry {

    private final CalendarEntryRepository entries;
    private final ResolveCalendarAccess access;
    private final EntryViews views;

    public GetEntry(CalendarEntryRepository entries, ResolveCalendarAccess access, EntryViews views) {
        this.entries = entries;
        this.access = access;
        this.views = views;
    }

    public EntryView get(String orgKey, String calendarKey, UUID entryId) {
        ReadableCalendar calendar = access.readable(orgKey, calendarKey);
        return entries.findByIdAndOrgKeyAndCalendarKey(entryId, orgKey, calendarKey)
                .map(entry -> views.of(entry, calendar.access()))
                .orElseThrow(EntryNotFoundException::new);
    }
}
