package com.processpuzzle.calendar.entry.domain;

import static com.processpuzzle.calendar.entry.EntryFixtures.ALICE;
import static com.processpuzzle.calendar.entry.EntryFixtures.CALENDAR;
import static com.processpuzzle.calendar.entry.EntryFixtures.ORG;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

/** The mapping binds, and the window query keeps its boundaries: end and exclusive end dates never overlap. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-entry-persistence;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@EntityScan("com.processpuzzle.calendar.entry.domain")
@EnableJpaRepositories("com.processpuzzle.calendar.entry.domain")
class CalendarEntryPersistenceTest {

    @Configuration
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestConfig {
    }

    private static final Instant FROM = Instant.parse("2026-12-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-12-08T00:00:00Z");
    private static final LocalDate FIRST_DAY = LocalDate.parse("2026-12-01");
    private static final LocalDate LAST_DAY = LocalDate.parse("2026-12-07");

    @Autowired
    private CalendarEntryRepository repository;

    private CalendarEntry timed(String start, String end) {
        CalendarEntry entry = com.processpuzzle.calendar.entry.EntryFixtures.timedEntry(ALICE, EntryVisibility.PUBLIC);
        entry.setId(null);
        entry.setVersion(null);
        entry.setStartUtc(Instant.parse(start));
        entry.setEndUtc(Instant.parse(end));
        return repository.saveAndFlush(entry);
    }

    private CalendarEntry allDay(String start, String endExclusive) {
        CalendarEntry entry = com.processpuzzle.calendar.entry.EntryFixtures.allDayEntry(ALICE, LocalDate.parse(start), LocalDate.parse(endExclusive));
        entry.setId(null);
        entry.setVersion(null);
        return repository.saveAndFlush(entry);
    }

    private List<CalendarEntry> overlapping(String org, Set<String> calendars) {
        return repository.findOverlapping(org, calendars, FROM, TO, FIRST_DAY, LAST_DAY);
    }

    @Test
    void roundTripsEveryFieldAndGetsIdVersionAndTimestamps() {
        CalendarEntry saved = timed("2026-12-02T09:00:00Z", "2026-12-02T10:00:00Z");

        CalendarEntry reloaded = repository.findByIdAndOrgKeyAndCalendarKey(saved.getId(), ORG, CALENDAR).orElseThrow();

        assertThat(reloaded.getTitle()).isEqualTo("Planning");
        assertThat(reloaded.getDescription()).isEqualTo("Quarterly planning");
        assertThat(reloaded.getLocation()).isEqualTo("Room 1");
        assertThat(reloaded.getCategory()).isEqualTo("meeting");
        assertThat(reloaded.getVisibility()).isEqualTo(EntryVisibility.PUBLIC);
        assertThat(reloaded.getStartUtc()).isEqualTo(Instant.parse("2026-12-02T09:00:00Z"));
        assertThat(reloaded.getEndUtc()).isEqualTo(Instant.parse("2026-12-02T10:00:00Z"));
        assertThat(reloaded.getTimeZone()).isEqualTo("UTC");
        assertThat(reloaded.getOrganizerSub()).isEqualTo(ALICE);
        assertThat(reloaded.getOrganizerDisplayName()).isEqualTo("Name of sub-alice");
        assertThat(reloaded.getVersion()).isZero();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void anAllDayEntryRoundTripsItsDates() {
        CalendarEntry saved = allDay("2026-12-24", "2026-12-27");

        CalendarEntry reloaded = repository.findByIdAndOrgKeyAndCalendarKey(saved.getId(), ORG, CALENDAR).orElseThrow();

        assertThat(reloaded.getStartDate()).isEqualTo(LocalDate.parse("2026-12-24"));
        assertThat(reloaded.getEndDate()).isEqualTo(LocalDate.parse("2026-12-27"));
        assertThat(reloaded.getStartUtc()).isNull();
    }

    @Test
    void findsTimedEntriesThatOverlapTheWindowIncludingOnesThatStraddleItsEdges() {
        CalendarEntry inside = timed("2026-12-02T09:00:00Z", "2026-12-02T10:00:00Z");
        CalendarEntry straddlesStart = timed("2026-11-30T23:00:00Z", "2026-12-01T01:00:00Z");
        CalendarEntry straddlesEnd = timed("2026-12-07T23:00:00Z", "2026-12-08T01:00:00Z");
        CalendarEntry surrounds = timed("2026-11-01T00:00:00Z", "2027-01-01T00:00:00Z");

        assertThat(overlapping(ORG, Set.of(CALENDAR))).containsExactlyInAnyOrder(inside, straddlesStart, straddlesEnd, surrounds);
    }

    @Test
    void anEntryThatEndsWhereTheWindowStartsOrStartsWhereItEndsDoesNotOverlap() {
        timed("2026-11-30T23:00:00Z", "2026-12-01T00:00:00Z");
        timed("2026-12-08T00:00:00Z", "2026-12-08T01:00:00Z");
        timed("2026-01-01T00:00:00Z", "2026-01-01T01:00:00Z");

        assertThat(overlapping(ORG, Set.of(CALENDAR))).isEmpty();
    }

    @Test
    void anAllDayEntryOverlapsWhenItCoversADayOfTheWindowAndItsEndDateIsExclusive() {
        CalendarEntry inside = allDay("2026-12-03", "2026-12-04");
        CalendarEntry straddlesStart = allDay("2026-11-29", "2026-12-02");
        CalendarEntry straddlesEnd = allDay("2026-12-07", "2026-12-10");
        allDay("2026-11-28", "2026-12-01");
        allDay("2026-12-08", "2026-12-09");

        assertThat(overlapping(ORG, Set.of(CALENDAR))).containsExactlyInAnyOrder(inside, straddlesStart, straddlesEnd);
    }

    @Test
    void isScopedByOrganizationAndCalendar() {
        CalendarEntry mine = timed("2026-12-02T09:00:00Z", "2026-12-02T10:00:00Z");
        CalendarEntry otherCalendar = com.processpuzzle.calendar.entry.EntryFixtures.timedEntry(ALICE, EntryVisibility.PUBLIC);
        otherCalendar.setId(null);
        otherCalendar.setVersion(null);
        otherCalendar.setCalendarKey("meeting-room-a");
        repository.saveAndFlush(otherCalendar);
        CalendarEntry otherOrg = com.processpuzzle.calendar.entry.EntryFixtures.timedEntry(ALICE, EntryVisibility.PUBLIC);
        otherOrg.setId(null);
        otherOrg.setVersion(null);
        otherOrg.setOrgKey("org-2");
        repository.saveAndFlush(otherOrg);

        assertThat(overlapping(ORG, Set.of(CALENDAR))).containsExactly(mine);
        assertThat(overlapping(ORG, Set.of(CALENDAR, "meeting-room-a"))).hasSize(2);
        assertThat(overlapping("org-2", Set.of(CALENDAR))).containsExactly(otherOrg);
        assertThat(repository.findByIdAndOrgKeyAndCalendarKey(mine.getId(), "org-2", CALENDAR)).isEmpty();
        assertThat(repository.findByIdAndOrgKeyAndCalendarKey(mine.getId(), ORG, "meeting-room-a")).isEmpty();
    }

    @Test
    void erasesOneTenantsEntriesInOneStatement() {
        timed("2026-12-02T09:00:00Z", "2026-12-02T10:00:00Z");
        CalendarEntry elsewhere = com.processpuzzle.calendar.entry.EntryFixtures.timedEntry(ALICE, EntryVisibility.PUBLIC);
        elsewhere.setId(null);
        elsewhere.setVersion(null);
        elsewhere.setOrgKey("org-2");
        repository.saveAndFlush(elsewhere);

        assertThat(repository.eraseByOrgKey(ORG)).isEqualTo(1);

        assertThat(overlapping(ORG, Set.of(CALENDAR))).isEmpty();
        assertThat(overlapping("org-2", Set.of(CALENDAR))).hasSize(1);
    }
}
