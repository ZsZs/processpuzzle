package com.processpuzzle.calendar.definition.domain;

import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder is scoped by {@code orgKey}, so an unscoped read of another tenant's row is not expressible by accident. */
public interface CalendarDefinitionRepository extends JpaRepository<CalendarDefinition, CalendarDefinitionKey> {

    Optional<CalendarDefinition> findByOrgKeyAndDefinitionKeyAndChannel(String orgKey, String definitionKey, Channel channel);

    List<CalendarDefinition> findByOrgKeyAndChannelOrderByDefinitionKeyAsc(String orgKey, Channel channel);

    /**
     * The released row of a calendar, locked until the transaction ends. Bookings of one resource calendar take turns
     * behind this lock, so each one counts the others that are already in place.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CalendarDefinition d where d.orgKey = :orgKey and d.definitionKey = :definitionKey and d.channel = com.processpuzzle.core.definition.Channel.RELEASED")
    Optional<CalendarDefinition> lockReleased(@Param("orgKey") String orgKey, @Param("definitionKey") String definitionKey);

    /** Every definition of every tenant on the channel; retention reads the period of each. */
    List<CalendarDefinition> findByChannel(Channel channel);

    /** Every definition of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from CalendarDefinition d where d.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
