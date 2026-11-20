package com.processpuzzle.calendar.entry.domain;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.EntryKind;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The rules a calendar's definition puts on a booking, as a pure function of the definition, the request and the
 * clock. Boundaries, slots and the horizon are evaluated in the organization's time zone, as the contract says;
 * a timed entry still keeps the zone it was entered in.
 *
 * <p>Collects every violation rather than stopping at the first, so a form can show them together. Capacity is not
 * checked here: it needs the other bookings, and arrives with the resource phase.
 */
public final class BookingRules {

    static final int TITLE_MAX = 200;
    static final int DESCRIPTION_MAX = 4000;
    static final int LOCATION_MAX = 200;
    static final int CATEGORY_MAX = 64;

    private BookingRules() {
    }

    /**
     * @param organizationZone the zone the organization works in
     * @param now the current instant
     * @return the violations, empty when the request may be booked on the calendar
     */
    public static List<String> check(CalendarDefinition definition, BookingRequest request, ZoneId organizationZone, Instant now) {
        List<String> violations = new ArrayList<>();
        checkText(request, violations);
        if (request.kind() == null) {
            violations.add("'kind' is required");
            return violations;
        }
        if (!definition.accepts(request.kind())) {
            violations.add("this calendar does not accept " + request.kind() + " entries");
            return violations;
        }
        if (request.kind() == EntryKind.RECURRING) {
            checkRecurrence(request, organizationZone, violations);
        } else if (request.recurrence() != null) {
            violations.add("'recurrence' is only for a recurring entry");
        }
        if (hasTimedTemplate(request)) {
            checkTimed(definition, request, organizationZone, now, violations);
        } else {
            checkAllDay(definition, request, organizationZone, now, violations);
        }
        return violations;
    }

    /** A recurring entry's template is timed when the request gives instants, as {@link CalendarEntry} decides it. */
    private static boolean hasTimedTemplate(BookingRequest request) {
        return request.kind() == EntryKind.TIMED || request.kind() == EntryKind.RECURRING && request.start() != null;
    }

    private static void checkRecurrence(BookingRequest request, ZoneId zone, List<String> violations) {
        Recurrence rule = request.recurrence();
        if (rule == null) {
            violations.add("a recurring entry needs a 'recurrence'");
            return;
        }
        if (rule.frequency() == null) {
            violations.add("'recurrence.frequency' is required");
            return;
        }
        if (rule.count() != null && rule.until() != null) {
            violations.add("'recurrence' takes a 'count' or an 'until', not both");
            return;
        }
        if (rule.count() != null && (rule.count() < 1 || rule.count() > Recurrence.MAX_COUNT)) {
            violations.add("'recurrence.count' must be between 1 and " + Recurrence.MAX_COUNT);
            return;
        }
        LocalDate first = templateDay(request, zone);
        if (rule.until() != null && first != null && rule.until().isBefore(first)) {
            violations.add("'recurrence.until' must not be before the first occurrence");
        }
    }

    /** The day the template falls on, in the zone it is entered in; null when the request gives no time at all. */
    private static LocalDate templateDay(BookingRequest request, ZoneId organizationZone) {
        if (request.start() == null) {
            return request.startDate();
        }
        boolean named = request.timeZone() != null && !request.timeZone().isBlank() && isZone(request.timeZone());
        return request.start().atZone(named ? ZoneId.of(request.timeZone()) : organizationZone).toLocalDate();
    }

    private static void checkText(BookingRequest request, List<String> violations) {
        if (request.title() == null || request.title().isBlank()) {
            violations.add("'title' is required");
        } else if (request.title().length() > TITLE_MAX) {
            violations.add("'title' must not be longer than " + TITLE_MAX + " characters");
        }
        tooLong(request.description(), DESCRIPTION_MAX, "description", violations);
        tooLong(request.location(), LOCATION_MAX, "location", violations);
        tooLong(request.category(), CATEGORY_MAX, "category", violations);
    }

    private static void tooLong(String value, int max, String name, List<String> violations) {
        if (value != null && value.length() > max) {
            violations.add("'" + name + "' must not be longer than " + max + " characters");
        }
    }

    private static void checkTimed(CalendarDefinition definition, BookingRequest request, ZoneId zone, Instant now,
                                   List<String> violations) {
        if (request.start() == null || request.end() == null) {
            violations.add("a timed entry needs 'start' and 'end'");
            return;
        }
        if (!request.end().isAfter(request.start())) {
            violations.add("'end' must be after 'start'");
            return;
        }
        if (request.timeZone() != null && !isZone(request.timeZone())) {
            violations.add("'timeZone' is not a time zone id: '" + request.timeZone() + "'");
        }
        long minutes = Duration.between(request.start(), request.end()).toMinutes();
        if (definition.getMinDurationMinutes() != null && minutes < definition.getMinDurationMinutes()) {
            violations.add("an entry must last at least " + definition.getMinDurationMinutes() + " minutes");
        }
        if (definition.getMaxDurationMinutes() != null && minutes > definition.getMaxDurationMinutes()) {
            violations.add("an entry may last at most " + definition.getMaxDurationMinutes() + " minutes");
        }
        if (definition.getSlotMinutes() != null
                && !(onSlot(request.start(), zone, definition.getSlotMinutes()) && onSlot(request.end(), zone, definition.getSlotMinutes()))) {
            violations.add("an entry must start and end on a multiple of " + definition.getSlotMinutes() + " minutes");
        }
        if (definition.getHorizonDays() != null && request.start().isAfter(now.plus(Duration.ofDays(definition.getHorizonDays())))) {
            violations.add("an entry may start at most " + definition.getHorizonDays() + " days ahead");
        }
    }

    private static void checkAllDay(CalendarDefinition definition, BookingRequest request, ZoneId zone, Instant now,
                                    List<String> violations) {
        if (request.startDate() == null || request.endDate() == null) {
            violations.add("an all-day entry needs 'startDate' and 'endDate'");
            return;
        }
        if (!request.endDate().isAfter(request.startDate())) {
            violations.add("'endDate' must be after 'startDate' — it is the day after the last day");
            return;
        }
        if (definition.getHorizonDays() != null) {
            LocalDate latest = now.atZone(zone).toLocalDate().plusDays(definition.getHorizonDays());
            if (request.startDate().isAfter(latest)) {
                violations.add("an entry may start at most " + definition.getHorizonDays() + " days ahead");
            }
        }
    }

    /** Whether the instant is a whole number of slots after local midnight in {@code zone}. */
    private static boolean onSlot(Instant instant, ZoneId zone, int slotMinutes) {
        LocalTime time = instant.atZone(zone).toLocalTime();
        return time.getSecond() == 0 && time.getNano() == 0 && (time.getHour() * 60 + time.getMinute()) % slotMinutes == 0;
    }

    private static boolean isZone(String id) {
        try {
            ZoneId.of(id);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }
}
