package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.execution.domain.ProcessInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.WorkProductInstance;
import com.processpuzzle.workflow.execution.usecases.inbound.CompleteTaskUseCase;
import com.processpuzzle.workflow.model.CompleteTaskResponse;
import com.processpuzzle.workflow.model.PageOfProcessInstanceSummary;
import com.processpuzzle.workflow.model.ProcessInstanceSummary;
import com.processpuzzle.workflow.model.StepResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Maps between execution-layer domain objects and the generated {@code workflow.model} classes. */
@Component
public class WorkflowExecutionMapper {

    // -- Process Instance --------------------------------------------------

    public com.processpuzzle.workflow.model.ProcessInstanceSummary toSummaryModel(ProcessInstance instance) {
        return new ProcessInstanceSummary()
                .id(instance.getId().toString())
                .processDefinitionId(instance.getProcessDefinitionId())
                .processDefinitionName(instance.getProcessDefinitionName())
                .status(com.processpuzzle.workflow.model.ProcessInstanceStatus.fromValue(instance.getStatus().name()))
                .entityId(instance.getEntityId())
                .startedAt(toOffsetDateTime(instance.getStartedAt()))
                .completedAt(toOffsetDateTime(instance.getCompletedAt()));
    }

    public com.processpuzzle.workflow.model.ProcessInstance toModel(ProcessInstance instance, List<TaskInstance> tasks,
                                                                      List<WorkProductInstance> workProducts) {
        com.processpuzzle.workflow.model.ProcessInstance model = new com.processpuzzle.workflow.model.ProcessInstance();
        model.setId(instance.getId().toString());
        model.setProcessDefinitionId(instance.getProcessDefinitionId());
        model.setProcessDefinitionName(instance.getProcessDefinitionName());
        model.setStatus(com.processpuzzle.workflow.model.ProcessInstanceStatus.fromValue(instance.getStatus().name()));
        model.setEntityId(instance.getEntityId());
        model.setStartedAt(toOffsetDateTime(instance.getStartedAt()));
        model.setCompletedAt(toOffsetDateTime(instance.getCompletedAt()));
        model.setContext(instance.getContext());
        model.setTasks(tasks.stream().map(this::toModel).toList());
        model.setWorkProducts(workProducts.stream().map(this::toModel).toList());
        return model;
    }

    public PageOfProcessInstanceSummary toModel(Page<ProcessInstance> page) {
        List<ProcessInstanceSummary> content = page.getContent().stream().map(this::toSummaryModel).toList();
        return new PageOfProcessInstanceSummary()
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
                .stepResults(instance.getStepResults().stream().map(sr -> new StepResult()
                        .stepId(sr.getStepId())
                        .completedAt(toOffsetDateTime(sr.getCompletedAt()))
                        .toolResponse(sr.getToolResponse())
                        .error(sr.getError())).toList());
    }

    public CompleteTaskResponse toModel(CompleteTaskUseCase.Result result) {
        return new CompleteTaskResponse()
                .accepted(result.accepted())
                .task(toModel(result.task()))
                .postconditionDetail(result.postconditionDetail());
    }

    // -- Work Product Instance ------------------------------------------------

    public com.processpuzzle.workflow.model.WorkProductInstance toModel(WorkProductInstance instance) {
        return new com.processpuzzle.workflow.model.WorkProductInstance()
                .id(instance.getId().toString())
                .workProductDefinitionId(instance.getWorkProductDefinitionId())
                .name(instance.getName())
                .type(com.processpuzzle.workflow.model.WorkProductType.fromValue(instance.getType().name()))
                .entityId(instance.getEntityId())
                .stateMachineInstanceId(instance.getStateMachineInstanceId())
                .currentState(instance.getCurrentState())
                .updatedAt(toOffsetDateTime(instance.getUpdatedAt()));
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
