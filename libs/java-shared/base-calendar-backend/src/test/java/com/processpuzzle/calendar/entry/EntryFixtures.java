package com.processpuzzle.calendar.entry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.domain.CalendarKind;
import com.processpuzzle.calendar.definition.domain.CalendarVisibility;
import com.processpuzzle.calendar.definition.domain.EntryKind;
import com.processpuzzle.calendar.definition.domain.PublicDetail;
import com.processpuzzle.calendar.entry.domain.CalendarEntry;
import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.domain.EntryVisibility;
import com.processpuzzle.calendar.entry.usecases.inbound.OrganizationTimeZone;
import com.processpuzzle.calendar.entry.usecases.outbound.OrganizationTimeZonePort;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.core.security.CurrentPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;

/** Builders for the tests of the entry part: calendars of every visibility, callers of every standing, entries. */
public final class EntryFixtures {

    public static final String ORG = "org-1";
    public static final String CALENDAR = "team-calendar";
    public static final String ALICE = "sub-alice";
    public static final String BOB = "sub-bob";

    private EntryFixtures() {
    }

    /** A released TEAM calendar with the given visibility and detail; every role list empty, any kind but recurring. */
    public static CalendarDefinition calendar(CalendarVisibility visibility, PublicDetail detail) {
        return CalendarDefinition.builder()
                .orgKey(ORG)
                .definitionKey(CALENDAR)
                .channel(Channel.RELEASED)
                .name("Team calendar")
                .kind(CalendarKind.TEAM)
                .visibility(visibility)
                .publicDetail(detail)
                .allowedKinds(new ArrayList<>(List.of(EntryKind.TIMED, EntryKind.ALL_DAY)))
                .build();
    }

    public static CalendarDefinition withRoles(CalendarDefinition definition, List<String> read, List<String> book, List<String> manage) {
        definition.setReadRoles(new ArrayList<>(read));
        definition.setBookRoles(new ArrayList<>(book));
        definition.setManageRoles(new ArrayList<>(manage));
        return definition;
    }

    /** An {@link ObjectProvider} that answers {@code value} however it is asked. */
    @SuppressWarnings("unchecked")
    public static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(value);
        when(provider.getIfUnique(any())).thenReturn(value);
        return provider;
    }

    /** The organization time zone use case with no host adapter and the given configured default. */
    @SuppressWarnings("unchecked")
    public static OrganizationTimeZone zones(String defaultZone) {
        ObjectProvider<OrganizationTimeZonePort> none = mock(ObjectProvider.class);
        when(none.getIfUnique(any())).thenAnswer(call -> ((java.util.function.Supplier<?>) call.getArgument(0)).get());
        return new OrganizationTimeZone(none, defaultZone);
    }

    /** A repository mock whose {@code save} returns what it is given, with an id assigned to a new entry. */
    public static CalendarEntryRepository repository() {
        CalendarEntryRepository repository = mock(CalendarEntryRepository.class);
        when(repository.save(any(CalendarEntry.class))).thenAnswer(call -> {
            CalendarEntry entry = call.getArgument(0);
            if (entry.getId() == null) {
                entry.setId(UUID.randomUUID());
            }
            return entry;
        });
        return repository;
    }

    /** No token at all. */
    public static CurrentPrincipal anonymous() {
        CurrentPrincipal principal = mock(CurrentPrincipal.class);
        when(principal.isAuthenticated()).thenReturn(false);
        when(principal.subject()).thenReturn(Optional.empty());
        when(principal.authorities()).thenReturn(Set.of());
        return principal;
    }

    /** A signed-in member of {@link #ORG} holding the given realm roles. */
    public static CurrentPrincipal member(String subject, String... roles) {
        return signedIn(subject, ORG, false, roles);
    }

    /** A signed-in user whose token comes from another organization's realm. */
    public static CurrentPrincipal foreigner(String subject, String... roles) {
        return signedIn(subject, "org-2", false, roles);
    }

    /** ProcessPuzzle staff, who belong to no organization and may do anything. */
    public static CurrentPrincipal staff(String subject) {
        return signedIn(subject, "platform", true);
    }

    private static CurrentPrincipal signedIn(String subject, String realm, boolean staff, String... roles) {
        CurrentPrincipal principal = mock(CurrentPrincipal.class);
        when(principal.isAuthenticated()).thenReturn(true);
        when(principal.isPlatformAdmin()).thenReturn(staff);
        when(principal.isMemberOf(ORG)).thenReturn(ORG.equals(realm));
        when(principal.subject()).thenReturn(Optional.of(subject));
        when(principal.displayName()).thenReturn(Optional.of("Name of " + subject));
        when(principal.authorities()).thenReturn(Set.of(roles));
        return principal;
    }

    public static CalendarEntry timedEntry(String organizer, EntryVisibility visibility) {
        return CalendarEntry.builder()
                .id(UUID.randomUUID())
                .orgKey(ORG)
                .calendarKey(CALENDAR)
                .kind(EntryKind.TIMED)
                .title("Planning")
                .description("Quarterly planning")
                .location("Room 1")
                .category("meeting")
                .visibility(visibility)
                .startUtc(Instant.parse("2026-12-01T09:00:00Z"))
                .endUtc(Instant.parse("2026-12-01T10:00:00Z"))
                .timeZone("UTC")
                .organizerSub(organizer)
                .organizerDisplayName("Name of " + organizer)
                .version(0L)
                .build();
    }

    public static CalendarEntry allDayEntry(String organizer, LocalDate start, LocalDate endExclusive) {
        return CalendarEntry.builder()
                .id(UUID.randomUUID())
                .orgKey(ORG)
                .calendarKey(CALENDAR)
                .kind(EntryKind.ALL_DAY)
                .title("Holiday")
                .visibility(EntryVisibility.PUBLIC)
                .startDate(start)
                .endDate(endExclusive)
                .organizerSub(organizer)
                .organizerDisplayName("Name of " + organizer)
                .version(0L)
                .build();
    }
}
