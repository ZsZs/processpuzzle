package com.processpuzzle.calendar.entry;

import static com.processpuzzle.calendar.entry.EntryFixtures.CALENDAR;
import static com.processpuzzle.calendar.entry.EntryFixtures.ORG;
import static com.processpuzzle.calendar.entry.EntryFixtures.provider;
import static com.processpuzzle.calendar.entry.EntryFixtures.repository;
import static com.processpuzzle.calendar.entry.EntryFixtures.zones;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.processpuzzle.calendar.definition.domain.CalendarDefinition;
import com.processpuzzle.calendar.definition.usecases.inbound.FindCalendarDefinition;
import com.processpuzzle.calendar.entry.domain.CalendarEntryRepository;
import com.processpuzzle.calendar.entry.usecases.inbound.BookEntry;
import com.processpuzzle.calendar.entry.usecases.inbound.CancelEntry;
import com.processpuzzle.calendar.entry.usecases.inbound.EntryPlatformFacts;
import com.processpuzzle.calendar.entry.usecases.inbound.EntryViews;
import com.processpuzzle.calendar.entry.usecases.inbound.GetCalendar;
import com.processpuzzle.calendar.entry.usecases.inbound.GetEntry;
import com.processpuzzle.calendar.entry.usecases.inbound.ListOccurrences;
import com.processpuzzle.calendar.entry.usecases.inbound.OrganizationTimeZone;
import com.processpuzzle.calendar.entry.usecases.inbound.ResolveCalendarAccess;
import com.processpuzzle.calendar.entry.usecases.inbound.UpdateEntry;
import com.processpuzzle.core.security.CurrentPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * The entry use cases wired for one caller and one released calendar, over a mocked repository and the real access
 * rules: what the tests of the use cases share. The clock stands at 2026-11-19T12:00Z and the organization works in
 * Europe/Budapest.
 */
public final class EntryWiring {

    public static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");

    public final CalendarEntryRepository repository = repository();
    public final EntryPlatformFacts facts = mock(EntryPlatformFacts.class);
    public final OrganizationTimeZone zones = zones("Europe/Budapest");
    public final ResolveCalendarAccess access;
    public final EntryViews views;
    private final CurrentPrincipal principal;
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    public EntryWiring(CurrentPrincipal principal, CalendarDefinition calendar) {
        FindCalendarDefinition definitions = mock(FindCalendarDefinition.class);
        when(definitions.released(ORG, CALENDAR)).thenReturn(Optional.of(calendar));
        this.principal = principal;
        this.access = new ResolveCalendarAccess(definitions, principal);
        this.views = new EntryViews(principal);
    }

    public BookEntry book() {
        return new BookEntry(repository, access, zones, principal, views, facts, provider(clock));
    }

    public UpdateEntry update() {
        return new UpdateEntry(repository, access, zones, principal, views, facts, provider(clock));
    }

    public CancelEntry cancel() {
        return new CancelEntry(repository, access, principal, facts);
    }

    public GetEntry get() {
        return new GetEntry(repository, access, views);
    }

    public GetCalendar calendar() {
        return new GetCalendar(access, zones);
    }

    public ListOccurrences occurrences() {
        return new ListOccurrences(repository, access, zones, views);
    }
}
