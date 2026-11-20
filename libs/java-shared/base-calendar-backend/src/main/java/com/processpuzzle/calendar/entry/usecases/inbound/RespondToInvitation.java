package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.domain.Attendee;
import com.processpuzzle.calendar.entry.domain.AttendanceResponse;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.domain.EntryView;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarAccessDeniedException;
import com.processpuzzle.calendar.entry.usecases.exception.EntryNotFoundException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidEntryRequestException;
import com.processpuzzle.calendar.entry.usecases.inbound.ResolveCalendarAccess.ReadableCalendar;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the caller's answer to an invitation to an entry. Only the invitee named by the caller's own subject answers,
 * and only for an invitation made by name: a role's members answer by adding themselves. The answer changes nothing
 * else about the entry, so it does not touch its version, and the calendar must still be one the caller may read.
 */
@Service
@Transactional
public class RespondToInvitation {

    private final CalendarEntryRepository entries;
    private final ResolveCalendarAccess access;
    private final CallerMembership membership;
    private final EntryViews views;
    private final Clock clock;

    public RespondToInvitation(CalendarEntryRepository entries, ResolveCalendarAccess access, CallerMembership membership,
                               EntryViews views, ObjectProvider<Clock> clock) {
        this.entries = entries;
        this.access = access;
        this.membership = membership;
        this.views = views;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    public EntryView answer(String orgKey, String calendarKey, UUID entryId, AttendanceResponse answer) {
        if (answer == null || answer == AttendanceResponse.NEEDS_ACTION) {
            throw new InvalidEntryRequestException("An answer is accepted, tentative or declined");
        }
        ReadableCalendar calendar = access.readable(orgKey, calendarKey);
        CalendarEntry entry = entries.findByIdAndOrgKeyAndCalendarKey(entryId, orgKey, calendarKey)
                .orElseThrow(EntryNotFoundException::new);
        String subject = membership.memberSubject(orgKey)
                .orElseThrow(() -> new CalendarAccessDeniedException("Sign in to answer an invitation"));
        Attendee invitation = entry.invitationOf(subject)
                .orElseThrow(() -> new CalendarAccessDeniedException("You are not invited to this entry by name"));
        invitation.answer(answer, clock.instant());
        CalendarEntry saved = entries.save(entry);
        return views.of(saved, calendar.access());
    }
}
