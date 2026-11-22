package com.processpuzzle.calendar.entry.usecases;

import static com.processpuzzle.calendar.entry.EntryFixtures.ALICE;
import static com.processpuzzle.calendar.entry.EntryFixtures.BOB;
import static com.processpuzzle.calendar.entry.EntryFixtures.CALENDAR;
import static com.processpuzzle.calendar.entry.EntryFixtures.ORG;
import static com.processpuzzle.calendar.entry.EntryFixtures.anonymous;
import static com.processpuzzle.calendar.entry.EntryFixtures.calendar;
import static com.processpuzzle.calendar.entry.EntryFixtures.member;
import static com.processpuzzle.calendar.entry.EntryFixtures.withRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.EntryKind;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import com.processpuzzle.calendar.entry.EntryFixtures;
import com.processpuzzle.calendar.entry.EntryWiring;
import com.processpuzzle.calendar.entry.StubDirectory;
import com.processpuzzle.calendar.entry.domain.AttendanceResponse;
import com.processpuzzle.calendar.entry.domain.AttendeeRequest;
import com.processpuzzle.calendar.entry.domain.AttendeeType;
import com.processpuzzle.calendar.entry.domain.BookingRequest;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.EntryView;
import com.processpuzzle.calendar.entry.domain.EntryVisibility;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarAccessDeniedException;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarNotFoundException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidBookingException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidEntryRequestException;
import com.processpuzzle.core.security.CurrentPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Invitations: who may be invited, how the list is kept, what an invitee may see and answer, and what a time change does. */
class AttendeesTest {

    private static final String CAROL = "sub-carol";
    private static final StubDirectory PEOPLE = new StubDirectory(Map.of(ALICE, "Ann Organizer", BOB, "Bob Builder", CAROL, "Carol Carpenter"));

    private static BookingRequest timed(EntryVisibility visibility, List<AttendeeRequest> invitations) {
        return new BookingRequest(EntryKind.TIMED, "Planning", "Quarterly", "Room 1", "meeting", visibility,
                Instant.parse("2026-12-01T09:00:00Z"), Instant.parse("2026-12-01T10:00:00Z"), null, null, null, null, invitations);
    }

    private static BookingRequest moved(List<AttendeeRequest> invitations) {
        return new BookingRequest(EntryKind.TIMED, "Planning", "Quarterly", "Room 1", "meeting", EntryVisibility.PRIVATE,
                Instant.parse("2026-12-02T09:00:00Z"), Instant.parse("2026-12-02T10:00:00Z"), null, null, null, null, invitations);
    }

    private static BookingRequest sameTimes(List<AttendeeRequest> invitations) {
        return new BookingRequest(EntryKind.TIMED, "Planning, renamed", "Quarterly", "Room 1", "meeting", EntryVisibility.PRIVATE,
                Instant.parse("2026-12-01T09:00:00Z"), Instant.parse("2026-12-01T10:00:00Z"), null, null, null, null, invitations);
    }

    private static AttendeeRequest user(String subject) {
        return new AttendeeRequest(AttendeeType.USER, subject, false);
    }

    private static AttendeeRequest role(String name) {
        return new AttendeeRequest(AttendeeType.ROLE, name, false);
    }

    /** The entry as it is stored, for the people who come to it later. */
    private static EntryWiring wiredFor(CurrentPrincipal principal, CalendarDefinition calendar, CalendarEntry stored) {
        EntryWiring wiring = new EntryWiring(principal, calendar, PEOPLE);
        when(wiring.repository.findByIdAndOrgKeyAndCalendarKey(stored.getId(), ORG, CALENDAR)).thenReturn(Optional.of(stored));
        return wiring;
    }

    private static CalendarDefinition open() {
        return calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.BUSY_ONLY);
    }

    // --- booking

    @Test
    void theInvitedPersonIsNamedByTheDirectoryAndTheOrganizerIsAcceptedFromTheStart() {
        EntryWiring wiring = new EntryWiring(member(ALICE), open(), PEOPLE);

        CalendarEntry saved = wiring.book().book(ORG, CALENDAR, timed(EntryVisibility.PRIVATE, List.of(user(BOB)))).entry();

        assertThat(saved.getAttendees()).extracting(a -> a.getPrincipal() + "/" + a.getDisplayName() + "/" + a.getResponse())
                .containsExactly(ALICE + "/Name of sub-alice/ACCEPTED", BOB + "/Bob Builder/NEEDS_ACTION");
    }

    @Test
    void anUnknownPersonIsRefusedAndTheRefusalNamesTheInvitation() {
        EntryWiring wiring = new EntryWiring(member(ALICE), open(), PEOPLE);

        assertThatThrownBy(() -> wiring.book().book(ORG, CALENDAR, timed(EntryVisibility.PUBLIC, List.of(user(BOB), user("sub-stranger")))))
                .isInstanceOfSatisfying(InvalidBookingException.class,
                        e -> assertThat(e.getViolations()).containsExactly("attendee 2 is not a member of this organization"));
    }

    @Test
    void withoutADirectoryNobodyCanBeInvitedByName() {
        EntryWiring wiring = new EntryWiring(member(ALICE), open());

        assertThatThrownBy(() -> wiring.book().book(ORG, CALENDAR, timed(EntryVisibility.PUBLIC, List.of(user(BOB)))))
                .isInstanceOf(InvalidBookingException.class);
    }

    @Test
    void aRoleIsInvitedByNameAndAnswersNothing() {
        EntryWiring wiring = new EntryWiring(member(ALICE), open(), PEOPLE);

        CalendarEntry saved = wiring.book().book(ORG, CALENDAR, timed(EntryVisibility.PUBLIC, List.of(role("staff")))).entry();

        assertThat(saved.getAttendees()).filteredOn(a -> a.getType() == AttendeeType.ROLE)
                .singleElement().satisfies(a -> {
                    assertThat(a.getPrincipal()).isEqualTo("staff");
                    assertThat(a.getResponse()).isNull();
                });
    }

    @Test
    void duplicatesAndTheOrganizerInTheListAreTakenOnce() {
        EntryWiring wiring = new EntryWiring(member(ALICE), open(), PEOPLE);

        CalendarEntry saved = wiring.book().book(ORG, CALENDAR, timed(EntryVisibility.PUBLIC, List.of(user(BOB), user(BOB), user(ALICE)))).entry();

        assertThat(saved.getAttendees()).extracting(a -> a.getPrincipal()).containsExactly(ALICE, BOB);
    }

    @Test
    void tooManyInvitationsAreRefused() {
        EntryWiring wiring = new EntryWiring(member(ALICE), open(), PEOPLE);
        List<AttendeeRequest> many = java.util.stream.IntStream.range(0, 51).mapToObj(i -> role("role-" + i)).toList();

        assertThatThrownBy(() -> wiring.book().book(ORG, CALENDAR, timed(EntryVisibility.PUBLIC, many)))
                .isInstanceOfSatisfying(InvalidBookingException.class, e -> assertThat(e.getViolations()).containsExactly("an entry may invite at most 50 attendees"));
    }

    // --- the invitee's view

    @Test
    void aPersonInvitedByNameSeesAPrivateEntryInFullAndMayAnswerItWithoutTheBookingRight() {
        CalendarDefinition bookersOnly = withRoles(open(), List.of(), List.of("staff"), List.of());
        EntryWiring organizer = new EntryWiring(member(ALICE, "staff"), bookersOnly, PEOPLE);
        CalendarEntry stored = organizer.book().book(ORG, CALENDAR, timed(EntryVisibility.PRIVATE, List.of(user(BOB)))).entry();

        EntryView bob = wiredFor(member(BOB), bookersOnly, stored).get().get(ORG, CALENDAR, stored.getId());
        assertThat(bob.busy()).as("invited, so the detail is theirs").isFalse();
        assertThat(bob.invited()).isTrue();
        assertThat(bob.canRespond()).isTrue();
        assertThat(bob.canEdit()).isFalse();

        EntryView answered = wiredFor(member(BOB), bookersOnly, stored).respond().answer(ORG, CALENDAR, stored.getId(), AttendanceResponse.TENTATIVE);
        assertThat(answered.entry().invitationOf(BOB).orElseThrow().getResponse()).isEqualTo(AttendanceResponse.TENTATIVE);
        assertThat(answered.entry().invitationOf(BOB).orElseThrow().getRespondedAt()).isNotNull();
    }

    @Test
    void someoneNotInvitedSeesAPrivateEntryAsBusyTimeOnly() {
        CalendarEntry stored = new EntryWiring(member(ALICE), open(), PEOPLE)
                .book().book(ORG, CALENDAR, timed(EntryVisibility.PRIVATE, List.of(user(BOB)))).entry();

        EntryView carol = wiredFor(member(CAROL), open(), stored).get().get(ORG, CALENDAR, stored.getId());

        assertThat(carol.busy()).isTrue();
        assertThat(carol.invited()).isFalse();
        assertThat(carol.canRespond()).isFalse();
    }

    @Test
    void aRoleInvitationMakesTheRolesMembersInvitedButNotCanRespond() {
        CalendarEntry stored = new EntryWiring(member(ALICE), open(), PEOPLE)
                .book().book(ORG, CALENDAR, timed(EntryVisibility.PRIVATE, List.of(role("staff")))).entry();

        EntryView staff = wiredFor(member(CAROL, "staff"), open(), stored).get().get(ORG, CALENDAR, stored.getId());
        EntryView guest = wiredFor(member(CAROL, "guest"), open(), stored).get().get(ORG, CALENDAR, stored.getId());

        assertThat(staff.invited()).isTrue();
        assertThat(staff.busy()).isFalse();
        assertThat(staff.canRespond()).isFalse();
        assertThat(guest.invited()).isFalse();
    }

    @Test
    void rolesOfAnotherOrganizationInvitePeopleToNothing() {
        CalendarEntry stored = new EntryWiring(member(ALICE), open(), PEOPLE)
                .book().book(ORG, CALENDAR, timed(EntryVisibility.PRIVATE, List.of(role("staff")))).entry();

        EntryView foreign = wiredFor(EntryFixtures.foreigner(CAROL, "staff"), calendar(CalendarVisibility.PUBLIC, PublicDetail.FULL), stored)
                .get().get(ORG, CALENDAR, stored.getId());

        assertThat(foreign.invited()).isFalse();
        assertThat(foreign.busy()).isTrue();
    }

    @Test
    void onlyTheOrganizerMayEditAnEntryWithInvitations() {
        CalendarEntry stored = new EntryWiring(member(ALICE), open(), PEOPLE)
                .book().book(ORG, CALENDAR, timed(EntryVisibility.PUBLIC, List.of(user(BOB)))).entry();

        assertThat(wiredFor(member(ALICE), open(), stored).get().get(ORG, CALENDAR, stored.getId()).canEdit()).isTrue();
        assertThat(wiredFor(member(BOB), open(), stored).get().get(ORG, CALENDAR, stored.getId()).canEdit()).isFalse();
    }

    // --- answering

    @Test
    void onlyThePersonInvitedByNameAnswers() {
        CalendarEntry stored = new EntryWiring(member(ALICE), open(), PEOPLE)
                .book().book(ORG, CALENDAR, timed(EntryVisibility.PUBLIC, List.of(user(BOB), role("staff")))).entry();

        assertThatThrownBy(() -> wiredFor(member(CAROL), open(), stored).respond().answer(ORG, CALENDAR, stored.getId(), AttendanceResponse.ACCEPTED))
                .isInstanceOf(CalendarAccessDeniedException.class).hasMessageContaining("not invited");
        assertThatThrownBy(() -> wiredFor(member(CAROL, "staff"), open(), stored).respond().answer(ORG, CALENDAR, stored.getId(), AttendanceResponse.ACCEPTED))
                .isInstanceOf(CalendarAccessDeniedException.class).hasMessageContaining("not invited");
        assertThatThrownBy(() -> wiredFor(anonymous(), open(), stored).respond().answer(ORG, CALENDAR, stored.getId(), AttendanceResponse.ACCEPTED))
                .isInstanceOf(CalendarNotFoundException.class);
    }

    @Test
    void noAnswerIsNotAnAnswer() {
        CalendarEntry stored = new EntryWiring(member(ALICE), open(), PEOPLE)
                .book().book(ORG, CALENDAR, timed(EntryVisibility.PUBLIC, List.of(user(BOB)))).entry();

        assertThatThrownBy(() -> wiredFor(member(BOB), open(), stored).respond().answer(ORG, CALENDAR, stored.getId(), AttendanceResponse.NEEDS_ACTION))
                .isInstanceOf(InvalidEntryRequestException.class);
    }

    // --- changing the entry

    @Test
    void movingTheTimeAsksEveryInviteeAgainAndTheOrganizerStaysAccepted() {
        EntryWiring organizer = new EntryWiring(member(ALICE), open(), PEOPLE);
        CalendarEntry stored = organizer.book().book(ORG, CALENDAR, timed(EntryVisibility.PUBLIC, List.of(user(BOB)))).entry();
        wiredFor(member(BOB), open(), stored).respond().answer(ORG, CALENDAR, stored.getId(), AttendanceResponse.ACCEPTED);

        EntryWiring change = wiredFor(member(ALICE), open(), stored);
        change.update().update(ORG, CALENDAR, stored.getId(), moved(List.of(user(BOB))));

        assertThat(stored.invitationOf(BOB).orElseThrow().getResponse()).isEqualTo(AttendanceResponse.NEEDS_ACTION);
        assertThat(stored.invitationOf(ALICE).orElseThrow().getResponse()).isEqualTo(AttendanceResponse.ACCEPTED);
    }

    @Test
    void changingOnlyTheWordsKeepsTheAnswers() {
        EntryWiring organizer = new EntryWiring(member(ALICE), open(), PEOPLE);
        CalendarEntry stored = organizer.book().book(ORG, CALENDAR, timed(EntryVisibility.PUBLIC, List.of(user(BOB)))).entry();
        wiredFor(member(BOB), open(), stored).respond().answer(ORG, CALENDAR, stored.getId(), AttendanceResponse.DECLINED);

        wiredFor(member(ALICE), open(), stored).update().update(ORG, CALENDAR, stored.getId(), sameTimes(List.of(user(BOB))));

        assertThat(stored.invitationOf(BOB).orElseThrow().getResponse()).isEqualTo(AttendanceResponse.DECLINED);
    }

    @Test
    void aChangeWithoutInviteesKeepsThemAndAChangeWithInviteesReplacesThem() {
        EntryWiring organizer = new EntryWiring(member(ALICE), open(), PEOPLE);
        CalendarEntry stored = organizer.book().book(ORG, CALENDAR, timed(EntryVisibility.PUBLIC, List.of(user(BOB)))).entry();

        wiredFor(member(ALICE), open(), stored).update().update(ORG, CALENDAR, stored.getId(), sameTimesWithoutInvitees());
        assertThat(stored.getAttendees()).extracting(a -> a.getPrincipal()).containsExactly(ALICE, BOB);

        wiredFor(member(ALICE), open(), stored).update().update(ORG, CALENDAR, stored.getId(), sameTimes(List.of(user(CAROL))));
        assertThat(stored.getAttendees()).extracting(a -> a.getPrincipal()).containsExactly(ALICE, CAROL);
    }

    private static BookingRequest sameTimesWithoutInvitees() {
        return sameTimes(null);
    }
}
