package com.processpuzzle.calendar.entry.usecases;

import static com.processpuzzle.calendar.entry.EntryFixtures.CALENDAR;
import static com.processpuzzle.calendar.entry.EntryFixtures.ORG;
import static com.processpuzzle.calendar.entry.EntryFixtures.ALICE;
import static com.processpuzzle.calendar.entry.EntryFixtures.anonymous;
import static com.processpuzzle.calendar.entry.EntryFixtures.calendar;
import static com.processpuzzle.calendar.entry.EntryFixtures.foreigner;
import static com.processpuzzle.calendar.entry.EntryFixtures.member;
import static com.processpuzzle.calendar.entry.EntryFixtures.staff;
import static com.processpuzzle.calendar.entry.EntryFixtures.withRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import com.processpuzzle.calendar.definition.usecases.inbound.FindCalendarDefinition;
import com.processpuzzle.calendar.entry.domain.CalendarAccess;
import com.processpuzzle.calendar.entry.usecases.exception.CalendarNotFoundException;
import com.processpuzzle.calendar.entry.usecases.inbound.ResolveCalendarAccess;
import com.processpuzzle.core.security.CurrentPrincipal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Who may do what with a calendar. The platform's own guard lets an anonymous caller through and treats one as
 * holding every role, so these cases pin the stricter reading a calendar needs: anonymous and foreign-tenant callers
 * see only what is public, and never book.
 */
class ResolveCalendarAccessTest {

    private FindCalendarDefinition definitions;

    @BeforeEach
    void setUp() {
        definitions = mock(FindCalendarDefinition.class);
    }

    private CalendarAccess access(CurrentPrincipal principal, CalendarDefinition definition) {
        return new ResolveCalendarAccess(definitions, principal).of(ORG, definition);
    }

    // --- PUBLIC

    @Test
    void anybodyReadsAPublicCalendarButOnlyMembersBook() {
        CalendarDefinition definition = calendar(CalendarVisibility.PUBLIC, PublicDetail.FULL);

        assertThat(access(anonymous(), definition)).isEqualTo(new CalendarAccess(true, false, false, true));
        assertThat(access(foreigner(ALICE), definition)).isEqualTo(new CalendarAccess(true, false, false, true));
        assertThat(access(member(ALICE), definition)).isEqualTo(new CalendarAccess(true, true, false, true));
    }

    @Test
    void anAnonymousReaderOfAPublicCalendarSeesBusyTimeWhenTheCalendarSaysSo() {
        CalendarDefinition definition = calendar(CalendarVisibility.PUBLIC, PublicDetail.BUSY_ONLY);

        assertThat(access(anonymous(), definition).fullDetail()).isFalse();
        assertThat(access(foreigner(ALICE), definition).fullDetail()).isFalse();
        assertThat(access(member(ALICE), definition).fullDetail()).isTrue();
    }

    // --- AUTHENTICATED

    @Test
    void anAuthenticatedCalendarIsForMembersOnly() {
        CalendarDefinition definition = calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.FULL);

        assertThat(access(anonymous(), definition)).isEqualTo(CalendarAccess.NONE);
        assertThat(access(foreigner(ALICE), definition)).isEqualTo(CalendarAccess.NONE);
        assertThat(access(member(ALICE), definition).canRead()).isTrue();
        assertThat(access(staff("staff-1"), definition).canRead()).isTrue();
    }

    // --- ROLES

    @Test
    void aRolesCalendarIsForMembersHoldingAReadRole() {
        CalendarDefinition definition = withRoles(calendar(CalendarVisibility.ROLES, PublicDetail.FULL), List.of("staff"), List.of(), List.of());

        assertThat(access(anonymous(), definition)).isEqualTo(CalendarAccess.NONE);
        assertThat(access(foreigner(ALICE, "staff"), definition)).isEqualTo(CalendarAccess.NONE);
        assertThat(access(member(ALICE), definition)).isEqualTo(CalendarAccess.NONE);
        assertThat(access(member(ALICE, "guest"), definition)).isEqualTo(CalendarAccess.NONE);
        assertThat(access(member(ALICE, "staff"), definition).canRead()).isTrue();
    }

    @Test
    void aManagerCanAlwaysReadWhatTheyManage() {
        CalendarDefinition definition = withRoles(calendar(CalendarVisibility.ROLES, PublicDetail.BUSY_ONLY),
                List.of("staff"), List.of(), List.of("office-admin"));

        CalendarAccess manager = access(member(ALICE, "office-admin"), definition);

        assertThat(manager).isEqualTo(new CalendarAccess(true, true, true, true));
    }

    // --- booking

    @Test
    void withoutBookingRolesAnyMemberWhoMayReadBooks() {
        CalendarDefinition definition = calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.BUSY_ONLY);

        CalendarAccess member = access(member(ALICE), definition);

        assertThat(member.canBook()).isTrue();
        assertThat(member.fullDetail()).as("a booker sees the content").isTrue();
    }

    @Test
    void withBookingRolesOnlyTheirHoldersBookAndOthersSeeWhatTheCalendarAllows() {
        CalendarDefinition definition = withRoles(calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.BUSY_ONLY),
                List.of(), List.of("staff"), List.of());

        assertThat(access(member(ALICE, "staff"), definition)).isEqualTo(new CalendarAccess(true, true, false, true));
        assertThat(access(member(ALICE, "guest"), definition)).isEqualTo(new CalendarAccess(true, false, false, false));
    }

    @Test
    void aBookingRoleDoesNotMakeAnUnreadableCalendarReadable() {
        CalendarDefinition definition = withRoles(calendar(CalendarVisibility.ROLES, PublicDetail.FULL),
                List.of("staff"), List.of("guest"), List.of());

        assertThat(access(member(ALICE, "guest"), definition)).isEqualTo(CalendarAccess.NONE);
    }

    // --- managing

    @Test
    void anEmptyManageListMakesNobodyAManagerButPlatformStaff() {
        CalendarDefinition definition = calendar(CalendarVisibility.PUBLIC, PublicDetail.FULL);

        assertThat(access(member(ALICE, "office-admin"), definition).canManage()).isFalse();
        assertThat(access(anonymous(), definition).canManage()).isFalse();
        assertThat(access(staff("staff-1"), definition)).isEqualTo(new CalendarAccess(true, true, true, true));
    }

    @Test
    void aManageRoleOfAnotherTenantsTokenGrantsNothing() {
        CalendarDefinition definition = withRoles(calendar(CalendarVisibility.PUBLIC, PublicDetail.FULL),
                List.of(), List.of(), List.of("office-admin"));

        assertThat(access(foreigner(ALICE, "office-admin"), definition).canManage()).isFalse();
    }

    // --- lookup

    @Test
    void aMissingOrUnreadableCalendarAnswersTheSameNotFound() {
        CalendarDefinition hidden = calendar(CalendarVisibility.AUTHENTICATED, PublicDetail.FULL);
        when(definitions.released(ORG, CALENDAR)).thenReturn(Optional.of(hidden));
        when(definitions.released(ORG, "missing")).thenReturn(Optional.empty());
        ResolveCalendarAccess anonymousAccess = new ResolveCalendarAccess(definitions, anonymous());

        assertThatThrownBy(() -> anonymousAccess.readable(ORG, CALENDAR)).isInstanceOf(CalendarNotFoundException.class);
        assertThatThrownBy(() -> anonymousAccess.readable(ORG, "missing")).isInstanceOf(CalendarNotFoundException.class);
        assertThat(anonymousAccess.find(ORG, CALENDAR)).isEmpty();
    }

    @Test
    void aReadableCalendarComesWithTheCallersAccess() {
        when(definitions.released(ORG, CALENDAR)).thenReturn(Optional.of(calendar(CalendarVisibility.PUBLIC, PublicDetail.FULL)));

        ResolveCalendarAccess.ReadableCalendar readable = new ResolveCalendarAccess(definitions, anonymous()).readable(ORG, CALENDAR);

        assertThat(readable.definition().getDefinitionKey()).isEqualTo(CALENDAR);
        assertThat(readable.access().canRead()).isTrue();
    }
}
