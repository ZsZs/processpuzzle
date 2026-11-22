package com.processpuzzle.calendar.entry.usecases.inbound;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarKind;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.domain.OccurrenceSpan;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The capacity of a resource calendar: how many bookings may overlap at one time. A team calendar has no capacity and
 * is never checked.
 *
 * <p>Each occurrence of the booking counts the bookings already in place that overlap it, the entry being changed left
 * out. The count is only meaningful once the calendar is locked, so {@link #violations} takes the lock first and must
 * run inside the booking's transaction. Capacity counts bookings, not units: every entry takes one place.
 *
 * <p>A series is checked for its occurrences within {@link #HORIZON_DAYS} of its first day. A series that runs
 * longer than that is not checked beyond the horizon; it is the one limit of this check.
 */
@Service
public class ResourceCapacity {

    /** How far ahead a series is checked, from its first day. */
    static final int HORIZON_DAYS = 730;

    private final CalendarEntryRepository entries;
    private final ResolveCalendarAccess access;

    public ResourceCapacity(CalendarEntryRepository entries, ResolveCalendarAccess access) {
        this.entries = entries;
        this.access = access;
    }

    /**
     * @param candidate the entry as it would be stored; its id is null for a new booking
     * @param selfId the id of the entry being changed, or null for a new booking, so that it does not count against itself
     * @return the violations, empty when every occurrence has a free place
     */
    public List<String> violations(String orgKey, CalendarDefinition definition, CalendarEntry candidate, UUID selfId, ZoneId zone) {
        if (definition.getKind() != CalendarKind.RESOURCE) {
            return List.of();
        }
        access.lockForBooking(orgKey, definition.getDefinitionKey());
        Instant from = candidate.isRecurring() ? candidate.getSeriesFirstDay().atStartOfDay(zone).toInstant() : Instant.EPOCH;
        Instant to = from.plus(Duration.ofDays(HORIZON_DAYS));
        for (OccurrenceSpan span : candidate.spansWithin(from, to, zone)) {
            if (taken(orgKey, candidate.getCalendarKey(), selfId, span, zone) >= definition.getCapacity()) {
                return List.of("the resource has no free place at " + localTime(span.startsAt(zone), zone)
                        + " (capacity " + definition.getCapacity() + ")");
            }
        }
        return List.of();
    }

    /** How many bookings, other than {@code selfId}, have an occurrence that overlaps {@code span}. */
    private int taken(String orgKey, String calendarKey, UUID selfId, OccurrenceSpan span, ZoneId zone) {
        Instant start = span.startsAt(zone);
        Instant end = span.start() != null ? span.end() : span.endDate().atStartOfDay(zone).toInstant();
        LocalDate firstDay = start.atZone(zone).toLocalDate();
        LocalDate lastDay = end.minusNanos(1).atZone(zone).toLocalDate();
        int taken = 0;
        // One day either side, as in the occurrence query: a timed occurrence's local day may be in its own zone.
        for (CalendarEntry other : entries.findOverlapping(orgKey, Set.of(calendarKey), start, end, firstDay.minusDays(1), lastDay.plusDays(1))) {
            if (selfId != null && selfId.equals(other.getId())) {
                continue;
            }
            for (OccurrenceSpan otherSpan : other.spansWithin(start, end, zone)) {
                if (otherSpan.overlaps(start, end, firstDay, lastDay)) {
                    taken++;
                }
            }
        }
        return taken;
    }

    private static String localTime(Instant instant, ZoneId zone) {
        LocalDateTime local = LocalDateTime.ofInstant(instant, zone);
        return local.toLocalDate() + " " + local.toLocalTime().withSecond(0).withNano(0);
    }
}
