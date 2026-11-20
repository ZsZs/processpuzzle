package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.domain.Attendee;
import com.processpuzzle.calendar.entry.domain.AttendanceResponse;
import com.processpuzzle.calendar.entry.domain.BookingRequest;
import com.processpuzzle.calendar.entry.domain.BookingRules;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.domain.EntryView;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarAccessDeniedException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidBookingException;
import com.processpuzzle.calendar.entry.usecases.inbound.ResolveCalendarAccess.ReadableCalendar;
import com.processpuzzle.core.security.CurrentPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Books an entry on a calendar for the caller, who becomes its organizer and is invited to it, accepted. Needs a
 * signed-in member with a booking role; a caller who may not even read the calendar is told it does not exist. The
 * entry has to keep the rules of the calendar's released definition, and every invitation has to be a member.
 */
@Service
@Transactional
public class BookEntry {

    private final CalendarEntryRepository entries;
    private final ResolveCalendarAccess access;
    private final OrganizationTimeZone zones;
    private final CurrentPrincipal principal;
    private final EntryViews views;
    private final EntryPlatformFacts facts;
    private final AttendeeResolver attendees;
    private final Clock clock;

    public BookEntry(CalendarEntryRepository entries, ResolveCalendarAccess access, OrganizationTimeZone zones,
                     CurrentPrincipal principal, EntryViews views, EntryPlatformFacts facts, AttendeeResolver attendees,
                     ObjectProvider<Clock> clock) {
        this.entries = entries;
        this.access = access;
        this.zones = zones;
        this.principal = principal;
        this.views = views;
        this.facts = facts;
        this.attendees = attendees;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    public EntryView book(String orgKey, String calendarKey, BookingRequest request) {
        ReadableCalendar calendar = access.readable(orgKey, calendarKey);
        String organizer = principal.subject()
                .orElseThrow(() -> new CalendarAccessDeniedException("Sign in to book an entry"));
        if (!calendar.access().canBook()) {
            throw new CalendarAccessDeniedException("You may not book on this calendar");
        }
        ZoneId zone = zones.of(orgKey);
        Instant now = clock.instant();
        List<String> violations = BookingRules.check(calendar.definition(), request, zone, now);
        if (!violations.isEmpty()) {
            throw new InvalidBookingException(violations);
        }
        List<Attendee> invited = request.attendees() == null
                ? List.of()
                : attendees.resolve(orgKey, request.attendees(), organizer, now);
        CalendarEntry entry = CalendarEntry.of(orgKey, calendarKey, request, zone);
        entry.setOrganizerSub(organizer);
        entry.setOrganizerDisplayName(principal.displayName().orElse(null));
        entry.reconcileAttendees(invited, organizerRow(organizer, entry.getOrganizerDisplayName(), now), false, now);
        CalendarEntry saved = entries.save(entry);
        facts.created(saved);
        return views.of(saved, calendar.access());
    }

    /** The organizer's own invitation: accepted from the start, and never asked again. */
    static Attendee organizerRow(String organizer, String name, Instant now) {
        return Attendee.user(organizer, name == null ? organizer : name, false, AttendanceResponse.ACCEPTED, now);
    }
}
