package com.processpuzzle.calendar.entry.domain;

import com.processpuzzle.calendar.definition.domain.EntryKind;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What a caller asks to book or to change an entry to, in the domain's vocabulary. Which of the time fields count
 * depends on the kind: a timed entry has {@code start}, {@code end} and optionally a {@code timeZone}, an all-day
 * entry a {@code startDate} and an exclusive {@code endDate}; the fields of the other kind are ignored.
 */
public record BookingRequest(
        EntryKind kind,
        String title,
        String description,
        String location,
        String category,
        EntryVisibility visibility,
        Instant start,
        Instant end,
        String timeZone,
        LocalDate startDate,
        LocalDate endDate,
        Long version) {
}
