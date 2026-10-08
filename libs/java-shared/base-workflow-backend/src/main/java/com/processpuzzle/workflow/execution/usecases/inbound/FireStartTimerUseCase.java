package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.StartEvent;
import com.processpuzzle.workflow.definition.domain.TimerExpressions;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.execution.domain.StartTimerSchedule;
import com.processpuzzle.workflow.execution.domain.StartTimerScheduleRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts a workflow whose TIME_BASED_PRECONDITION start timer came due, as {@code TimerSweep} finds the
 * {@link StartTimerSchedule}s.
 *
 * <p>The row is advanced — to the next firing of a cycle, or to nothing — and flushed <em>before</em> the
 * instance is started, in the same transaction. That is the deduplication: a second sweep holding the same
 * row fails on its version and rolls back without starting anything. Firings missed while nothing swept,
 * say during a restart, are skipped rather than started in a burst.
 *
 * <p>A row whose start event or timer is gone is removed instead. {@code REQUIRES_NEW}: the sweep fires
 * each row on its own.
 */
@Component
public class FireStartTimerUseCase {

    private final StartTimerScheduleRepository scheduleRepository;
    private final WorkflowRepository workflowRepository;
    private final StartWorkflowInstanceUseCase startWorkflowInstance;
    private final Clock clock;

    public FireStartTimerUseCase(StartTimerScheduleRepository scheduleRepository, WorkflowRepository workflowRepository,
                                 StartWorkflowInstanceUseCase startWorkflowInstance, Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.workflowRepository = workflowRepository;
        this.startWorkflowInstance = startWorkflowInstance;
        this.clock = clock;
    }

    /** @return the started instance; empty when the row was not due, or no longer schedules anything */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<WorkflowInstance> fire(UUID scheduleId) {
        StartTimerSchedule row = scheduleRepository.findById(scheduleId).orElse(null);
        Instant now = clock.instant();
        if (row == null || row.getDueAt() == null || row.getDueAt().isAfter(now)) {
            return Optional.empty();
        }
        StartEvent startEvent = findTimedStartEvent(row).orElse(null);
        if (startEvent == null) {
            scheduleRepository.delete(row);
            return Optional.empty();
        }

        Optional<TimerExpressions.Arming> next = nextFiring(startEvent, row.getDueAt(), row.getRemainingFirings());
        while (next.isPresent() && next.get().dueAt().isBefore(now)) {
            next = nextFiring(startEvent, next.get().dueAt(), next.get().remainingFirings());
        }
        // Not Optional.map: an unbounded cycle's null remaining count would come out as empty.
        row.setDueAt(next.isPresent() ? next.get().dueAt() : null);
        row.setRemainingFirings(next.isPresent() ? next.get().remainingFirings() : Integer.valueOf(0));
        scheduleRepository.saveAndFlush(row);

        return Optional.of(startWorkflowInstance.startScheduled(row.getOrgKey(), row.getWorkflowId(), startEvent.getId()));
    }

    private static Optional<TimerExpressions.Arming> nextFiring(StartEvent startEvent, Instant lastDue, Integer remaining) {
        return TimerExpressions.next(startEvent.getTimer(), path -> null, lastDue, remaining);
    }

    private Optional<StartEvent> findTimedStartEvent(StartTimerSchedule row) {
        return workflowRepository.findByOrgKeyAndId(row.getOrgKey(), row.getWorkflowId())
                .map(Workflow::getStartEvents)
                .orElse(List.of())
                .stream()
                .filter(startEvent -> row.getStartEventId().equals(startEvent.getId()))
                .filter(startEvent -> startEvent.getStartType() == WorkflowStartConditionType.TIME_BASED_PRECONDITION)
                .filter(startEvent -> startEvent.getTimer() != null)
                .findFirst();
    }
}
