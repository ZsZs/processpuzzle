package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.domain.OccurrenceView;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidEntryRequestException;
import com.processpuzzle.calendar.entry.usecases.inbound.ResolveCalendarAccess.ReadableCalendar;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the listed calendars show in a time window. A calendar the caller may not read, or that is not released, is
 * left out and not reported, so the answer does not reveal that it exists. Open to anonymous callers, who get what
 * public calendars show them. The window is capped so that no query reads an unbounded stretch of time.
 *
 * <p>Single entries only for now: a recurring entry will be expanded here.
 */
@Service
@Transactional(readOnly = true)
public class ListOccurrences {

    /** The longest window one call may ask for. */
    public static final Duration MAX_WINDOW = Duration.ofDays(100);

    private final CalendarEntryRepository entries;
    private final ResolveCalendarAccess access;
    private final OrganizationTimeZone zones;
    private final EntryViews views;

    public ListOccurrences(CalendarEntryRepository entries, ResolveCalendarAccess access, OrganizationTimeZone zones,
                           EntryViews views) {
        this.entries = entries;
        this.access = access;
        this.zones = zones;
        this.views = views;
    }

    /**
     * @param from the window start, inclusive
     * @param to the window end, exclusive
     * @return the occurrences overlapping the window as the caller sees them, in start order; a recurring entry
     *         contributes one per day of its series in the window
     * @throws InvalidEntryRequestException for a window that is empty, reversed or longer than {@link #MAX_WINDOW}
     */
    public List<OccurrenceView> list(String orgKey, List<String> calendarKeys, Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw new InvalidEntryRequestException("'to' must be after 'from'");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new InvalidEntryRequestException("The window may not be longer than " + MAX_WINDOW.toDays() + " days");
        }
        Map<String, ReadableCalendar> readable = new LinkedHashMap<>();
        for (String key : new LinkedHashSet<>(calendarKeys)) {
            access.find(orgKey, key).ifPresent(calendar -> readable.put(key, calendar));
        }
        if (readable.isEmpty()) {
            return List.of();
        }
        ZoneId zone = zones.of(orgKey);
        LocalDate firstDay = from.atZone(zone).toLocalDate();
        LocalDate lastDay = to.minusNanos(1).atZone(zone).toLocalDate();
        // One day either side: a series is found by its days, and a timed occurrence's local day is in its own zone.
        return entries.findOverlapping(orgKey, readable.keySet(), from, to, firstDay.minusDays(1), lastDay.plusDays(1)).stream()
                .flatMap(entry -> entry.spansWithin(from, to, zone).stream()
                        .filter(span -> span.overlaps(from, to, firstDay, lastDay))
                        .map(span -> new OccurrenceView(views.of(entry, readable.get(entry.getCalendarKey()).access()), span)))
                .sorted(Comparator.comparing((OccurrenceView occurrence) -> occurrence.span().startsAt(zone))
                        .thenComparing(occurrence -> occurrence.view().entry().getId()))
                .toList();
    }
}
