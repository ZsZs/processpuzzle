package com.processpuzzle.workflow.execution.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StartTimerScheduleRepository extends JpaRepository<StartTimerSchedule, UUID> {

    List<StartTimerSchedule> findByOrgKeyAndWorkflowId(String orgKey, String workflowId);

    /** The start timers due by {@code now}, across organizations, earliest first — one page of {@code TimerSweep}. */
    List<StartTimerSchedule> findTop100ByDueAtLessThanEqualOrderByDueAtAsc(Instant now);

    /**
     * Every start-timer schedule of a tenant in one statement, for erasing the tenant. A bulk delete, so it
     * neither loads the rows nor checks their version.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from StartTimerSchedule s where s.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
