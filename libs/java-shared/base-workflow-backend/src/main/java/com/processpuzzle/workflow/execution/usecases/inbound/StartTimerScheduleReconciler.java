package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.StartEvent;
import com.processpuzzle.workflow.definition.domain.TimerDefinition;
import com.processpuzzle.workflow.definition.domain.TimerExpressions;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowStartConditionType;
import com.processpuzzle.workflow.execution.domain.StartTimerSchedule;
import com.processpuzzle.workflow.execution.domain.StartTimerScheduleRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps a workflow's {@link StartTimerSchedule} rows in step with its TIME_BASED_PRECONDITION start
 * events: a row per start event with a timer, armed when it is new or its timer changed, kept as it is
 * — due time included — when the timer did not change, and removed when its start event, or the whole
 * workflow, is gone.
 */
@Component
public class StartTimerScheduleReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(StartTimerScheduleReconciler.class);

    private final StartTimerScheduleRepository scheduleRepository;
    private final WorkflowRepository workflowRepository;
    private final Clock clock;

    public StartTimerScheduleReconciler(StartTimerScheduleRepository scheduleRepository,
                                        WorkflowRepository workflowRepository, Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.workflowRepository = workflowRepository;
        this.clock = clock;
    }

    @Transactional
    public void reconcile(String orgKey, String workflowId) {
        Map<String, StartTimerSchedule> stale = new LinkedHashMap<>();
        scheduleRepository.findByOrgKeyAndWorkflowId(orgKey, workflowId)
                .forEach(row -> stale.put(row.getStartEventId(), row));

        for (StartEvent startEvent : timedStartEvents(workflowRepository.findByOrgKeyAndId(orgKey, workflowId))) {
            String spec = specOf(startEvent.getTimer());
            StartTimerSchedule row = stale.remove(startEvent.getId());
            if (row != null && spec.equals(row.getSpecHash())) {
                continue;
            }
            if (row == null) {
                row = StartTimerSchedule.builder().orgKey(orgKey).workflowId(workflowId)
                        .startEventId(startEvent.getId()).build();
            }
            TimerExpressions.Arming arming = arm(orgKey, workflowId, startEvent).orElse(null);
            row.setDueAt(arming == null ? null : arming.dueAt());
            row.setRemainingFirings(arming == null ? Integer.valueOf(0) : arming.remainingFirings());
            row.setSpecHash(spec);
            scheduleRepository.save(row);
        }
        scheduleRepository.deleteAll(List.copyOf(stale.values()));
    }

    /** The timer a row was armed from, as the row records it. */
    static String specOf(TimerDefinition timer) {
        return timer.getType() + ":" + timer.getExpression();
    }

    private Optional<TimerExpressions.Arming> arm(String orgKey, String workflowId, StartEvent startEvent) {
        try {
            // The validator refuses a path on a start timer; there is no context to resolve one against.
            return TimerExpressions.arm(startEvent.getTimer(), path -> null, clock.instant());
        } catch (IllegalArgumentException e) {
            LOG.warn("{}: start event '{}' of workflow '{}' has a timer that cannot be armed: {}", orgKey,
                    startEvent.getId(), workflowId, e.getMessage());
            return Optional.empty();
        }
    }

    private static List<StartEvent> timedStartEvents(Optional<Workflow> workflow) {
        return workflow.map(Workflow::getStartEvents).orElse(List.of()).stream()
                .filter(startEvent -> startEvent.getStartType() == WorkflowStartConditionType.TIME_BASED_PRECONDITION)
                .filter(startEvent -> startEvent.getTimer() != null && startEvent.getTimer().getType() != null)
                .toList();
    }
}
