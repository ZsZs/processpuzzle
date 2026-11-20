package com.processpuzzle.calendar.entry.domain;

import com.processpuzzle.calendar.definition.domain.EntryKind;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What a caller asks to book or to change an entry to. {@code attendees} is null when the request says nothing about
 * invitations: a booking then invites nobody beyond the organizer, and a change keeps the invitees as they are., in the domain's vocabulary. Which of the time fields count
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
        Long version,
        java.util.List<AttendeeRequest> attendees) {

    /** A request without invitations: what a booking that names nobody, and a change that keeps the invitees, use. */
    public BookingRequest(EntryKind kind, String title, String description, String location, String category,
                          EntryVisibility visibility, java.time.Instant start, java.time.Instant end, String timeZone,
                          java.time.LocalDate startDate, java.time.LocalDate endDate, Long version) {
        this(kind, title, description, location, category, visibility, start, end, timeZone, startDate, endDate, version, null);
    }
}
