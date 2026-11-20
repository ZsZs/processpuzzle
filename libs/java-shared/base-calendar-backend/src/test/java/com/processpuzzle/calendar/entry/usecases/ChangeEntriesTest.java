package com.processpuzzle.calendar.entry.usecases;

import static com.processpuzzle.calendar.entry.EntryFixtures.ALICE;
import static com.processpuzzle.calendar.entry.EntryFixtures.BOB;
import static com.processpuzzle.calendar.entry.EntryFixtures.CALENDAR;
import static com.processpuzzle.calendar.entry.EntryFixtures.ORG;
import static com.processpuzzle.calendar.entry.EntryFixtures.anonymous;
import static com.processpuzzle.calendar.entry.EntryFixtures.calendar;
import static com.processpuzzle.calendar.entry.EntryFixtures.foreigner;
import static com.processpuzzle.calendar.entry.EntryFixtures.member;
import static com.processpuzzle.calendar.entry.EntryFixtures.staff;
import static com.processpuzzle.calendar.entry.EntryFixtures.timedEntry;
import static com.processpuzzle.calendar.entry.EntryFixtures.withRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.EntryKind;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import com.processpuzzle.calendar.entry.EntryWiring;
import com.processpuzzle.calendar.entry.domain.BookingRequest;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.EntryView;
import com.processpuzzle.calendar.entry.domain.EntryVisibility;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarAccessDeniedException;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarNotFoundException;
import com.processpuzzle.calendar.entry.usecases.exception.EntryNotFoundException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidBookingException;
import com.processpuzzle.calendar.entry.usecases.exception.StaleEntryException;
import com.processpuzzle.core.security.CurrentPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Reading one entry, replacing it and cancelling it: who may, and what the caller gets to see. */
class ChangeEntriesTest {

    private static BookingRequest renamed(Long version) {
        return new BookingRequest(EntryKind.TIMED, "Renamed", null, null, null, EntryVisibility.PUBLIC,
                Instant.parse("2026-12-01T11:00:00Z"), Instant.parse("2026-12-01T12:00:00Z"), "UTC", null, null, version);
    }

    private static EntryWiring wired(CurrentPrincipal principal, CalendarEntry stored, CalendarDefinition calendar) {
        EntryWiring wiring = new EntryWiring(principal, calendar);
        when(wiring.repository.findByIdAndOrgKeyAndCalendarKey(stored.getId(), ORG, CALENDAR)).thenReturn(Optional.of(stored));
        return wiring;
    }

    private static CalendarDefinition team() {
        return calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.BUSY_ONLY);
    }

    // --- read

    @Test
    void aBookerSeesTheContentOfAPublicEntryAndAReaderOnlyWhenItIsTaking() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);
        CalendarDefinition bookersOnly = withRoles(team(), List.of(), List.of("staff"), List.of());

        EntryView booker = wired(member(BOB, "staff"), stored, bookersOnly).get().get(ORG, CALENDAR, stored.getId());
        EntryView reader = wired(member(BOB, "guest"), stored, bookersOnly).get().get(ORG, CALENDAR, stored.getId());

        assertThat(booker.busy()).isFalse();
        assertThat(reader.busy()).isTrue();
        assertThat(reader.canEdit()).isFalse();
    }

    @Test
    void aPrivateEntryIsBusyEvenToABookerButNotToItsOrganizerOrAManager() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PRIVATE);

        assertThat(wired(member(BOB), stored, team()).get().get(ORG, CALENDAR, stored.getId()).busy()).isTrue();
        assertThat(wired(member(ALICE), stored, team()).get().get(ORG, CALENDAR, stored.getId()).busy()).isFalse();
        assertThat(wired(staff("staff-1"), stored, team()).get().get(ORG, CALENDAR, stored.getId()).busy()).isFalse();
    }

    @Test
    void anAnonymousCallerReadsAnEntryOfAPublicCalendarAtItsPublicDetail() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);

        EntryView full = wired(anonymous(), stored, calendar(CalendarVisibility.PUBLIC, PublicDetail.FULL)).get().get(ORG, CALENDAR, stored.getId());
        EntryView busy = wired(anonymous(), stored, calendar(CalendarVisibility.PUBLIC, PublicDetail.BUSY_ONLY)).get().get(ORG, CALENDAR, stored.getId());

        assertThat(full.busy()).isFalse();
        assertThat(full.mine()).isFalse();
        assertThat(busy.busy()).isTrue();
    }

    @Test
    void anEntryOfAnUnreadableCalendarOrAMissingEntryIsNotFound() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);
        EntryWiring hidden = wired(foreigner(BOB), stored, team());
        EntryWiring visible = wired(member(BOB), stored, team());

        assertThatThrownBy(() -> hidden.get().get(ORG, CALENDAR, stored.getId())).isInstanceOf(CalendarNotFoundException.class);
        assertThatThrownBy(() -> visible.get().get(ORG, CALENDAR, java.util.UUID.randomUUID())).isInstanceOf(EntryNotFoundException.class);
    }

    // --- update

    @Test
    void theOrganizerReplacesTheEntryAndTheFactIsPublished() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PRIVATE);
        EntryWiring wiring = wired(member(ALICE), stored, team());

        EntryView updated = wiring.update().update(ORG, CALENDAR, stored.getId(), renamed(0L));

        assertThat(updated.entry()).isSameAs(stored);
        assertThat(stored.getTitle()).isEqualTo("Renamed");
        assertThat(stored.getVisibility()).isEqualTo(EntryVisibility.PUBLIC);
        assertThat(stored.getStartUtc()).isEqualTo(Instant.parse("2026-12-01T11:00:00Z"));
        assertThat(stored.getOrganizerSub()).as("the organizer does not change").isEqualTo(ALICE);
        verify(wiring.facts).updated(stored);
    }

    @Test
    void aManagerReplacesSomebodyElsesEntryButAPlainBookerDoesNot() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);
        CalendarDefinition managed = withRoles(team(), List.of(), List.of(), List.of("office-admin"));

        EntryWiring manager = wired(member(BOB, "office-admin"), stored, managed);
        manager.update().update(ORG, CALENDAR, stored.getId(), renamed(null));
        assertThat(stored.getTitle()).isEqualTo("Renamed");

        CalendarEntry other = timedEntry(ALICE, EntryVisibility.PUBLIC);
        EntryWiring booker = wired(member(BOB), other, managed);
        assertThatThrownBy(() -> booker.update().update(ORG, CALENDAR, other.getId(), renamed(null)))
                .isInstanceOf(CalendarAccessDeniedException.class);
        assertThat(other.getTitle()).isEqualTo("Planning");
        verify(booker.facts, never()).updated(any());
    }

    @Test
    void anOrganizerWhoLostTheBookingRightCannotEditTheirOwnEntry() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);
        CalendarDefinition bookersOnly = withRoles(team(), List.of(), List.of("staff"), List.of());
        EntryWiring wiring = wired(member(ALICE, "guest"), stored, bookersOnly);

        assertThatThrownBy(() -> wiring.update().update(ORG, CALENDAR, stored.getId(), renamed(null)))
                .isInstanceOf(CalendarAccessDeniedException.class);
    }

    @Test
    void aStaleVersionIsRefusedAndAMissingOneOverwrites() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);
        stored.setVersion(4L);
        EntryWiring wiring = wired(member(ALICE), stored, team());

        assertThatThrownBy(() -> wiring.update().update(ORG, CALENDAR, stored.getId(), renamed(3L)))
                .isInstanceOf(StaleEntryException.class);
        verify(wiring.repository, never()).save(any());

        wiring.update().update(ORG, CALENDAR, stored.getId(), renamed(null));
        assertThat(stored.getTitle()).isEqualTo("Renamed");
    }

    @Test
    void theKindOfAnEntryCannotChangeAndTheNewContentKeepsTheRules() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);
        EntryWiring wiring = wired(member(ALICE), stored, team());
        BookingRequest toAllDay = new BookingRequest(EntryKind.ALL_DAY, "Day", null, null, null, null, null, null, null,
                LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-25"), null);
        BookingRequest backwards = new BookingRequest(EntryKind.TIMED, "x", null, null, null, null,
                Instant.parse("2026-12-01T12:00:00Z"), Instant.parse("2026-12-01T11:00:00Z"), null, null, null, null);

        assertThatThrownBy(() -> wiring.update().update(ORG, CALENDAR, stored.getId(), toAllDay))
                .isInstanceOfSatisfying(InvalidBookingException.class, e -> assertThat(e.getViolations()).singleElement().asString().contains("kind"));
        assertThatThrownBy(() -> wiring.update().update(ORG, CALENDAR, stored.getId(), backwards))
                .isInstanceOf(InvalidBookingException.class);
        assertThat(stored.getTitle()).isEqualTo("Planning");
    }

    @Test
    void anAnonymousCallerCannotEditAnEntryOfAPublicCalendar() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);
        EntryWiring wiring = wired(anonymous(), stored, calendar(CalendarVisibility.PUBLIC, PublicDetail.FULL));

        assertThatThrownBy(() -> wiring.update().update(ORG, CALENDAR, stored.getId(), renamed(null)))
                .isInstanceOf(CalendarAccessDeniedException.class);
        assertThatThrownBy(() -> wiring.cancel().cancel(ORG, CALENDAR, stored.getId()))
                .isInstanceOf(CalendarAccessDeniedException.class);
    }

    // --- cancel

    @Test
    void theOrganizerCancelsTheirEntryAndTheFactIsPublished() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);
        EntryWiring wiring = wired(member(ALICE), stored, team());

        wiring.cancel().cancel(ORG, CALENDAR, stored.getId());

        verify(wiring.repository).delete(stored);
        verify(wiring.facts).deleted(stored);
    }

    @Test
    void somebodyElseCannotCancelAndNothingIsDeleted() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);
        EntryWiring wiring = wired(member(BOB), stored, team());

        assertThatThrownBy(() -> wiring.cancel().cancel(ORG, CALENDAR, stored.getId()))
                .isInstanceOf(CalendarAccessDeniedException.class);
        verify(wiring.repository, never()).delete(any());
        verify(wiring.facts, never()).deleted(any());
    }

    @Test
    void aMissingEntryCannotBeCancelled() {
        CalendarEntry stored = timedEntry(ALICE, EntryVisibility.PUBLIC);
        EntryWiring wiring = wired(member(ALICE), stored, team());

        assertThatThrownBy(() -> wiring.cancel().cancel(ORG, CALENDAR, java.util.UUID.randomUUID()))
                .isInstanceOf(EntryNotFoundException.class);
    }
}
