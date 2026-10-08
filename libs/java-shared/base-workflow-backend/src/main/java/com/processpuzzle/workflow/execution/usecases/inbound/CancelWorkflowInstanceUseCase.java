package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.events.WorkflowInstanceCancelledEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@Transactional
public class CancelWorkflowInstanceUseCase {

    private final WorkflowInstanceRepository repository;
    private final EventInstanceRepository eventInstanceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CancelWorkflowInstanceUseCase(WorkflowInstanceRepository repository,
                                         EventInstanceRepository eventInstanceRepository,
                                         ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventInstanceRepository = eventInstanceRepository;
        this.eventPublisher = eventPublisher;
    }

    public void cancel(String orgKey, UUID instanceId, String reason) {
        WorkflowInstance instance = repository.findByOrgKeyAndId(orgKey, instanceId)
                .orElseThrow(() -> new NotFoundException("No workflow instance with id '%s'".formatted(instanceId)));

        if (instance.getStatus() == WorkflowInstanceStatus.COMPLETED || instance.getStatus() == WorkflowInstanceStatus.CANCELLED) {
            throw new ConflictException("Workflow instance '%s' is already %s".formatted(instanceId, instance.getStatus()));
        }
        instance.setStatus(WorkflowInstanceStatus.CANCELLED);
        instance.setCompletedAt(Instant.now());
        repository.save(instance);

        // A cancelled run waits for nothing any more: withdraw its catches, so no event is delivered to it.
        eventInstanceRepository.findByOrgKeyAndWorkflowInstanceId(orgKey, instanceId).stream()
                .filter(event -> !event.getStatus().isTerminal())
                .forEach(event -> {
                    event.setStatus(EventInstanceStatus.CANCELLED);
                    eventInstanceRepository.save(event);
                });

        eventPublisher.publishEvent(new WorkflowInstanceCancelledEvent(orgKey, instanceId, instance.getWorkflowId(), reason));
    }
}
