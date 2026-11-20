package com.processpuzzle.calendar.entry.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * One occurrence of an entry as a span of time: the instants of a timed occurrence, or the days of an all-day one with
 * an exclusive end. {@code recurrenceId} is the occurrence's local day, or null for a single entry.
 */
public record OccurrenceSpan(LocalDate recurrenceId, Instant start, Instant end, LocalDate startDate, LocalDate endDate) {

    /** Whether the span overlaps {@code [from, to)} as instants, or the days {@code [firstDay, lastDay]} for all-day. */
    public boolean overlaps(Instant from, Instant to, LocalDate firstDay, LocalDate lastDay) {
        if (start != null) {
            return start.isBefore(to) && end.isAfter(from);
        }
        return !startDate.isAfter(lastDay) && endDate.isAfter(firstDay);
    }

    /** When the span starts: the instant, or midnight of its first day in {@code zone} for an all-day span. */
    public Instant startsAt(ZoneId zone) {
        return start != null ? start : startDate.atStartOfDay(zone).toInstant();
    }
}
