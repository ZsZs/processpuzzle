package com.processpuzzle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.processpuzzle.calendar.definition.domain.EntryKind;
import com.processpuzzle.calendar.definition.usecases.inbound.ImportCalendarDefinitions;
import com.processpuzzle.calendar.entry.domain.BookingRequest;
import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.domain.EntryView;
import com.processpuzzle.calendar.entry.domain.OccurrenceView;
import com.processpuzzle.calendar.entry.domain.EntryVisibility;
import com.processpuzzle.calendar.entry.domain.Recurrence;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarAccessDeniedException;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarNotFoundException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidBookingException;
import com.processpuzzle.calendar.entry.usecases.inbound.BookEntry;
import com.processpuzzle.calendar.entry.usecases.inbound.CancelEntry;
import com.processpuzzle.calendar.entry.usecases.inbound.GetCalendar;
import com.processpuzzle.calendar.entry.usecases.inbound.GetEntry;
import com.processpuzzle.calendar.entry.usecases.inbound.ListOccurrences;
import com.processpuzzle.security.SecurityTestTokens;
import com.processpuzzle.shared.event.PlatformEvent;
import com.processpuzzle.shared.event.PlatformEventAction;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Calendars through the real platform: the released definitions, the realm roles in the token and the database
 * decide who sees and books what. In an organization of its own, with a public calendar that shows outsiders only busy
 * time, a calendar for signed-in members, and a room for staff.
 *
 * <p>The tokens are real {@code Jwt}s in the security context, so "a member of this organization" means what it means
 * on the wire: a valid token from this organization's realm. Anything else — no token, another tenant's — is an
 * outsider.
 */
@SpringBootTest
@RecordApplicationEvents
class CalendarIntegrationTest {

    private static final String ORG = "calendar-integration";
    private static final String FROM = "2027-03-01T00:00:00Z";
    private static final String TO = "2027-03-08T00:00:00Z";

    private static final String CALENDARS = """
            calendar-definitions:
              - key: public-events
                name: Public events
                visibility: PUBLIC
                publicDetail: BUSY_ONLY
              - key: team-calendar
                name: Team calendar
                visibility: AUTHENTICATED
                publicDetail: FULL
                allowedKinds: [TIMED, ALL_DAY, RECURRING]
              - key: meeting-room
                name: Meeting room
                kind: RESOURCE
                visibility: ROLES
                readRoles: [staff]
                bookRoles: [staff]
                manageRoles: [office-admin]
                allowedKinds: [TIMED]
                slotMinutes: 30
                minDurationMinutes: 30
            """;

    @Autowired private ImportCalendarDefinitions importCalendars;
    @Autowired private GetCalendar getCalendar;
    @Autowired private BookEntry book;
    @Autowired private GetEntry getEntry;
    @Autowired private CancelEntry cancel;
    @Autowired private ListOccurrences occurrences;
    @Autowired private CalendarEntryRepository entries;
    @Autowired private ApplicationEvents events;
    @Autowired private PlatformTransactionManager transactions;

    @BeforeEach
    void seed() throws IOException {
        new TransactionTemplate(transactions).executeWithoutResult(status -> entries.eraseByOrgKey(ORG));
        assertThat(importCalendars.execute(ORG, new ByteArrayInputStream(CALENDARS.getBytes(StandardCharsets.UTF_8)), true).errors()).isEmpty();
    }

    @AfterEach
    void signOut() {
        SecurityTestTokens.clear();
    }

    private static BookingRequest meeting(String title) {
        return new BookingRequest(EntryKind.TIMED, title, "Details of " + title, "Room 1", "meeting", EntryVisibility.PUBLIC,
                Instant.parse("2027-03-02T09:00:00Z"), Instant.parse("2027-03-02T10:00:00Z"), null, null, null, null);
    }

    private List<OccurrenceView> window(String... calendars) {
        return occurrences.list(ORG, List.of(calendars), Instant.parse(FROM), Instant.parse(TO));
    }

    @Test
    void anonymousVisitorsSeeOnlyThePublicCalendar() {
        SecurityTestTokens.clear();

        assertThat(getCalendar.get(ORG, "public-events").access().canRead()).isTrue();
        assertThat(getCalendar.get(ORG, "public-events").access().canBook()).isFalse();
        assertThatThrownBy(() -> getCalendar.get(ORG, "team-calendar")).isInstanceOf(CalendarNotFoundException.class);
        assertThatThrownBy(() -> getCalendar.get(ORG, "meeting-room")).isInstanceOf(CalendarNotFoundException.class);
        assertThat(window("team-calendar", "meeting-room")).isEmpty();
    }

    @Test
    void aMemberBooksAndOutsidersSeeOnlyWhenItTakesPlace() {
        SecurityTestTokens.authenticateAs(ORG, "sub-ann");
        EntryView booked = book.book(ORG, "public-events", meeting("Open day"));
        assertThat(booked.entry().getId()).isNotNull();
        assertThat(booked.mine()).isTrue();
        assertThat(booked.busy()).isFalse();

        // Another member of the organization books nothing special, but a booker sees the content.
        SecurityTestTokens.authenticateAs(ORG, "sub-bob");
        assertThat(window("public-events")).singleElement().satisfies(view -> {
            assertThat(view.view().busy()).isFalse();
            assertThat(view.view().entry().getTitle()).isEqualTo("Open day");
            assertThat(view.view().mine()).isFalse();
        });

        // An anonymous visitor and another tenant's user see the time and nothing else.
        SecurityTestTokens.clear();
        assertThat(window("public-events")).singleElement().satisfies(view -> {
            assertThat(view.view().busy()).isTrue();
            assertThat(view.view().canEdit()).isFalse();
        });
        SecurityTestTokens.authenticateAs("some-other-org", "sub-eve", "staff", "office-admin");
        assertThat(window("public-events")).singleElement().satisfies(view -> assertThat(view.view().busy()).isTrue());
        assertThatThrownBy(() -> book.book(ORG, "public-events", meeting("Hijack"))).isInstanceOf(CalendarAccessDeniedException.class);
        assertThat(entries.findAll()).filteredOn(entry -> ORG.equals(entry.getOrgKey())).hasSize(1);
    }

    @Test
    void aPrivateEntryIsBusyToOtherMembersButNotToItsOrganizer() {
        SecurityTestTokens.authenticateAs(ORG, "sub-ann");
        BookingRequest secret = new BookingRequest(EntryKind.TIMED, "Salary review", null, null, null, EntryVisibility.PRIVATE,
                Instant.parse("2027-03-02T09:00:00Z"), Instant.parse("2027-03-02T10:00:00Z"), null, null, null, null);
        book.book(ORG, "team-calendar", secret);

        assertThat(window("team-calendar")).singleElement().satisfies(view -> assertThat(view.view().busy()).isFalse());
        SecurityTestTokens.authenticateAs(ORG, "sub-bob");
        assertThat(window("team-calendar")).singleElement().satisfies(view -> assertThat(view.view().busy()).isTrue());
    }

    @Test
    void theRoomIsStaffOnlyAndAnOfficeAdminCancelsOthersBookings() {
        SecurityTestTokens.authenticateAs(ORG, "sub-ann");
        assertThatThrownBy(() -> getCalendar.get(ORG, "meeting-room")).isInstanceOf(CalendarNotFoundException.class);

        SecurityTestTokens.authenticateAs(ORG, "sub-bob", "staff");
        EntryView booked = book.book(ORG, "meeting-room", meeting("Planning"));
        assertThat(getCalendar.get(ORG, "meeting-room").access().canManage()).isFalse();

        SecurityTestTokens.authenticateAs(ORG, "sub-cid", "staff");
        assertThatThrownBy(() -> cancel.cancel(ORG, "meeting-room", booked.entry().getId())).isInstanceOf(CalendarAccessDeniedException.class);

        SecurityTestTokens.authenticateAs(ORG, "sub-dee", "office-admin");
        cancel.cancel(ORG, "meeting-room", booked.entry().getId());
        assertThat(entries.findById(booked.entry().getId())).isEmpty();
    }

    @Test
    void theCalendarRulesAreEnforcedAndNothingIsStoredWhenTheyAreBroken() {
        SecurityTestTokens.authenticateAs(ORG, "sub-bob", "staff");
        BookingRequest offSlot = new BookingRequest(EntryKind.TIMED, "Quick chat", null, null, null, null,
                Instant.parse("2027-03-02T09:10:00Z"), Instant.parse("2027-03-02T09:20:00Z"), null, null, null, null);

        assertThatThrownBy(() -> book.book(ORG, "meeting-room", offSlot))
                .isInstanceOfSatisfying(InvalidBookingException.class, e -> assertThat(e.getViolations()).hasSize(2));
        assertThat(window("meeting-room")).isEmpty();
    }

    @Test
    void aWeeklySeriesIsFoundInEveryWindowItFallsIn() {
        SecurityTestTokens.authenticateAs(ORG, "sub-ann");
        BookingRequest weekly = new BookingRequest(EntryKind.RECURRING, "Stand-up", null, null, null, null,
                Instant.parse("2027-03-02T08:00:00Z"), Instant.parse("2027-03-02T08:15:00Z"), "Europe/Budapest", null, null, null,
                null, new Recurrence(Recurrence.Frequency.WEEKLY, 3, null));
        EntryView series = book.book(ORG, "team-calendar", weekly);

        // The window of the second week, and of the third: the series reaches both, in its own local time.
        assertThat(occurrences.list(ORG, List.of("team-calendar"), Instant.parse("2027-03-08T00:00:00Z"), Instant.parse("2027-03-15T00:00:00Z")))
                .singleElement().satisfies(occurrence -> {
                    assertThat(occurrence.view().entry().getId()).isEqualTo(series.entry().getId());
                    assertThat(occurrence.span().recurrenceId()).isEqualTo(java.time.LocalDate.parse("2027-03-09"));
                    assertThat(occurrence.span().start()).isEqualTo(Instant.parse("2027-03-09T08:00:00Z"));
                });
        // The fourth occurrence would be the fourth, which a count of three does not have.
        assertThat(occurrences.list(ORG, List.of("team-calendar"), Instant.parse("2027-03-22T00:00:00Z"), Instant.parse("2027-03-29T00:00:00Z"))).isEmpty();
    }

    @Test
    void bookingPublishesADurableFactAboutTheEntry() {
        SecurityTestTokens.authenticateAs(ORG, "sub-ann");

        EntryView booked = book.book(ORG, "team-calendar", meeting("Kick-off"));

        assertThat(events.stream(PlatformEvent.class)
                .filter(event -> "calendar-entry".equals(event.subjectType()) && ORG.equals(event.orgKey())))
                .singleElement().satisfies(event -> {
                    assertThat(event.action()).isEqualTo(PlatformEventAction.CREATED);
                    assertThat(event.subjectId()).isEqualTo(booked.entry().getId().toString());
                    assertThat(event.payload()).containsEntry("calendarKey", "team-calendar").containsEntry("title", "Kick-off");
                });
    }

    @Test
    void anEntryOfAnotherOrganizationIsNeverReachableThroughThisOne() {
        SecurityTestTokens.authenticateAs(ORG, "sub-ann");
        EntryView booked = book.book(ORG, "team-calendar", meeting("Ours"));

        assertThatThrownBy(() -> getEntry.get("some-other-org", "team-calendar", booked.entry().getId()))
                .isInstanceOf(CalendarNotFoundException.class);
    }
}
