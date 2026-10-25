package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Erases a finished instance together with everything that belongs to it: its events (and with them any armed
 * timer), tasks (comments and step results are columns of the task row) and artifacts. Nothing else references an
 * instance: other features hold no instance id, and the entity an instance acts on is not owned by it, so it stays.
 *
 * <p>Only a COMPLETED or CANCELLED instance can be erased — a running one is cancelled first, which is what disarms
 * its timers and withdraws its catches in an orderly way. The instance number is not reused.
 */
@Component
@Transactional
public class DeleteWorkflowInstanceUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DeleteWorkflowInstanceUseCase.class);
    private static final Set<WorkflowInstanceStatus> FINISHED =
            EnumSet.of(WorkflowInstanceStatus.COMPLETED, WorkflowInstanceStatus.CANCELLED);

    private final WorkflowInstanceRepository repository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final EventInstanceRepository eventInstanceRepository;
    private final ArtifactInstanceRepository artifactInstanceRepository;

    public DeleteWorkflowInstanceUseCase(WorkflowInstanceRepository repository,
                                         TaskInstanceRepository taskInstanceRepository,
                                         EventInstanceRepository eventInstanceRepository,
                                         ArtifactInstanceRepository artifactInstanceRepository) {
        this.repository = repository;
        this.taskInstanceRepository = taskInstanceRepository;
        this.eventInstanceRepository = eventInstanceRepository;
        this.artifactInstanceRepository = artifactInstanceRepository;
    }

    public void delete(String orgKey, UUID instanceId) {
        WorkflowInstance instance = repository.findByOrgKeyAndId(orgKey, instanceId)
                .orElseThrow(() -> new NotFoundException("No workflow instance with id '%s'".formatted(instanceId)));
        if (!FINISHED.contains(instance.getStatus())) {
            throw new ConflictException("Workflow instance '%s' is %s; cancel it before deleting it"
                    .formatted(instanceId, instance.getStatus()));
        }
        int events = eventInstanceRepository.deleteByWorkflowInstance(orgKey, instanceId);
        int tasks = taskInstanceRepository.deleteByWorkflowInstance(orgKey, instanceId);
        int artifacts = artifactInstanceRepository.deleteByWorkflowInstance(orgKey, instanceId);
        repository.delete(instance);
        LOG.info("{}: workflow instance {} deleted with {} task(s), {} artifact(s) and {} event(s).",
                orgKey, instanceId, tasks, artifacts, events);
    }
}
