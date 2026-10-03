package com.processpuzzle.ai.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisionJobTicketRepository extends JpaRepository<VisionJobTicket, UUID> {

    /** Open tickets not touched since {@code horizon}: unsubmitted, or submitted with no notification. */
    List<VisionJobTicket> findByStatusInAndTouchedAtBefore(Collection<VisionJobTicket.Status> statuses, Instant horizon);
}
