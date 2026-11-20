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

    /**
     * The entries the caller organizes, or is invited to by name or through one of {@code roles}, that overlap the
     * window. {@code roles} must not be empty: an empty IN list is not valid JPQL, so callers pass a placeholder.
     */
    @Query("""
            select distinct e from CalendarEntry e left join e.attendees a
            where e.orgKey = :orgKey
              and (e.organizerSub = :subject
                   or (a.type = com.processpuzzle.calendar.entry.domain.AttendeeType.USER and a.principal = :subject)
                   or (a.type = com.processpuzzle.calendar.entry.domain.AttendeeType.ROLE and a.principal in :roles))
              and ((e.kind = com.processpuzzle.calendar.definition.domain.EntryKind.TIMED
                    and e.startUtc < :to and e.endUtc > :from)
                or (e.kind = com.processpuzzle.calendar.definition.domain.EntryKind.ALL_DAY
                    and e.startDate <= :lastDay and e.endDate > :firstDay))
            """)
    List<CalendarEntry> findMine(@Param("orgKey") String orgKey, @Param("subject") String subject,
                                 @Param("roles") Collection<String> roles,
                                 @Param("from") Instant from, @Param("to") Instant to,
                                 @Param("firstDay") LocalDate firstDay, @Param("lastDay") LocalDate lastDay);

    /** The entries a user organized, for erasing them. */
    List<CalendarEntry> findByOrgKeyAndOrganizerSub(String orgKey, String organizerSub);

    /** The entries a user is invited to by name, for erasing the invitations. */
    @Query("""
            select distinct e from CalendarEntry e join e.attendees a
            where e.orgKey = :orgKey and a.type = com.processpuzzle.calendar.entry.domain.AttendeeType.USER and a.principal = :subject
            """)
    List<CalendarEntry> findInvitedBy(@Param("orgKey") String orgKey, @Param("subject") String subject);

    /** Every entry of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from CalendarEntry e where e.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
