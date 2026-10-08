package com.processpuzzle.workflow.execution.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StartTimerScheduleRepository extends JpaRepository<StartTimerSchedule, UUID> {

    List<StartTimerSchedule> findByOrgKeyAndWorkflowId(String orgKey, String workflowId);

    /** The start timers due by {@code now}, across organizations, earliest first — one page of {@code TimerSweep}. */
    List<StartTimerSchedule> findTop100ByDueAtLessThanEqualOrderByDueAtAsc(Instant now);
}
