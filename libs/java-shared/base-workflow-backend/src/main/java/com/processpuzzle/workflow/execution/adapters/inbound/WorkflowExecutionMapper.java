package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.usecases.inbound.CompleteTaskUseCase;
import com.processpuzzle.workflow.model.CompleteTaskResponse;
import com.processpuzzle.workflow.model.PageOfWorkflowInstance;
import com.processpuzzle.workflow.model.StepResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import com.processpuzzle.workflow.execution.usecases.outbound.EntityLabelPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import com.processpuzzle.workflow.execution.domain.WorkflowContext;

/**
 * Maps between execution-layer domain objects and the generated {@code workflow.model} classes.
 *
 * <p>An instance's {@code entityLabel} is resolved here, per response, through {@link EntityLabelPort}
 * rather than stored: a name copied at start time would go stale the first time the object is renamed.
 */
@Component
public class WorkflowExecutionMapper {

    private final EntityLabelPort entityLabels;

    /** Names no entity — for callers that only map, as the endpoint tests do. */
    public WorkflowExecutionMapper() {
        this(EntityLabelPort.NONE);
    }

    @Autowired
    public WorkflowExecutionMapper(ObjectProvider<EntityLabelPort> entityLabelsProvider) {
        this(entityLabelsProvider.getIfUnique(() -> EntityLabelPort.NONE));
    }

    public WorkflowExecutionMapper(EntityLabelPort entityLabels) {
        this.entityLabels = entityLabels;
    }

    // -- Workflow Instance --------------------------------------------------

    public com.processpuzzle.workflow.model.WorkflowInstance toModel(WorkflowInstance instance, List<TaskInstance> tasks,
                                                                      List<ArtifactInstance> artifacts) {
        return toModel(instance, tasks, artifacts, List.of());
    }

    public com.processpuzzle.workflow.model.WorkflowInstance toModel(WorkflowInstance instance, List<TaskInstance> tasks,
                                                                      List<ArtifactInstance> artifacts,
                                                                      List<EventInstance> events) {
        com.processpuzzle.workflow.model.WorkflowInstance model = new com.processpuzzle.workflow.model.WorkflowInstance();
        model.setId(instance.getId().toString());
        model.setInstanceNumber(instance.getInstanceNumber());
        model.setWorkflowId(instance.getWorkflowId());
        model.setWorkflowName(instance.getWorkflowName());
        model.setStatus(com.processpuzzle.workflow.model.WorkflowInstanceStatus.fromValue(instance.getStatus().name()));
        model.setEntityId(instance.getEntityId());
        model.setEntityType(instance.getEntityType());
        model.setEntityLabel(entityLabelOf(instance));
        model.setStartEventId(instance.getStartEventId());
        model.setStartedAt(toOffsetDateTime(instance.getStartedAt()));
        model.setCompletedAt(toOffsetDateTime(instance.getCompletedAt()));
        // Assembled, not stored: the API's `context` is still the *current* context, it is simply
        // derived from the task and event contributions rather than from a field. Costs no query — the
        // task and event instances are already here because the response carries them.
        model.setContext(WorkflowContext.assemble(instance, tasks, events));
        model.setTasks(tasks.stream().map(this::toModel).toList());
        model.setArtifacts(artifacts.stream().map(this::toModel).toList());
        model.setEvents(events.stream().map(this::toModel).toList());
        return model;
    }

    /** The subject's name; its id when it has no type to resolve a name by, or the host names nothing. */
    private String entityLabelOf(WorkflowInstance instance) {
        if (instance.getEntityId() == null) {
            return null;
        }
        if (instance.getEntityType() == null) {
            return instance.getEntityId();
        }
        return entityLabels.labelOf(instance.getOrgKey(), instance.getEntityType(), instance.getEntityId())
                .orElse(instance.getEntityId());
    }

    /**
     * One page of <em>full</em> instances. The caller supplies the already-mapped rows rather than the
     * domain page, because assembling an instance needs its task and artifact instances, which
     * this mapper has no repository to reach — see {@code WorkflowInstancesEndpoint.listWorkflowInstances}.
     *
     * <p>Full rather than a summary projection for the reason spelled out on
     * {@code WorkflowDefinitionMapper.toModel(Page)}: base-entity's generated form reads the record out
     * of the loaded list, so a lighter shape here would render an empty form.
     */
    public PageOfWorkflowInstance toPageModel(Page<WorkflowInstance> page,
                                              List<com.processpuzzle.workflow.model.WorkflowInstance> content) {
        return new PageOfWorkflowInstance()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .number(page.getNumber())
                .size(page.getSize());
    }

    // -- Task Instance -------------------------------------------------------

    public com.processpuzzle.workflow.model.TaskInstance toModel(TaskInstance instance) {
        return new com.processpuzzle.workflow.model.TaskInstance()
                .id(instance.getId().toString())
                .taskDefinitionId(instance.getTaskDefinitionId())
                .name(instance.getName())
                .status(com.processpuzzle.workflow.model.TaskInstanceStatus.fromValue(instance.getStatus().name()))
                .assignedTo(instance.getAssignedTo())
                .blockedReason(instance.getBlockedReason())
                .activatedAt(toOffsetDateTime(instance.getActivatedAt()))
                .completedAt(toOffsetDateTime(instance.getCompletedAt()))
                .skippedAt(toOffsetDateTime(instance.getSkippedAt()))
                .cancelledAt(toOffsetDateTime(instance.getCancelledAt()))
                .cancelReason(instance.getCancelReason())
                .stepResults(instance.getStepResults().stream().map(sr -> new StepResult()
                        .stepId(sr.getStepId())
                        .completedAt(toOffsetDateTime(sr.getCompletedAt()))
                        .toolResponse(sr.getToolResponse())
                        .error(sr.getError())).toList())
                .contextContribution(instance.getContextContribution());
    }

    public CompleteTaskResponse toModel(CompleteTaskUseCase.Result result) {
        return new CompleteTaskResponse()
                .accepted(result.accepted())
                .task(toModel(result.task()))
                .postconditionDetail(result.postconditionDetail());
    }

    // -- Event Instance -------------------------------------------------------

    public com.processpuzzle.workflow.model.EventInstance toModel(EventInstance instance) {
        return new com.processpuzzle.workflow.model.EventInstance()
                .id(instance.getId() == null ? null : instance.getId().toString())
                .eventUseId(instance.getEventUseId())
                .eventDefinitionId(instance.getEventDefinitionId())
                .name(instance.getName())
                .direction(com.processpuzzle.workflow.model.EventDirection.fromValue(instance.getDirection().name()))
                .status(com.processpuzzle.workflow.model.EventInstanceStatus.fromValue(instance.getStatus().name()))
                .correlationValue(instance.getCorrelationValue())
                .waitingSince(toOffsetDateTime(instance.getWaitingSince()))
                .occurredAt(toOffsetDateTime(instance.getOccurredAt()))
                .dueAt(toOffsetDateTime(instance.getDueAt()))
                .fireCount(instance.firings())
                .payload(instance.getPayload())
                .contextContribution(instance.getContextContribution());
    }

    // -- Artifact Instance ------------------------------------------------

    public com.processpuzzle.workflow.model.ArtifactInstance toModel(ArtifactInstance instance) {
        return new com.processpuzzle.workflow.model.ArtifactInstance()
                .id(instance.getId().toString())
                .artifactDefinitionId(instance.getArtifactDefinitionId())
                .name(instance.getName())
                .type(com.processpuzzle.workflow.model.ArtifactType.fromValue(instance.getType().name()))
                .entityId(instance.getEntityId())
                .stateMachineInstanceId(instance.getStateMachineInstanceId())
                .currentState(instance.getCurrentState())
                .updatedAt(toOffsetDateTime(instance.getUpdatedAt()));
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
