package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.domain.Attendee;
import com.processpuzzle.calendar.entry.domain.AttendeeType;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarAccessDeniedException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidEntryRequestException;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases one user's calendar data on request: the right to erasure. The bookings a user made stay, so the calendar
 * keeps its times, but they no longer say who made them — the organizer becomes {@value #ERASED}. Their invitations
 * are removed from the entries of other people, which is what being invited means about a person.
 *
 * <p>Allowed for the user themself and for whoever administers the organization. There is no undo.
 */
@Service
@Transactional
public class EraseCalendarUser {

    /** What stands in for the subject of an erased organizer; no identity provider subject looks like it. */
    public static final String ERASED = "erased";
    /** What stands in for the name of an erased organizer or invitee. */
    public static final String ERASED_NAME = "Erased user";

    /** What was anonymized or removed. */
    public record Result(int entries, int invitations) {
    }

    private final CalendarEntryRepository entries;
    private final OrganizationGuard guard;
    private final CurrentPrincipal principal;

    public EraseCalendarUser(CalendarEntryRepository entries, OrganizationGuard guard, CurrentPrincipal principal) {
        this.entries = entries;
        this.guard = guard;
        this.principal = principal;
    }

    public Result erase(String orgKey, String userSub) {
        guard.requireAccess(orgKey);
        if (userSub == null || userSub.isBlank() || ERASED.equals(userSub)) {
            throw new InvalidEntryRequestException("A user is required");
        }
        String caller = principal.subject().orElseThrow(() -> new CalendarAccessDeniedException("Erasing needs a signed-in user"));
        if (!caller.equals(userSub)) {
            guard.requireAdministration(orgKey);
        }

        List<CalendarEntry> organized = entries.findByOrgKeyAndOrganizerSub(orgKey, userSub);
        Set<UUID> organizedIds = organized.stream().map(CalendarEntry::getId).collect(Collectors.toSet());
        for (CalendarEntry entry : organized) {
            entry.setOrganizerSub(ERASED);
            entry.setOrganizerDisplayName(null);
            for (Attendee attendee : entry.getAttendees()) {
                if (attendee.isUser(userSub)) {
                    attendee.setPrincipal(ERASED);
                    attendee.setDisplayName(ERASED_NAME);
                }
            }
        }

        int invitations = 0;
        List<CalendarEntry> invitedTo = entries.findInvitedBy(orgKey, userSub);
        for (CalendarEntry entry : invitedTo) {
            if (organizedIds.contains(entry.getId())) {
                continue;
            }
            long removed = entry.getAttendees().stream().filter(attendee -> attendee.getType() == AttendeeType.USER && userSub.equals(attendee.getPrincipal())).count();
            entry.getAttendees().removeIf(attendee -> attendee.getType() == AttendeeType.USER && userSub.equals(attendee.getPrincipal()));
            invitations += Math.toIntExact(removed);
        }
        entries.saveAll(organized);
        entries.saveAll(invitedTo);
        return new Result(organized.size(), invitations);
    }
}
