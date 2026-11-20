package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.entry.domain.CalendarAccess;
import com.processpuzzle.calendar.entry.usecases.inbound.ResolveCalendarAccess.ReadableCalendar;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A released calendar as the caller sees it: what a widget needs to draw it and to know what the caller may do. The
 * role lists of the definition stay behind; only their effect, the caller's {@link CalendarAccess}, is reported.
 */
@Service
@Transactional(readOnly = true)
public class GetCalendar {

    /** The definition, the caller's access to it and the zone the organization works in. */
    public record Summary(CalendarDefinition definition, CalendarAccess access, ZoneId timeZone) {
    }

    private final ResolveCalendarAccess access;
    private final OrganizationTimeZone zones;

    public GetCalendar(ResolveCalendarAccess access, OrganizationTimeZone zones) {
        this.access = access;
        this.zones = zones;
    }

    public Summary get(String orgKey, String calendarKey) {
        ReadableCalendar calendar = access.readable(orgKey, calendarKey);
        return new Summary(calendar.definition(), calendar.access(), zones.of(orgKey));
    }
}
