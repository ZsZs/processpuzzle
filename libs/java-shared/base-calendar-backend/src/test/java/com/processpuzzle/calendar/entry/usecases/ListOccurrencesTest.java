package com.processpuzzle.calendar.entry.usecases;

import static com.processpuzzle.calendar.entry.EntryFixtures.ALICE;
import static com.processpuzzle.calendar.entry.EntryFixtures.BOB;
import static com.processpuzzle.calendar.entry.EntryFixtures.CALENDAR;
import static com.processpuzzle.calendar.entry.EntryFixtures.ORG;
import static com.processpuzzle.calendar.entry.EntryFixtures.allDayEntry;
import static com.processpuzzle.calendar.entry.EntryFixtures.anonymous;
import static com.processpuzzle.calendar.entry.EntryFixtures.calendar;
import static com.processpuzzle.calendar.entry.EntryFixtures.member;
import static com.processpuzzle.calendar.entry.EntryFixtures.timedEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import com.processpuzzle.calendar.entry.EntryWiring;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.OccurrenceView;
import com.processpuzzle.calendar.entry.domain.EntryVisibility;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidEntryRequestException;
import com.processpuzzle.core.security.CurrentPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ListOccurrencesTest {

    private static final Instant FROM = Instant.parse("2026-12-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-12-08T00:00:00Z");

    private static EntryWiring wired(CurrentPrincipal principal, CalendarVisibility visibility, PublicDetail detail) {
        return new EntryWiring(principal, calendar(visibility, detail));
    }

    private static void stored(EntryWiring wiring, CalendarEntry... entries) {
        when(wiring.repository.findOverlapping(eq(ORG), any(), any(), any(), any(), any())).thenReturn(List.of(entries));
    }

    @Test
    void listsWhatTheReadableCalendarsHoldInStartOrderIncludingAllDayEntries() {
        EntryWiring wiring = wired(member(ALICE), CalendarVisibility.AUTHENTICATED, PublicDetail.FULL);
        CalendarEntry late = timedEntry(ALICE, EntryVisibility.PUBLIC);
        late.setStartUtc(Instant.parse("2026-12-03T09:00:00Z"));
        late.setEndUtc(Instant.parse("2026-12-03T10:00:00Z"));
        CalendarEntry early = timedEntry(ALICE, EntryVisibility.PUBLIC);
        CalendarEntry holiday = allDayEntry(ALICE, LocalDate.parse("2026-12-02"), LocalDate.parse("2026-12-04"));
        stored(wiring, late, holiday, early);

        List<OccurrenceView> listed = wiring.occurrences().list(ORG, List.of(CALENDAR), FROM, TO);

        assertThat(listed).extracting(view -> view.view().entry().getId()).containsExactly(early.getId(), holiday.getId(), late.getId());
    }

    @Test
    void asksTheRepositoryForTheWindowInTheOrganizationsDays() {
        EntryWiring wiring = wired(member(ALICE), CalendarVisibility.AUTHENTICATED, PublicDetail.FULL);
        stored(wiring);

        // Budapest is UTC+1 in December: 23:00Z on the 30th is already 1 December locally.
        wiring.occurrences().list(ORG, List.of(CALENDAR), Instant.parse("2026-11-30T23:00:00Z"), Instant.parse("2026-12-07T23:00:00Z"));

        ArgumentCaptor<LocalDate> first = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> last = ArgumentCaptor.forClass(LocalDate.class);
        verify(wiring.repository).findOverlapping(eq(ORG), eq(Set.of(CALENDAR)), eq(Instant.parse("2026-11-30T23:00:00Z")),
                eq(Instant.parse("2026-12-07T23:00:00Z")), first.capture(), last.capture());
        // Widened by a day either side, so that a series whose local day is in its own zone is not missed; the exact check follows.
        assertThat(first.getValue()).isEqualTo(LocalDate.parse("2026-11-30"));
        assertThat(last.getValue()).as("the exclusive end instant belongs to the next day, widened by one more").isEqualTo(LocalDate.parse("2026-12-08"));
    }

    @Test
    void aCalendarTheCallerMayNotReadIsLeftOutWithoutAskingForItsEntries() {
        EntryWiring wiring = wired(anonymous(), CalendarVisibility.AUTHENTICATED, PublicDetail.FULL);

        assertThat(wiring.occurrences().list(ORG, List.of(CALENDAR, "unknown"), FROM, TO)).isEmpty();
        verify(wiring.repository, never()).findOverlapping(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anAnonymousCallerGetsBusyTimeFromAPublicCalendarWithBusyOnlyDetail() {
        EntryWiring wiring = wired(anonymous(), CalendarVisibility.PUBLIC, PublicDetail.BUSY_ONLY);
        stored(wiring, timedEntry(ALICE, EntryVisibility.PUBLIC));

        List<OccurrenceView> listed = wiring.occurrences().list(ORG, List.of(CALENDAR), FROM, TO);

        assertThat(listed).singleElement().satisfies(view -> {
            assertThat(view.view().busy()).isTrue();
            assertThat(view.view().mine()).isFalse();
            assertThat(view.view().canEdit()).isFalse();
        });
    }

    @Test
    void theCallerSeesOwnPrivateEntriesInFullAndOthersAsBusy() {
        EntryWiring wiring = wired(member(ALICE), CalendarVisibility.AUTHENTICATED, PublicDetail.FULL);
        CalendarEntry mine = timedEntry(ALICE, EntryVisibility.PRIVATE);
        CalendarEntry theirs = timedEntry(BOB, EntryVisibility.PRIVATE);
        theirs.setStartUtc(Instant.parse("2026-12-02T09:00:00Z"));
        theirs.setEndUtc(Instant.parse("2026-12-02T10:00:00Z"));
        stored(wiring, mine, theirs);

        List<OccurrenceView> listed = wiring.occurrences().list(ORG, List.of(CALENDAR), FROM, TO);

        assertThat(listed).extracting(o -> o.view().busy()).containsExactly(false, true);
        assertThat(listed).extracting(o -> o.view().mine()).containsExactly(true, false);
    }

    @Test
    void aRepeatedKeyIsAskedOnce() {
        EntryWiring wiring = wired(member(ALICE), CalendarVisibility.AUTHENTICATED, PublicDetail.FULL);
        stored(wiring);

        wiring.occurrences().list(ORG, List.of(CALENDAR, CALENDAR), FROM, TO);

        verify(wiring.repository).findOverlapping(eq(ORG), eq(Set.of(CALENDAR)), any(), any(), any(), any());
    }

    @Test
    void refusesAnEmptyReversedOrTooLongWindow() {
        EntryWiring wiring = wired(member(ALICE), CalendarVisibility.AUTHENTICATED, PublicDetail.FULL);

        assertThatThrownBy(() -> wiring.occurrences().list(ORG, List.of(CALENDAR), TO, FROM)).isInstanceOf(InvalidEntryRequestException.class);
        assertThatThrownBy(() -> wiring.occurrences().list(ORG, List.of(CALENDAR), FROM, FROM)).isInstanceOf(InvalidEntryRequestException.class);
        assertThatThrownBy(() -> wiring.occurrences().list(ORG, List.of(CALENDAR), FROM, FROM.plusSeconds(101L * 24 * 3600)))
                .isInstanceOf(InvalidEntryRequestException.class).hasMessageContaining("100 days");
        assertThat(wiring.occurrences().list(ORG, List.of(CALENDAR), FROM, FROM.plusSeconds(100L * 24 * 3600))).isEmpty();
    }

    @Test
    void theCalendarSummaryReportsTheCallersPermissionsAndTheOrganizationZone() {
        EntryWiring wiring = wired(member(ALICE), CalendarVisibility.AUTHENTICATED, PublicDetail.FULL);

        var summary = wiring.calendar().get(ORG, CALENDAR);

        assertThat(summary.timeZone().getId()).isEqualTo("Europe/Budapest");
        assertThat(summary.access().canBook()).isTrue();
        assertThat(summary.definition().getDefinitionKey()).isEqualTo(CALENDAR);
    }
}
