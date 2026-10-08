package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.common.WorkflowSchedulingConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link TimerSweep} every {@code base-workflow.timers.interval} (default {@code PT30S}), the first
 * time {@code base-workflow.timers.initial-delay} after startup. Absent when
 * {@code base-workflow.timers.enabled} is false; see {@link WorkflowSchedulingConfiguration}.
 */
@Component
@ConditionalOnProperty(name = WorkflowSchedulingConfiguration.TIMERS_ENABLED, havingValue = "true", matchIfMissing = true)
public class TimerSweepScheduler {

    private final TimerSweep timerSweep;

    public TimerSweepScheduler(TimerSweep timerSweep) {
        this.timerSweep = timerSweep;
    }

    @Scheduled(fixedDelayString = "${base-workflow.timers.interval:PT30S}",
            initialDelayString = "${base-workflow.timers.initial-delay:PT30S}")
    public void sweep() {
        timerSweep.sweep();
    }
}
