package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.domain.CalendarAccess;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.domain.EntryView;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidEntryRequestException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the caller has booked or been invited to, across every calendar the caller may read. Only a member has any
 * such thing: anybody else gets nothing, not even an empty-looking answer that hides a refusal, because the endpoint
 * is closed to them anyway.
 *
 * <p>A calendar the caller may not read is left out of the answer, as the occurrences of a calendar are, so the answer
 * never reveals a calendar the caller could not have seen.
 */
@Service
@Transactional(readOnly = true)
public class ListMyOccurrences {

    /** Placeholder for a member with no roles: an empty IN list is not valid JPQL, and no role has this name. */
    private static final String NO_ROLE = "\u0000no-role";

    private final CalendarEntryRepository entries;
    private final ResolveCalendarAccess access;
    private final OrganizationTimeZone zones;
    private final CallerMembership membership;
    private final EntryViews views;

    public ListMyOccurrences(CalendarEntryRepository entries, ResolveCalendarAccess access, OrganizationTimeZone zones,
                             CallerMembership membership, EntryViews views) {
        this.entries = entries;
        this.access = access;
        this.zones = zones;
        this.membership = membership;
        this.views = views;
    }

    /**
     * @throws InvalidEntryRequestException for a window that is empty, reversed or longer than the calendars' own cap
     */
    public List<EntryView> list(String orgKey, Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw new InvalidEntryRequestException("'to' must be after 'from'");
        }
        if (Duration.between(from, to).compareTo(ListOccurrences.MAX_WINDOW) > 0) {
            throw new InvalidEntryRequestException("The window may not be longer than " + ListOccurrences.MAX_WINDOW.toDays() + " days");
        }
        Optional<String> subject = membership.memberSubject(orgKey);
        if (subject.isEmpty()) {
            return List.of();
        }
        Collection<String> roles = membership.memberRoles(orgKey);
        Collection<String> roleParameter = roles.isEmpty() ? Set.of(NO_ROLE) : Set.copyOf(roles);
        ZoneId zone = zones.of(orgKey);
        LocalDate firstDay = from.atZone(zone).toLocalDate();
        LocalDate lastDay = to.minusNanos(1).atZone(zone).toLocalDate();

        Map<String, CalendarAccess> readable = new HashMap<>();
        List<CalendarEntry> mine = entries.findMine(orgKey, subject.get(), roleParameter, from, to, firstDay, lastDay);
        return mine.stream()
                .filter(entry -> readable.computeIfAbsent(entry.getCalendarKey(), key -> accessTo(orgKey, key)) != CalendarAccess.NONE)
                .sorted(Comparator.comparing((CalendarEntry entry) -> entry.startsAt(zone)).thenComparing(CalendarEntry::getId))
                .map(entry -> views.of(entry, readable.get(entry.getCalendarKey())))
                .toList();
    }

    private CalendarAccess accessTo(String orgKey, String calendarKey) {
        return access.find(orgKey, calendarKey).map(ResolveCalendarAccess.ReadableCalendar::access).orElse(CalendarAccess.NONE);
    }
}
