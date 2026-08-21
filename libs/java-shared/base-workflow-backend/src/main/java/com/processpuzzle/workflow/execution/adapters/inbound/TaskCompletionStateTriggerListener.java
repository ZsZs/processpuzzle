package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.execution.domain.TaskCompletedEvent;
import com.processpuzzle.workflow.execution.domain.TransitionOutcome;
import com.processpuzzle.workflow.execution.usecases.outbound.EntityStateGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The "task completion fires a state trigger" half of the SPEM-inspired coupling between
 * base-workflow and base-state. @TransactionalEventListener keeps this transactional and
 * module-scoped per Spring Modulith convention — it runs after the completing transaction
 * commits, so a rejected or conflicting trigger never rolls back the task completion itself;
 * the task is done regardless of whether the order's state could follow it.
 */
@Component
class TaskCompletionStateTriggerListener {

    private static final Logger log = LoggerFactory.getLogger(TaskCompletionStateTriggerListener.class);

    private final EntityStateGateway entityStateGateway;

    TaskCompletionStateTriggerListener(EntityStateGateway entityStateGateway) {
        this.entityStateGateway = entityStateGateway;
    }

    @TransactionalEventListener
    void on(TaskCompletedEvent event) {
        if (event.completionStateTriggerKey() == null) {
            return;
        }

        TransitionOutcome outcome = entityStateGateway.fireTrigger(
                event.orgKey(),
                event.stateMachineEntityName(),
                event.entityInstanceId(),
                event.completionStateTriggerKey());

        switch (outcome) {
            case TransitionOutcome.Applied applied -> log.info(
                    "Task '{}' on process instance '{}' moved {}/{} to state '{}'.",
                    event.taskDefinitionId(), event.processInstanceId(),
                    event.stateMachineEntityName(), event.entityInstanceId(), applied.newStateKey());
            case TransitionOutcome.Rejected rejected -> log.warn(
                    "Task '{}' on process instance '{}' completed, but trigger '{}' was rejected for {}/{}: {}. "
                            + "The task stays completed; the order's state did not advance and needs attention.",
                    event.taskDefinitionId(), event.processInstanceId(), event.completionStateTriggerKey(),
                    event.stateMachineEntityName(), event.entityInstanceId(), rejected.reason());
            case TransitionOutcome.Conflict conflict -> log.warn(
                    "Task '{}' on process instance '{}' completed, but trigger '{}' hit an optimistic-lock "
                            + "conflict for {}/{}: {}. Retry the trigger once the concurrent update settles.",
                    event.taskDefinitionId(), event.processInstanceId(), event.completionStateTriggerKey(),
                    event.stateMachineEntityName(), event.entityInstanceId(), conflict.message());
        }
    }
}
