package com.processpuzzle.calendar.entry.domain;

import static com.processpuzzle.calendar.entry.EntryFixtures.calendar;
import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.EntryKind;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingRulesTest {

    private static final ZoneId BUDAPEST = ZoneId.of("Europe/Budapest");
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    private static CalendarDefinition open() {
        return calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.FULL);
    }

    private static BookingRequest timed(String start, String end) {
        return new BookingRequest(EntryKind.TIMED, "Planning", null, null, null, EntryVisibility.PUBLIC,
                Instant.parse(start), Instant.parse(end), null, null, null, null);
    }

    private static BookingRequest allDay(String start, String endExclusive) {
        return new BookingRequest(EntryKind.ALL_DAY, "Holiday", null, null, null, EntryVisibility.PUBLIC,
                null, null, null, LocalDate.parse(start), LocalDate.parse(endExclusive), null);
    }

    private static List<String> check(CalendarDefinition definition, BookingRequest request) {
        return BookingRules.check(definition, request, BUDAPEST, NOW);
    }

    @Test
    void aWellFormedTimedAndAllDayEntryPass() {
        assertThat(check(open(), timed("2026-12-01T09:00:00Z", "2026-12-01T10:00:00Z"))).isEmpty();
        assertThat(check(open(), allDay("2026-12-24", "2026-12-27"))).isEmpty();
    }

    @Test
    void aTitleIsRequiredAndBounded() {
        BookingRequest untitled = new BookingRequest(EntryKind.ALL_DAY, " ", null, null, null, null, null, null, null,
                LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-25"), null);
        BookingRequest tooLong = new BookingRequest(EntryKind.ALL_DAY, "x".repeat(201), "d".repeat(4001), "l".repeat(201),
                "c".repeat(65), null, null, null, null, LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-25"), null);

        assertThat(check(open(), untitled)).singleElement().asString().contains("'title' is required");
        assertThat(check(open(), tooLong)).hasSize(4);
    }

    @Test
    void theKindIsRequiredAndHasToBeOneTheCalendarAccepts() {
        BookingRequest noKind = new BookingRequest(null, "x", null, null, null, null, null, null, null, null, null, null);
        CalendarDefinition timedOnly = open();
        timedOnly.setAllowedKinds(new ArrayList<>(List.of(EntryKind.TIMED)));

        assertThat(check(open(), noKind)).contains("'kind' is required");
        assertThat(check(timedOnly, allDay("2026-12-24", "2026-12-25"))).singleElement().asString().contains("does not accept ALL_DAY");
    }

    @Test
    void aRecurringEntryNeedsARecurrenceAndItsTemplateIsChecked() {
        CalendarDefinition allowsRecurring = open();
        allowsRecurring.setAllowedKinds(new ArrayList<>(List.of(EntryKind.TIMED, EntryKind.RECURRING)));
        BookingRequest withoutRule = new BookingRequest(EntryKind.RECURRING, "Standup", null, null, null, null,
                Instant.parse("2026-12-01T09:00:00Z"), Instant.parse("2026-12-01T09:15:00Z"), null, null, null, null);

        assertThat(check(allowsRecurring, withoutRule)).singleElement().asString().contains("needs a 'recurrence'");
        assertThat(check(allowsRecurring, recurring(WEEKLY, 10, null))).isEmpty();
    }

    @Test
    void aRecurrenceEndsOnACountOrADateNotBoth() {
        CalendarDefinition allowsRecurring = open();
        allowsRecurring.setAllowedKinds(new ArrayList<>(List.of(EntryKind.TIMED, EntryKind.RECURRING)));

        assertThat(check(allowsRecurring, recurring(WEEKLY, 3, LocalDate.parse("2027-01-31")))).singleElement().asString().contains("not both");
        assertThat(check(allowsRecurring, recurring(WEEKLY, 0, null))).singleElement().asString().contains("between 1 and 500");
        assertThat(check(allowsRecurring, recurring(WEEKLY, 501, null))).singleElement().asString().contains("between 1 and 500");
        assertThat(check(allowsRecurring, recurring(WEEKLY, null, LocalDate.parse("2026-11-30")))).singleElement().asString()
                .contains("must not be before the first occurrence");
    }

    @Test
    void aRecurrenceOnASingleEntryIsRefused() {
        BookingRequest single = new BookingRequest(EntryKind.TIMED, "Lunch", null, null, null, null,
                Instant.parse("2026-12-01T09:00:00Z"), Instant.parse("2026-12-01T10:00:00Z"), null, null, null, null,
                null, new Recurrence(Recurrence.Frequency.DAILY, 2, null));

        assertThat(check(open(), single)).singleElement().asString().contains("only for a recurring entry");
    }

    private static final Recurrence.Frequency WEEKLY = Recurrence.Frequency.WEEKLY;

    private static BookingRequest recurring(Recurrence.Frequency frequency, Integer count, LocalDate until) {
        return new BookingRequest(EntryKind.RECURRING, "Standup", null, null, null, null,
                Instant.parse("2026-12-01T09:00:00Z"), Instant.parse("2026-12-01T09:15:00Z"), null, null, null, null,
                null, new Recurrence(frequency, count, until));
    }

    @Test
    void aTimedEntryNeedsAStartBeforeItsEnd() {
        BookingRequest noEnd = new BookingRequest(EntryKind.TIMED, "x", null, null, null, null,
                Instant.parse("2026-12-01T09:00:00Z"), null, null, null, null, null);

        assertThat(check(open(), noEnd)).singleElement().asString().contains("'start' and 'end'");
        assertThat(check(open(), timed("2026-12-01T10:00:00Z", "2026-12-01T09:00:00Z"))).singleElement().asString().contains("after 'start'");
        assertThat(check(open(), timed("2026-12-01T09:00:00Z", "2026-12-01T09:00:00Z"))).singleElement().asString().contains("after 'start'");
    }

    @Test
    void anUnknownTimeZoneIsRefused() {
        BookingRequest request = new BookingRequest(EntryKind.TIMED, "x", null, null, null, null,
                Instant.parse("2026-12-01T09:00:00Z"), Instant.parse("2026-12-01T10:00:00Z"), "Mars/Base", null, null, null);

        assertThat(check(open(), request)).singleElement().asString().contains("'timeZone'");
    }

    @Test
    void minimumAndMaximumDurationAreEnforced() {
        CalendarDefinition definition = open();
        definition.setMinDurationMinutes(30);
        definition.setMaxDurationMinutes(120);

        assertThat(check(definition, timed("2026-12-01T09:00:00Z", "2026-12-01T09:15:00Z"))).singleElement().asString().contains("at least 30");
        assertThat(check(definition, timed("2026-12-01T09:00:00Z", "2026-12-01T12:00:00Z"))).singleElement().asString().contains("at most 120");
        assertThat(check(definition, timed("2026-12-01T09:00:00Z", "2026-12-01T10:00:00Z"))).isEmpty();
    }

    @Test
    void slotsAreMeasuredInTheOrganizationsZoneNotInUtc() {
        CalendarDefinition definition = open();
        definition.setSlotMinutes(60);
        // 09:00 UTC is 10:00 in Budapest in winter: on the hour in both.
        assertThat(check(definition, timed("2026-12-01T09:00:00Z", "2026-12-01T10:00:00Z"))).isEmpty();
        assertThat(check(definition, timed("2026-12-01T09:30:00Z", "2026-12-01T10:00:00Z"))).singleElement().asString().contains("multiple of 60");
        assertThat(check(definition, timed("2026-12-01T09:00:00Z", "2026-12-01T10:00:30Z"))).singleElement().asString().contains("multiple of 60");

        // In a zone with a half-hour offset the same UTC hour is off the slot.
        assertThat(BookingRules.check(definition, timed("2026-12-01T09:00:00Z", "2026-12-01T10:00:00Z"), ZoneId.of("Asia/Kolkata"), NOW))
                .singleElement().asString().contains("multiple of 60");
    }

    @Test
    void theHorizonLimitsHowFarAheadAnEntryMayStart() {
        CalendarDefinition definition = open();
        definition.setHorizonDays(30);

        assertThat(check(definition, timed("2026-12-19T09:00:00Z", "2026-12-19T10:00:00Z"))).isEmpty();
        assertThat(check(definition, timed("2026-12-20T09:00:00Z", "2026-12-20T10:00:00Z"))).singleElement().asString().contains("30 days ahead");
        assertThat(check(definition, allDay("2026-12-19", "2026-12-20"))).isEmpty();
        assertThat(check(definition, allDay("2026-12-20", "2026-12-21"))).singleElement().asString().contains("30 days ahead");
    }

    @Test
    void anAllDayEntryEndsOnALaterDateThanItStarts() {
        BookingRequest noDates = new BookingRequest(EntryKind.ALL_DAY, "x", null, null, null, null, null, null, null, null, null, null);

        assertThat(check(open(), noDates)).singleElement().asString().contains("'startDate' and 'endDate'");
        assertThat(check(open(), allDay("2026-12-24", "2026-12-24"))).singleElement().asString().contains("after 'startDate'");
    }

    @Test
    void anEntryInThePastIsAllowed() {
        assertThat(check(open(), timed("2025-01-01T09:00:00Z", "2025-01-01T10:00:00Z"))).isEmpty();
    }

    @Test
    void anEntryIsBuiltWithOnlyTheFieldsOfItsKindAndTheOrganizationZoneAsDefault() {
        CalendarEntry timedEntry = CalendarEntry.of("org-1", "team-calendar", timed("2026-12-01T09:00:00Z", "2026-12-01T10:00:00Z"), BUDAPEST);
        CalendarEntry allDayEntry = CalendarEntry.of("org-1", "team-calendar", allDay("2026-12-24", "2026-12-27"), BUDAPEST);

        assertThat(timedEntry.getTimeZone()).isEqualTo("Europe/Budapest");
        assertThat(timedEntry.getStartDate()).isNull();
        assertThat(allDayEntry.getTimeZone()).isNull();
        assertThat(allDayEntry.getStartUtc()).isNull();
        assertThat(allDayEntry.getEndDate()).isEqualTo(LocalDate.parse("2026-12-27"));
    }
}
