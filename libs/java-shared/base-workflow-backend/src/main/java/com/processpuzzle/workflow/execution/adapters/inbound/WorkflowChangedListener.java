package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.definition.domain.event.WorkflowChangedEvent;
import com.processpuzzle.workflow.execution.usecases.inbound.StartTimerScheduleReconciler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reschedules a workflow's start timers once a change to it has committed. Idempotent, since the
 * reconciler converges the rows on the definition as it now reads, so a redelivered event is harmless.
 */
@Component
public class WorkflowChangedListener {

    private final StartTimerScheduleReconciler reconciler;

    public WorkflowChangedListener(StartTimerScheduleReconciler reconciler) {
        this.reconciler = reconciler;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(WorkflowChangedEvent event) {
        reconciler.reconcile(event.orgKey(), event.workflowId());
    }
}
