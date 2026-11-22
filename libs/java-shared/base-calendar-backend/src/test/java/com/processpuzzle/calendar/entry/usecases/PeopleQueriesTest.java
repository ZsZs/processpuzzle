package com.processpuzzle.calendar.entry.usecases;

import static com.processpuzzle.calendar.entry.EntryFixtures.ALICE;
import static com.processpuzzle.calendar.entry.EntryFixtures.BOB;
import static com.processpuzzle.calendar.entry.EntryFixtures.CALENDAR;
import static com.processpuzzle.calendar.entry.EntryFixtures.ORG;
import static com.processpuzzle.calendar.entry.EntryFixtures.anonymous;
import static com.processpuzzle.calendar.entry.EntryFixtures.calendar;
import static com.processpuzzle.calendar.entry.EntryFixtures.member;
import static com.processpuzzle.calendar.entry.EntryFixtures.provider;
import static com.processpuzzle.calendar.entry.EntryFixtures.timedEntry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import com.processpuzzle.calendar.entry.EntryFixtures;
import com.processpuzzle.calendar.entry.EntryWiring;
import com.processpuzzle.calendar.entry.StubDirectory;
import com.processpuzzle.calendar.entry.domain.Attendee;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.domain.EntryVisibility;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarAccessDeniedException;
import com.processpuzzle.calendar.entry.usecases.exception.InvalidEntryRequestException;
import com.processpuzzle.calendar.entry.usecases.inbound.CallerMembership;
import com.processpuzzle.calendar.entry.usecases.inbound.EraseCalendarUser;
import com.processpuzzle.calendar.entry.usecases.inbound.SearchDirectory;
import com.processpuzzle.calendar.entry.usecases.outbound.CalendarDirectoryPort;
import com.processpuzzle.core.security.CurrentPrincipal;
import com.processpuzzle.core.tenancy.OrganizationGuard;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The people-facing queries: what a caller has been invited to, the directory search, and erasing a user. */
class PeopleQueriesTest {

    private static final Instant FROM = Instant.parse("2026-12-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-12-08T00:00:00Z");
    private static final StubDirectory PEOPLE = new StubDirectory(Map.of(ALICE, "Ann Organizer", BOB, "Bob Builder", "sub-carol", "Carol Carpenter"));

    // --- my occurrences

    @Test
    void aMemberSeesWhatTheyOrganizeOrAreInvitedTo() {
        EntryWiring wiring = new EntryWiring(member(BOB, "staff"), calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.BUSY_ONLY));
        CalendarEntry invitedByName = timedEntry(ALICE, EntryVisibility.PRIVATE);
        invitedByName.reconcileAttendees(List.of(Attendee.user(BOB, "Bob Builder", false, null, FROM)), organizerOf(ALICE), false, FROM);
        when(wiring.repository.findMine(eq(ORG), eq(BOB), anyCollection(), any(), any(), any(), any())).thenReturn(List.of(invitedByName));

        var mine = wiring.myOccurrences().list(ORG, FROM, TO);

        assertThat(mine).singleElement().satisfies(view -> {
            assertThat(view.view().invited()).isTrue();
            assertThat(view.view().busy()).as("invited, so the detail is theirs").isFalse();
            assertThat(view.view().canRespond()).isTrue();
        });
    }

    @Test
    void roleNamesOnlyCountForMembersAndAsTheRoleOfThisOrganization() {
        CalendarEntry roleEntry = timedEntry(ALICE, EntryVisibility.PUBLIC);
        roleEntry.reconcileAttendees(List.of(Attendee.role("staff", false)), organizerOf(ALICE), false, FROM);
        EntryWiring member = new EntryWiring(member(BOB, "staff"), calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.BUSY_ONLY));
        when(member.repository.findMine(eq(ORG), eq(BOB), anyCollection(), any(), any(), any(), any())).thenReturn(List.of(roleEntry));

        assertThat(member.myOccurrences().list(ORG, FROM, TO)).singleElement().satisfies(view -> assertThat(view.view().invited()).isTrue());

        EntryWiring foreigner = new EntryWiring(EntryFixtures.foreigner(BOB, "staff"), calendar(CalendarVisibility.PUBLIC, PublicDetail.FULL));
        when(foreigner.repository.findMine(eq(ORG), any(), anyCollection(), any(), any(), any(), any())).thenReturn(List.of(roleEntry));

        assertThat(foreigner.myOccurrences().list(ORG, FROM, TO)).as("a member of another organization has no invitations here").isEmpty();
    }

    @Test
    void aCalendarTheCallerMayNotReadIsLeftOut() {
        EntryWiring wiring = new EntryWiring(member(BOB), calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.BUSY_ONLY));
        CalendarEntry readable = timedEntry(ALICE, EntryVisibility.PUBLIC);
        CalendarEntry hidden = timedEntry(ALICE, EntryVisibility.PUBLIC);
        hidden.setCalendarKey("unreleased");
        when(wiring.repository.findMine(eq(ORG), eq(BOB), anyCollection(), any(), any(), any(), any())).thenReturn(List.of(readable, hidden));

        assertThat(wiring.myOccurrences().list(ORG, FROM, TO)).singleElement()
                .satisfies(view -> assertThat(view.view().entry().getCalendarKey()).isEqualTo(CALENDAR));
    }

    @Test
    void anonymousCallersHaveNoInvitations() {
        EntryWiring wiring = new EntryWiring(anonymous(), calendar(CalendarVisibility.PUBLIC, PublicDetail.FULL));

        assertThat(wiring.myOccurrences().list(ORG, FROM, TO)).isEmpty();
        verify(wiring.repository, never()).findMine(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void theWindowIsCheckedAsForTheCalendars() {
        EntryWiring wiring = new EntryWiring(member(BOB), calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.BUSY_ONLY));

        assertThatThrownBy(() -> wiring.myOccurrences().list(ORG, TO, FROM)).isInstanceOf(InvalidEntryRequestException.class);
        assertThatThrownBy(() -> wiring.myOccurrences().list(ORG, FROM, FROM.plusSeconds(101L * 24 * 3600)))
                .isInstanceOf(InvalidEntryRequestException.class);
    }

    // --- directory search

    @Test
    void membersSearchTheDirectoryAndShortQueriesFindNothing() {
        SearchDirectory search = new SearchDirectory(provider(PEOPLE), new CallerMembership(member(ALICE)));

        assertThat(search.search(ORG, "Bob")).extracting(CalendarDirectoryPort.DirectoryMatch::displayName).containsExactly("Bob Builder");
        assertThat(search.search(ORG, "B")).isEmpty();
        assertThat(search.search(ORG, "  ")).isEmpty();
        assertThat(search.search(ORG, null)).isEmpty();
    }

    @Test
    void theSearchIsForMembersOnly() {
        SearchDirectory anonymousSearch = new SearchDirectory(provider(PEOPLE), new CallerMembership(anonymous()));
        SearchDirectory foreignSearch = new SearchDirectory(provider(PEOPLE), new CallerMembership(EntryFixtures.foreigner(ALICE)));

        assertThatThrownBy(() -> anonymousSearch.search(ORG, "Bob")).isInstanceOf(CalendarAccessDeniedException.class);
        assertThatThrownBy(() -> foreignSearch.search(ORG, "Bob")).isInstanceOf(CalendarAccessDeniedException.class);
    }

    @Test
    void theSearchReturnsAtMostTwentyMatches() {
        Map<String, String> many = new java.util.HashMap<>();
        for (int i = 0; i < 30; i++) {
            many.put("sub-" + i, "Member " + i);
        }
        SearchDirectory search = new SearchDirectory(provider(new StubDirectory(many)), new CallerMembership(member(ALICE)));

        assertThat(search.search(ORG, "Member")).hasSize(CalendarDirectoryPort.MAX_RESULTS);
    }

    @Test
    void withoutADirectoryAdapterNothingIsFound() {
        SearchDirectory search = new SearchDirectory(provider(null), new CallerMembership(member(ALICE)));

        assertThat(search.search(ORG, "Bob")).isEmpty();
    }

    // --- erasure

    private static CalendarEntry organizedBy(String organizer) {
        CalendarEntry entry = timedEntry(organizer, EntryVisibility.PUBLIC);
        entry.reconcileAttendees(List.of(), organizerOf(organizer), false, Instant.parse("2026-12-01T08:00:00Z"));
        return entry;
    }

    private static Attendee organizerOf(String subject) {
        return Attendee.user(subject, "Name of " + subject, false, com.processpuzzle.calendar.entry.domain.AttendanceResponse.ACCEPTED, FROM);
    }

    @Test
    void aUsersOwnEntriesStayButNoLongerNameThemAndTheirInvitationsGo() {
        CalendarEntryRepository entries = mock(CalendarEntryRepository.class);
        OrganizationGuard guard = mock(OrganizationGuard.class);
        CurrentPrincipal principal = member(ALICE);
        CalendarEntry aliceOrganizes = organizedBy(ALICE);
        aliceOrganizes.reconcileAttendees(List.of(Attendee.user(BOB, "Bob Builder", false, null, FROM)), organizerOf(ALICE), false, FROM);
        CalendarEntry bobOrganizes = organizedBy(BOB);
        bobOrganizes.reconcileAttendees(List.of(Attendee.user(ALICE, "Name of sub-alice", false, null, FROM)), organizerOf(BOB), false, FROM);
        when(entries.findByOrgKeyAndOrganizerSub(ORG, ALICE)).thenReturn(List.of(aliceOrganizes));
        when(entries.findInvitedBy(ORG, ALICE)).thenReturn(List.of(aliceOrganizes, bobOrganizes));

        EraseCalendarUser.Result result = new EraseCalendarUser(entries, guard, principal).erase(ORG, ALICE);

        assertThat(result.entries()).isEqualTo(1);
        assertThat(result.invitations()).as("the invitation to Bob's entry").isEqualTo(1);
        assertThat(aliceOrganizes.getOrganizerSub()).isEqualTo(EraseCalendarUser.ERASED);
        assertThat(aliceOrganizes.getOrganizerDisplayName()).isNull();
        assertThat(aliceOrganizes.getAttendees()).extracting(Attendee::getPrincipal).containsExactly(EraseCalendarUser.ERASED, BOB);
        assertThat(bobOrganizes.getAttendees()).extracting(Attendee::getPrincipal).containsExactly(BOB);
        verify(guard).requireAccess(ORG);
        verify(guard, never()).requireAdministration(any());
    }

    @Test
    void erasingSomebodyElseNeedsAdministration() {
        CalendarEntryRepository entries = mock(CalendarEntryRepository.class);
        OrganizationGuard guard = mock(OrganizationGuard.class);
        org.mockito.Mockito.doThrow(new CalendarAccessDeniedException("administration needed")).when(guard).requireAdministration(ORG);

        assertThatThrownBy(() -> new EraseCalendarUser(entries, guard, member(BOB)).erase(ORG, ALICE))
                .isInstanceOf(CalendarAccessDeniedException.class);
        verify(entries, never()).saveAll(any());
    }

    @Test
    void erasingNeedsAUserAndNeverTheErasedMarker() {
        EraseCalendarUser erase = new EraseCalendarUser(mock(CalendarEntryRepository.class), mock(OrganizationGuard.class), member(ALICE));

        assertThatThrownBy(() -> erase.erase(ORG, " ")).isInstanceOf(InvalidEntryRequestException.class);
        assertThatThrownBy(() -> erase.erase(ORG, EraseCalendarUser.ERASED)).isInstanceOf(InvalidEntryRequestException.class);
    }
}
