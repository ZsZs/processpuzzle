package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.domain.Attendee;
import com.processpuzzle.calendar.entry.domain.AttendeeType;
import com.processpuzzle.calendar.entry.domain.BookingRequest;
import com.processpuzzle.calendar.entry.domain.BookingRules;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.domain.EntryView;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarAccessDeniedException;
import com.processpuzzle.calendar.entry.usecases.exception.EntryNotFoundException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidBookingException;
import com.processpuzzle.calendar.entry.usecases.exception.StaleEntryException;
import com.processpuzzle.calendar.entry.usecases.inbound.ResolveCalendarAccess.ReadableCalendar;
import com.processpuzzle.core.security.CurrentPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replaces an entry. Allowed for its organizer, who still needs the booking role, and for the calendar's managers.
 * A supplied {@code version} that no longer matches is refused rather than overwritten. The kind of an entry cannot
 * change, and the new content has to keep the calendar's rules like a new booking does.
 *
 * <p>The invitations: a request that names them replaces the list; one that does not keeps it. Moving the time asks
 * every invitee to answer again, which is what an invitee needs to know when the meeting moved.
 */
@Service
@Transactional
public class UpdateEntry {

    private final CalendarEntryRepository entries;
    private final ResolveCalendarAccess access;
    private final OrganizationTimeZone zones;
    private final CurrentPrincipal principal;
    private final EntryViews views;
    private final EntryPlatformFacts facts;
    private final AttendeeResolver attendees;
    private final Clock clock;

    public UpdateEntry(CalendarEntryRepository entries, ResolveCalendarAccess access, OrganizationTimeZone zones,
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

    public EntryView update(String orgKey, String calendarKey, UUID entryId, BookingRequest request) {
        ReadableCalendar calendar = access.readable(orgKey, calendarKey);
        CalendarEntry entry = entries.findByIdAndOrgKeyAndCalendarKey(entryId, orgKey, calendarKey)
                .orElseThrow(EntryNotFoundException::new);
        boolean mine = principal.subject().map(entry::isOrganizedBy).orElse(false);
        if (!calendar.access().mayEdit(mine)) {
            throw new CalendarAccessDeniedException("You may not change this entry");
        }
        if (request.version() != null && !request.version().equals(entry.getVersion())) {
            throw new StaleEntryException();
        }
        ZoneId zone = zones.of(orgKey);
        Instant now = clock.instant();
        List<String> violations = BookingRules.check(calendar.definition(), request, zone, now);
        if (!violations.isEmpty()) {
            throw new InvalidBookingException(violations);
        }
        if (request.kind() != entry.getKind()) {
            throw new InvalidBookingException(List.of("the kind of an entry cannot change"));
        }
        CalendarEntry replacement = CalendarEntry.of(orgKey, calendarKey, request, zone);
        boolean timeMoved = entry.timeDiffersFrom(replacement);
        List<Attendee> invited = request.attendees() == null
                ? keepInvitees(entry)
                : attendees.resolve(orgKey, request.attendees(), entry.getOrganizerSub(), now);
        entry.replaceWith(replacement);
        Attendee organizer = BookEntry.organizerRow(entry.getOrganizerSub(), entry.getOrganizerDisplayName(), now);
        entry.reconcileAttendees(invited, organizer, timeMoved, now);
        CalendarEntry saved = entries.save(entry);
        facts.updated(saved);
        return views.of(saved, calendar.access());
    }

    /** The invitees as they are now, without the organizer, to be written back unchanged. */
    private static List<Attendee> keepInvitees(CalendarEntry entry) {
        return entry.getAttendees().stream()
                .filter(attendee -> !(attendee.getType() == AttendeeType.USER && attendee.getPrincipal().equals(entry.getOrganizerSub())))
                .toList();
    }
}
