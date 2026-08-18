package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.execution.domain.ProcessInstance;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceRepository;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceStatus;
import com.processpuzzle.workflow.execution.events.ProcessInstanceCancelledEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
@Transactional
public class CancelProcessInstanceUseCase {

    private final ProcessInstanceRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public CancelProcessInstanceUseCase(ProcessInstanceRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public void cancel(String orgKey, UUID instanceId, String reason) {
        ProcessInstance instance = repository.findByOrgKeyAndId(orgKey, instanceId)
                .orElseThrow(() -> new NotFoundException("No process instance with id '%s'".formatted(instanceId)));

        if (instance.getStatus() == ProcessInstanceStatus.COMPLETED || instance.getStatus() == ProcessInstanceStatus.CANCELLED) {
            throw new ConflictException("Process instance '%s' is already %s".formatted(instanceId, instance.getStatus()));
        }
        instance.setStatus(ProcessInstanceStatus.CANCELLED);
        instance.setCompletedAt(Instant.now());
        repository.save(instance);

        eventPublisher.publishEvent(new ProcessInstanceCancelledEvent(orgKey, instanceId, instance.getProcessDefinitionId(), reason));
    }
}
