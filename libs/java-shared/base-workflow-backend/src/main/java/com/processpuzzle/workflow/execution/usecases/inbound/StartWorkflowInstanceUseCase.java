package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import com.processpuzzle.workflow.execution.events.WorkflowInstanceStartedEvent;
import com.processpuzzle.workflow.execution.events.ArtifactInstanceCreatedEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts a new instance of a workflow definition: creates the {@link WorkflowInstance}, one
 * {@link TaskInstance} per {@code TaskDefinition} (all initially PENDING), one
 * {@link ArtifactInstance} per {@code ArtifactDefinition}, then hands off to
 * {@link TaskActivationService} to activate whichever tasks are immediately eligible (those with
 * an empty {@code dependsOn}).
 *
 * <p>The definition arrives already resolved, so the tasks and artifacts copied into the instance
 * are the catalog entries as they read at start time. An instance is a snapshot in that sense: it
 * keeps the task ids and artifact names it was born with, and a later edit to a shared definition
 * does not rewrite it.
 */
@Component
@Transactional
public class StartWorkflowInstanceUseCase {

    private final ResolveWorkflowUseCase resolveWorkflow;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final ArtifactInstanceRepository artifactInstanceRepository;
    private final TaskActivationService taskActivationService;
    private final ApplicationEventPublisher eventPublisher;

    public StartWorkflowInstanceUseCase(ResolveWorkflowUseCase resolveWorkflow,
                                        WorkflowInstanceRepository workflowInstanceRepository,
                                        TaskInstanceRepository taskInstanceRepository,
                                        ArtifactInstanceRepository artifactInstanceRepository,
                                        TaskActivationService taskActivationService,
                                        ApplicationEventPublisher eventPublisher) {
        this.resolveWorkflow = resolveWorkflow;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.taskInstanceRepository = taskInstanceRepository;
        this.artifactInstanceRepository = artifactInstanceRepository;
        this.taskActivationService = taskActivationService;
        this.eventPublisher = eventPublisher;
    }

    public WorkflowInstance start(String orgKey, String workflowId, String entityId, Map<String, Object> initialContext) {
        ResolvedWorkflow definition = resolveWorkflow.resolveByOrgKeyAndId(orgKey, workflowId);

        WorkflowInstance instance = workflowInstanceRepository.save(WorkflowInstance.builder()
                .orgKey(orgKey)
                .workflowId(definition.id())
                .workflowName(definition.definition().getName())
                .status(WorkflowInstanceStatus.ACTIVE)
                .entityId(entityId)
                .initialContext(initialContext == null ? new HashMap<>() : new HashMap<>(initialContext))
                .startedAt(Instant.now())
                .build());

        for (ArtifactDefinition artifactDefinition : definition.artifacts()) {
            ArtifactInstance artifact = artifactInstanceRepository.save(ArtifactInstance.builder()
                    .orgKey(orgKey)
                    .workflowInstanceId(instance.getId())
                    .artifactDefinitionId(artifactDefinition.getId())
                    .name(artifactDefinition.getName())
                    .type(artifactDefinition.getArtifactType())
                    .updatedAt(Instant.now())
                    .build());
            eventPublisher.publishEvent(new ArtifactInstanceCreatedEvent(orgKey, instance.getId(), artifact.getId(),
                    artifactDefinition.getId(), artifactDefinition.getStateMachineId(), entityId));
        }

        definition.tasks().forEach(task -> taskInstanceRepository.save(TaskInstance.builder()
                .orgKey(orgKey)
                .workflowInstanceId(instance.getId())
                .taskDefinitionId(task.id())
                .name(task.definition().getName())
                .status(TaskInstanceStatus.PENDING)
                .build()));

        // Nothing has completed yet, so the initial context *is* the assembled one.
        taskActivationService.activateEligibleTasks(orgKey, definition, instance.getId(), instance.getInitialContext());
        eventPublisher.publishEvent(new WorkflowInstanceStartedEvent(orgKey, instance.getId(), definition.id(), entityId));

        return instance;
    }
}
