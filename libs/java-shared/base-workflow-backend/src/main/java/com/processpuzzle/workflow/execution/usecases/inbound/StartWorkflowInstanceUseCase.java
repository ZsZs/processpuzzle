package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.definition.domain.ArtifactDefinition;
import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolveWorkflowUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ResolvedWorkflow;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import com.processpuzzle.workflow.execution.domain.EventInstanceRepository;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import com.processpuzzle.workflow.execution.events.WorkflowInstanceStartedEvent;
import com.processpuzzle.workflow.execution.events.ArtifactInstanceCreatedEvent;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts a new instance of a workflow definition: an explicit start checks that one of its start
 * events admits the start ({@link StartEventAdmission}), a triggered one does not; either then creates the {@link WorkflowInstance}, one
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

    private static final Set<WorkflowInstanceStatus> NON_TERMINAL =
            EnumSet.of(WorkflowInstanceStatus.ACTIVE, WorkflowInstanceStatus.SUSPENDED);

    private final ResolveWorkflowUseCase resolveWorkflow;
    private final StartEventAdmission startEventAdmission;
    private final InstanceNumberAllocator instanceNumbers;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final TaskInstanceRepository taskInstanceRepository;
    private final EventInstanceRepository eventInstanceRepository;
    private final ArtifactInstanceRepository artifactInstanceRepository;
    private final TaskActivationService taskActivationService;
    private final ApplicationEventPublisher eventPublisher;

    public StartWorkflowInstanceUseCase(ResolveWorkflowUseCase resolveWorkflow,
                                        StartEventAdmission startEventAdmission,
                                        InstanceNumberAllocator instanceNumbers,
                                        WorkflowInstanceRepository workflowInstanceRepository,
                                        TaskInstanceRepository taskInstanceRepository,
                                        EventInstanceRepository eventInstanceRepository,
                                        ArtifactInstanceRepository artifactInstanceRepository,
                                        TaskActivationService taskActivationService,
                                        ApplicationEventPublisher eventPublisher) {
        this.resolveWorkflow = resolveWorkflow;
        this.startEventAdmission = startEventAdmission;
        this.instanceNumbers = instanceNumbers;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.taskInstanceRepository = taskInstanceRepository;
        this.eventInstanceRepository = eventInstanceRepository;
        this.artifactInstanceRepository = artifactInstanceRepository;
        this.taskActivationService = taskActivationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * An explicit start, through {@code /instances}.
     *
     * @param startEventId the start event to admit the start by, or null to try each — see
     *                     {@link StartEventAdmission}
     */
    public WorkflowInstance start(String orgKey, String workflowId, String entityId, String startEventId,
                                  Map<String, Object> initialContext) {
        ResolvedWorkflow definition = resolveWorkflow.resolveByOrgKeyAndId(orgKey, workflowId);
        String admittedBy = startEventAdmission.admit(orgKey, definition, startEventId, entityId).orElse(null);
        return createInstance(orgKey, definition, entityId, entityId == null ? null : entityTypeOf(definition),
                admittedBy, initialContext);
    }

    /**
     * A start by a TRIGGERING_EVENT start event, whose event has just occurred. Bypasses
     * {@link StartEventAdmission}: the caller has matched the event to the start event already, and
     * there is no principal to authorize — nobody asked.
     *
     * <p>Idempotent per subject: when {@code entityId} already has a non-terminal instance of the
     * workflow nothing is started. The event publication registry redelivers an event whose listener
     * did not complete, so the same event can arrive twice.
     *
     * <p>{@code REQUIRES_NEW}: the caller is an after-commit listener starting several workflows, and
     * one that fails must neither roll back nor poison the transaction of the others.
     *
     * @return the new instance; empty when one was already running for {@code entityId}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<WorkflowInstance> startTriggered(String orgKey, String workflowId, String startEventId,
                                                     String entityId, String entityType,
                                                     Map<String, Object> initialContext) {
        if (entityId != null && workflowInstanceRepository.existsByOrgKeyAndWorkflowIdAndEntityIdAndStatusIn(
                orgKey, workflowId, entityId, NON_TERMINAL)) {
            return Optional.empty();
        }
        ResolvedWorkflow definition = resolveWorkflow.resolveByOrgKeyAndId(orgKey, workflowId);
        return Optional.of(createInstance(orgKey, definition, entityId, entityType, startEventId, initialContext));
    }

    /**
     * The entity type an explicitly started run's subject is of: the workflow's first ENTITY artifact. An
     * explicit start names an object, not its type, and the artifact is the workflow's own statement of
     * which entity it works on.
     */
    private static String entityTypeOf(ResolvedWorkflow definition) {
        return definition.artifacts().stream()
                .filter(artifact -> artifact.getArtifactType() == ArtifactType.ENTITY)
                .map(ArtifactDefinition::getArtifactTypeId)
                .filter(type -> type != null && !type.isBlank())
                .findFirst()
                .orElse(null);
    }

    private WorkflowInstance createInstance(String orgKey, ResolvedWorkflow definition, String entityId,
                                            String entityType, String admittedBy, Map<String, Object> initialContext) {
        WorkflowInstance instance = workflowInstanceRepository.save(WorkflowInstance.builder()
                .orgKey(orgKey)
                .instanceNumber(instanceNumbers.next(orgKey))
                .workflowId(definition.id())
                .workflowName(definition.definition().getName())
                .status(WorkflowInstanceStatus.ACTIVE)
                .entityId(entityId)
                .entityType(entityType)
                .startEventId(admittedBy)
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
        definition.events().forEach(event -> eventInstanceRepository.save(
                TaskActivationService.pendingEventInstance(orgKey, instance.getId(), event)));

        // Nothing has completed yet, so the initial context *is* the assembled one.
        taskActivationService.activateEligibleTasks(orgKey, definition, instance.getId(), instance.getInitialContext());
        eventPublisher.publishEvent(new WorkflowInstanceStartedEvent(orgKey, instance.getId(), definition.id(), entityId));

        return instance;
    }
}
