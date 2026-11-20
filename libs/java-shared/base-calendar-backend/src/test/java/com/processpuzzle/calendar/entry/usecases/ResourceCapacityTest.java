package com.processpuzzle.calendar.entry.usecases;

import static com.processpuzzle.calendar.entry.EntryFixtures.ALICE;
import static com.processpuzzle.calendar.entry.EntryFixtures.CALENDAR;
import static com.processpuzzle.calendar.entry.EntryFixtures.ORG;
import static com.processpuzzle.calendar.entry.EntryFixtures.calendar;
import static com.processpuzzle.calendar.entry.EntryFixtures.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarKind;
import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.EntryKind;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import com.processpuzzle.calendar.entry.EntryWiring;
import com.processpuzzle.calendar.entry.domain.BookingRequest;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.EntryVisibility;
import com.processpuzzle.calendar.entry.domain.Recurrence;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidBookingException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Capacity of a resource calendar, as the booking and the change of an entry see it. */
class ResourceCapacityTest {

    private static CalendarDefinition room(CalendarKind kind, int capacity) {
        CalendarDefinition definition = calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.BUSY_ONLY);
        definition.setKind(kind);
        definition.setCapacity(capacity);
        definition.setAllowedKinds(new ArrayList<>(List.of(EntryKind.TIMED, EntryKind.RECURRING)));
        return definition;
    }

    private static BookingRequest timed(String start, String end) {
        return new BookingRequest(EntryKind.TIMED, "Use", null, null, null, EntryVisibility.PUBLIC,
                Instant.parse(start), Instant.parse(end), null, null, null, null);
    }

    /** A wiring whose repository reports {@code existing} as the bookings already in place. */
    private static EntryWiring withBookings(CalendarDefinition definition, CalendarEntry... existing) {
        EntryWiring wiring = new EntryWiring(member(ALICE), definition);
        when(wiring.repository.findOverlapping(eq(ORG), any(), any(), any(), any(), any())).thenReturn(List.of(existing));
        return wiring;
    }

    private static CalendarEntry booked(String start, String end) {
        CalendarEntry entry = CalendarEntry.of(ORG, CALENDAR, timed(start, end), java.time.ZoneId.of("Europe/Budapest"));
        entry.setId(UUID.randomUUID());
        entry.setOrganizerSub("sub-bob");
        return entry;
    }

    @Test
    void aResourceTakesOneBookingAtATime() {
        EntryWiring wiring = withBookings(room(CalendarKind.RESOURCE, 1), booked("2026-12-01T08:00:00Z", "2026-12-01T09:00:00Z"));

        assertThatThrownBy(() -> wiring.book().book(ORG, CALENDAR, timed("2026-12-01T08:30:00Z", "2026-12-01T09:30:00Z")))
                .isInstanceOf(InvalidBookingException.class)
                .hasMessageContaining("no free place at 2026-12-01 09:30");

        // Touching is not overlapping: the next booking may start when the first one ends.
        assertThat(wiring.book().book(ORG, CALENDAR, timed("2026-12-01T09:00:00Z", "2026-12-01T10:00:00Z")).entry().getTitle()).isEqualTo("Use");
    }

    @Test
    void aResourceWithTwoPlacesRefusesOnlyTheThird() {
        CalendarDefinition twoPlaces = room(CalendarKind.RESOURCE, 2);
        EntryWiring one = withBookings(twoPlaces, booked("2026-12-01T08:00:00Z", "2026-12-01T09:00:00Z"));
        assertThat(one.book().book(ORG, CALENDAR, timed("2026-12-01T08:00:00Z", "2026-12-01T09:00:00Z")).entry()).isNotNull();

        EntryWiring two = withBookings(twoPlaces, booked("2026-12-01T08:00:00Z", "2026-12-01T09:00:00Z"),
                booked("2026-12-01T08:00:00Z", "2026-12-01T09:00:00Z"));
        assertThatThrownBy(() -> two.book().book(ORG, CALENDAR, timed("2026-12-01T08:00:00Z", "2026-12-01T09:00:00Z")))
                .isInstanceOf(InvalidBookingException.class).hasMessageContaining("capacity 2");
    }

    @Test
    void theEntryBeingChangedDoesNotCountAgainstItself() {
        CalendarEntry mine = booked("2026-12-01T08:00:00Z", "2026-12-01T09:00:00Z");
        mine.setOrganizerSub(ALICE);
        EntryWiring wiring = withBookings(room(CalendarKind.RESOURCE, 1), mine);
        when(wiring.repository.findByIdAndOrgKeyAndCalendarKey(eq(mine.getId()), eq(ORG), eq(CALENDAR))).thenReturn(Optional.of(mine));

        // Moved within its own slot: it would clash only with itself.
        assertThat(wiring.update().update(ORG, CALENDAR, mine.getId(), timed("2026-12-01T08:15:00Z", "2026-12-01T09:00:00Z")).entry()).isNotNull();
    }

    @Test
    void everyOccurrenceOfASeriesIsChecked() {
        // A weekly series on Tuesdays at 09:00 Budapest time: 1, 8 and 15 December. Somebody holds the room on the 8th.
        CalendarEntry someoneElse = booked("2026-12-08T08:30:00Z", "2026-12-08T09:30:00Z");
        EntryWiring wiring = withBookings(room(CalendarKind.RESOURCE, 1), someoneElse);
        BookingRequest weekly = new BookingRequest(EntryKind.RECURRING, "Stand-up", null, null, null, EntryVisibility.PUBLIC,
                Instant.parse("2026-12-01T08:00:00Z"), Instant.parse("2026-12-01T09:00:00Z"), null, null, null, null, null,
                new Recurrence(Recurrence.Frequency.WEEKLY, 3, null));

        assertThatThrownBy(() -> wiring.book().book(ORG, CALENDAR, weekly))
                .isInstanceOf(InvalidBookingException.class).hasMessageContaining("no free place at 2026-12-08 09:00");
    }

    @Test
    void aTeamCalendarIsNeverChecked() {
        EntryWiring wiring = withBookings(room(CalendarKind.TEAM, 1), booked("2026-12-01T08:00:00Z", "2026-12-01T09:00:00Z"));

        assertThat(wiring.book().book(ORG, CALENDAR, timed("2026-12-01T08:00:00Z", "2026-12-01T09:00:00Z")).entry()).isNotNull();
    }
}
