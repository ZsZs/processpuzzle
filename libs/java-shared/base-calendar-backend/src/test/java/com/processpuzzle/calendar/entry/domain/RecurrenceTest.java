package com.processpuzzle.calendar.entry.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.processpuzzle.calendar.definition.domain.EntryKind;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurrenceTest {

    private static final LocalDate FRIDAY = LocalDate.parse("2026-03-20");
    private static final ZoneId BUDAPEST = ZoneId.of("Europe/Budapest");

    @Test
    void weeklyRepeatsOnTheWeekdayOfTheTemplate() {
        Recurrence weekly = new Recurrence(Recurrence.Frequency.WEEKLY, 3, null);

        assertThat(weekly.daysWithin(FRIDAY, FRIDAY, LocalDate.parse("2026-04-30")))
                .containsExactly(LocalDate.parse("2026-03-20"), LocalDate.parse("2026-03-27"), LocalDate.parse("2026-04-03"));
    }

    @Test
    void monthlySkipsAMonthWithoutTheDay() {
        Recurrence monthly = new Recurrence(Recurrence.Frequency.MONTHLY, null, null);
        LocalDate thirtyFirst = LocalDate.parse("2026-01-31");

        assertThat(monthly.daysWithin(thirtyFirst, thirtyFirst, LocalDate.parse("2026-05-31")))
                .containsExactly(LocalDate.parse("2026-01-31"), LocalDate.parse("2026-03-31"), LocalDate.parse("2026-05-31"));
    }

    @Test
    void theWindowCutsTheSeriesAndTheCountStillCountsFromTheStart() {
        Recurrence daily = new Recurrence(Recurrence.Frequency.DAILY, 5, null);

        assertThat(daily.daysWithin(FRIDAY, LocalDate.parse("2026-03-23"), LocalDate.parse("2026-04-30")))
                .containsExactly(LocalDate.parse("2026-03-23"), LocalDate.parse("2026-03-24"));
        assertThat(daily.lastDay(FRIDAY)).isEqualTo(LocalDate.parse("2026-03-24"));
    }

    @Test
    void untilIsInclusiveAndNoEndMeansNoLastDay() {
        Recurrence weekly = new Recurrence(Recurrence.Frequency.WEEKLY, null, LocalDate.parse("2026-03-27"));

        assertThat(weekly.daysWithin(FRIDAY, FRIDAY, LocalDate.parse("2026-12-31")))
                .containsExactly(LocalDate.parse("2026-03-20"), LocalDate.parse("2026-03-27"));
        assertThat(weekly.lastDay(FRIDAY)).isEqualTo(LocalDate.parse("2026-03-27"));
        assertThat(new Recurrence(Recurrence.Frequency.DAILY, null, null).lastDay(FRIDAY)).isNull();
    }

    @Test
    void aWeeklyNineOClockSeriesKeepsItsLocalTimeAcrossDaylightSavingTime() {
        BookingRequest request = new BookingRequest(EntryKind.RECURRING, "Standup", null, null, null, null,
                Instant.parse("2026-03-20T08:00:00Z"), Instant.parse("2026-03-20T08:30:00Z"), "Europe/Budapest", null, null, null,
                null, new Recurrence(Recurrence.Frequency.WEEKLY, 4, null));
        CalendarEntry series = CalendarEntry.of("acme", "team-calendar", request, BUDAPEST);

        List<OccurrenceSpan> spans = series.spansWithin(Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-05-01T00:00:00Z"), BUDAPEST);

        assertThat(spans).hasSize(4);
        // 09:00 local before the change (CET, +1) and after it (CEST, +2): the same wall-clock time, a different instant.
        assertThat(spans.get(0).start()).isEqualTo(Instant.parse("2026-03-20T08:00:00Z"));
        assertThat(spans.get(1).start()).isEqualTo(Instant.parse("2026-03-27T08:00:00Z"));
        assertThat(spans.get(2).start()).isEqualTo(Instant.parse("2026-04-03T07:00:00Z"));
        assertThat(spans.get(3).start()).isEqualTo(Instant.parse("2026-04-10T07:00:00Z"));
        assertThat(spans.get(2).end()).isEqualTo(Instant.parse("2026-04-03T07:30:00Z"));
        assertThat(series.getSeriesLastDay()).isEqualTo(LocalDate.parse("2026-04-10"));
    }

    @Test
    void anAllDaySeriesRepeatsEveryDayOfItsTemplateSpan() {
        BookingRequest request = new BookingRequest(EntryKind.RECURRING, "Course", null, null, null, null,
                null, null, null, LocalDate.parse("2026-03-20"), LocalDate.parse("2026-03-22"), null,
                null, new Recurrence(Recurrence.Frequency.WEEKLY, 2, null));
        CalendarEntry series = CalendarEntry.of("acme", "team-calendar", request, BUDAPEST);

        List<OccurrenceSpan> spans = series.spansWithin(Instant.parse("2026-03-26T00:00:00Z"), Instant.parse("2026-04-05T00:00:00Z"), BUDAPEST);

        assertThat(spans).extracting(OccurrenceSpan::startDate).containsExactly(LocalDate.parse("2026-03-27"));
        assertThat(spans.get(0).endDate()).isEqualTo(LocalDate.parse("2026-03-29"));
        assertThat(spans.get(0).recurrenceId()).isEqualTo(LocalDate.parse("2026-03-27"));
    }
}
