package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.domain.Attendee;
import com.processpuzzle.calendar.entry.domain.AttendanceResponse;
import com.processpuzzle.calendar.entry.domain.AttendeeRequest;
import com.processpuzzle.calendar.entry.domain.AttendeeType;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidBookingException;
import com.processpuzzle.calendar.entry.usecases.outbound.CalendarDirectoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Turns the invitations a request names into the attendees an entry stores. A person is looked up in the directory:
 * the name comes from there, and a subject the directory does not know is refused, so an invitation cannot reach a
 * stranger. A role is taken by name. The organizer is not resolved here — the entry adds them itself.
 */
@Service
public class AttendeeResolver {

    /** The most invitations one entry carries. */
    public static final int MAX_ATTENDEES = 50;
    private static final int MAX_PRINCIPAL = 200;

    private final CalendarDirectoryPort directory;

    public AttendeeResolver(ObjectProvider<CalendarDirectoryPort> directory) {
        this.directory = directory.getIfUnique(() -> EmptyDirectory.INSTANCE);
    }

    /**
     * @param requested the invitations, in the order the caller gave them; duplicates are dropped
     * @param organizerSub the organizer's subject, who is never resolved as an invitee
     * @return the invitations, without the organizer
     * @throws InvalidBookingException listing every invitation that cannot be made
     */
    public List<Attendee> resolve(String orgKey, List<AttendeeRequest> requested, String organizerSub, Instant now) {
        List<String> violations = new ArrayList<>();
        if (requested.size() > MAX_ATTENDEES) {
            violations.add("an entry may invite at most " + MAX_ATTENDEES + " attendees");
        }
        List<Attendee> resolved = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < requested.size(); index++) {
            AttendeeRequest invitation = requested.get(index);
            String label = "attendee " + (index + 1);
            if (invitation.type() == null || invitation.principal() == null || invitation.principal().isBlank()) {
                violations.add(label + " needs a type and a person or role");
                continue;
            }
            String principal = invitation.principal().strip();
            if (principal.length() > MAX_PRINCIPAL) {
                violations.add(label + " is too long");
                continue;
            }
            if (invitation.type() == AttendeeType.USER && principal.equals(organizerSub)) {
                continue;
            }
            if (!seen.add(invitation.type() + ":" + principal)) {
                continue;
            }
            if (invitation.type() == AttendeeType.ROLE) {
                resolved.add(Attendee.role(principal, invitation.required()));
                continue;
            }
            directory.find(orgKey, principal)
                    .ifPresentOrElse(
                            match -> resolved.add(Attendee.user(principal, match.displayName(), invitation.required(), AttendanceResponse.NEEDS_ACTION, now)),
                            () -> violations.add(label + " is not a member of this organization"));
        }
        if (!violations.isEmpty()) {
            throw new InvalidBookingException(violations);
        }
        return resolved;
    }
}
