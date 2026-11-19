package com.processpuzzle.calendar.entry.usecases;

import static com.processpuzzle.calendar.entry.EntryFixtures.ALICE;
import static com.processpuzzle.calendar.entry.EntryFixtures.CALENDAR;
import static com.processpuzzle.calendar.entry.EntryFixtures.ORG;
import static com.processpuzzle.calendar.entry.EntryFixtures.anonymous;
import static com.processpuzzle.calendar.entry.EntryFixtures.calendar;
import static com.processpuzzle.calendar.entry.EntryFixtures.foreigner;
import static com.processpuzzle.calendar.entry.EntryFixtures.member;
import static com.processpuzzle.calendar.entry.EntryFixtures.withRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.processpuzzle.calendar.entry.usecases.exception.InvalidBookingException;
import com.processpuzzle.core.security.CurrentPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BookEntryTest {

    private static BookingRequest timed() {
        return new BookingRequest(EntryKind.TIMED, "  Planning ", "Quarterly", "Room 1", "meeting", EntryVisibility.PRIVATE,
                Instant.parse("2026-12-01T09:00:00Z"), Instant.parse("2026-12-01T10:00:00Z"), null, null, null, 7L);
    }

    private static EntryWiring wired(CurrentPrincipal principal, CalendarDefinition calendar) {
        return new EntryWiring(principal, calendar);
    }

    @Test
    void aMemberBooksAndBecomesTheOrganizer() {
        EntryWiring wiring = wired(member(ALICE), calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.BUSY_ONLY));

        EntryView booked = wiring.book().book(ORG, CALENDAR, timed());

        CalendarEntry saved = booked.entry();
        assertThat(saved.getOrgKey()).isEqualTo(ORG);
        assertThat(saved.getCalendarKey()).isEqualTo(CALENDAR);
        assertThat(saved.getTitle()).isEqualTo("Planning");
        assertThat(saved.getVisibility()).isEqualTo(EntryVisibility.PRIVATE);
        assertThat(saved.getOrganizerSub()).isEqualTo(ALICE);
        assertThat(saved.getOrganizerDisplayName()).isEqualTo("Name of sub-alice");
        assertThat(saved.getTimeZone()).as("defaults to the organization zone").isEqualTo("Europe/Budapest");
        assertThat(saved.getId()).isNotNull();
        assertThat(booked.mine()).isTrue();
        assertThat(booked.busy()).as("the organizer sees their own private entry").isFalse();
        assertThat(booked.canEdit()).isTrue();
        verify(wiring.facts).created(saved);
    }

    @Test
    void anAllDayEntryKeepsItsDatesAndNoZone() {
        EntryWiring wiring = wired(member(ALICE), calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.FULL));
        BookingRequest holiday = new BookingRequest(EntryKind.ALL_DAY, "Holiday", null, null, null, null, null, null, null,
                LocalDate.parse("2026-12-24"), LocalDate.parse("2026-12-27"), null);

        CalendarEntry saved = wiring.book().book(ORG, CALENDAR, holiday).entry();

        assertThat(saved.getStartDate()).isEqualTo(LocalDate.parse("2026-12-24"));
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.parse("2026-12-27"));
        assertThat(saved.getTimeZone()).isNull();
        assertThat(saved.getVisibility()).isEqualTo(EntryVisibility.PUBLIC);
    }

    @Test
    void anAnonymousCallerCannotBookEvenOnAPublicCalendar() {
        EntryWiring wiring = wired(anonymous(), calendar(CalendarVisibility.PUBLIC, PublicDetail.FULL));

        assertThatThrownBy(() -> wiring.book().book(ORG, CALENDAR, timed())).isInstanceOf(CalendarAccessDeniedException.class);
        verify(wiring.repository, never()).save(any());
        verify(wiring.facts, never()).created(any());
    }

    @Test
    void aSignedInUserOfAnotherOrganizationCannotBookOnAPublicCalendar() {
        EntryWiring wiring = wired(foreigner(ALICE), calendar(CalendarVisibility.PUBLIC, PublicDetail.FULL));

        assertThatThrownBy(() -> wiring.book().book(ORG, CALENDAR, timed())).isInstanceOf(CalendarAccessDeniedException.class);
        verify(wiring.repository, never()).save(any());
    }

    @Test
    void aCallerWhoMayNotReadTheCalendarIsToldItDoesNotExist() {
        EntryWiring wiring = wired(member(ALICE), withRoles(calendar(CalendarVisibility.ROLES, PublicDetail.FULL), List.of("staff"), List.of(), List.of()));

        assertThatThrownBy(() -> wiring.book().book(ORG, CALENDAR, timed())).isInstanceOf(CalendarNotFoundException.class);
    }

    @Test
    void aReaderWithoutTheBookingRoleIsRefused() {
        EntryWiring wiring = wired(member(ALICE, "guest"),
                withRoles(calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.FULL), List.of(), List.of("staff"), List.of()));

        assertThatThrownBy(() -> wiring.book().book(ORG, CALENDAR, timed())).isInstanceOf(CalendarAccessDeniedException.class);
    }

    @Test
    void anEntryThatBreaksTheCalendarRulesIsRefusedWithEveryViolation() {
        CalendarDefinition room = calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.FULL);
        room.setSlotMinutes(30);
        room.setMinDurationMinutes(60);
        EntryWiring wiring = wired(member(ALICE), room);
        BookingRequest tooShortAndOffSlot = new BookingRequest(EntryKind.TIMED, "x", null, null, null, null,
                Instant.parse("2026-12-01T09:10:00Z"), Instant.parse("2026-12-01T09:20:00Z"), null, null, null, null);

        assertThatThrownBy(() -> wiring.book().book(ORG, CALENDAR, tooShortAndOffSlot))
                .isInstanceOfSatisfying(InvalidBookingException.class, e -> assertThat(e.getViolations()).hasSize(2));
        verify(wiring.repository, never()).save(any());
        verify(wiring.facts, never()).created(any());
    }

    @Test
    void theOrganizerSubjectGoesToTheRepositoryAndNotIntoAnyView() {
        EntryWiring wiring = wired(member(ALICE), calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.FULL));

        wiring.book().book(ORG, CALENDAR, timed());

        ArgumentCaptor<CalendarEntry> saved = ArgumentCaptor.forClass(CalendarEntry.class);
        verify(wiring.repository).save(saved.capture());
        assertThat(saved.getValue().isOrganizedBy(ALICE)).isTrue();
    }
}
