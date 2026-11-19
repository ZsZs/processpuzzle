package com.processpuzzle.calendar.entry.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder is scoped by {@code orgKey}, so an unscoped read of another tenant's row is not expressible by accident. */
public interface CalendarEntryRepository extends JpaRepository<CalendarEntry, UUID> {

    Optional<CalendarEntry> findByIdAndOrgKeyAndCalendarKey(UUID id, String orgKey, String calendarKey);

    /**
     * The entries that overlap a window. A timed entry overlaps {@code [from, to)} as instants; an all-day entry
     * overlaps it when it covers a day of {@code [firstDay, lastDay]}, its end date being exclusive.
     */
    @Query("""
            select e from CalendarEntry e
            where e.orgKey = :orgKey and e.calendarKey in :calendarKeys
              and ((e.kind = com.processpuzzle.calendar.definition.domain.EntryKind.TIMED
                    and e.startUtc < :to and e.endUtc > :from)
                or (e.kind = com.processpuzzle.calendar.definition.domain.EntryKind.ALL_DAY
                    and e.startDate <= :lastDay and e.endDate > :firstDay))
            """)
    List<CalendarEntry> findOverlapping(@Param("orgKey") String orgKey,
                                        @Param("calendarKeys") Collection<String> calendarKeys,
                                        @Param("from") Instant from, @Param("to") Instant to,
                                        @Param("firstDay") LocalDate firstDay, @Param("lastDay") LocalDate lastDay);

    /** Every entry of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from CalendarEntry e where e.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
