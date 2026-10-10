package com.processpuzzle.ai.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisionJobTicketRepository extends JpaRepository<VisionJobTicket, UUID> {

    /** Open tickets not touched since {@code horizon}: unsubmitted, or submitted with no notification. */
    List<VisionJobTicket> findByStatusInAndTouchedAtBefore(Collection<VisionJobTicket.Status> statuses, Instant horizon);

    /**
     * The ids alone of a tenant's tickets in {@code statuses}, for discarding its still-open jobs on the
     * vision server once the tickets are gone.
     */
    @Query("select t.jobId from VisionJobTicket t where t.orgKey = :orgKey and t.status in :statuses")
    List<UUID> findJobIdsByOrgKeyAndStatusIn(@Param("orgKey") String orgKey,
                                             @Param("statuses") Collection<VisionJobTicket.Status> statuses);

    /** Every ticket of a tenant in one statement, for erasing the tenant. */
    @Modifying(flushAutomatically = true)
    @Query("delete from VisionJobTicket t where t.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
