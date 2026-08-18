package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.definition.domain.WorkProductType;
import com.processpuzzle.workflow.execution.domain.ProcessInstance;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceStatus;
import com.processpuzzle.workflow.execution.domain.StepResult;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkProductInstance;
import com.processpuzzle.workflow.execution.usecases.inbound.CompleteTaskUseCase;
import com.processpuzzle.workflow.model.CompleteTaskResponse;
import com.processpuzzle.workflow.model.PageOfProcessInstanceSummary;
import com.processpuzzle.workflow.model.ProcessInstanceSummary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowExecutionMapperTest {

    private WorkflowExecutionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new WorkflowExecutionMapper();
    }

    @Test
    void toSummaryModel_and_pageModel() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        ProcessInstance instance = ProcessInstance.builder()
                .id(id)
                .orgKey("org-1")
                .processDefinitionId("proc-def-1")
                .processDefinitionName("Process Name")
                .status(ProcessInstanceStatus.ACTIVE)
                .entityId("entity-1")
                .startedAt(now)
                .build();

        ProcessInstanceSummary summary = mapper.toSummaryModel(instance);
        assertThat(summary.getId()).isEqualTo(id.toString());
        assertThat(summary.getProcessDefinitionId()).isEqualTo("proc-def-1");
        assertThat(summary.getStatus().getValue()).isEqualTo("ACTIVE");

        PageOfProcessInstanceSummary pageModel = mapper.toModel(new PageImpl<>(List.of(instance), PageRequest.of(0, 10), 1));
        assertThat(pageModel.getContent()).hasSize(1);
        assertThat(pageModel.getTotalElements()).isEqualTo(1);
    }

    @Test
    void toModel_fullProcessInstance() {
        UUID procId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID wpId = UUID.randomUUID();
        Instant now = Instant.now();

        ProcessInstance proc = ProcessInstance.builder()
                .id(procId)
                .orgKey("org-1")
                .processDefinitionId("proc-def-1")
                .processDefinitionName("Process Name")
                .status(ProcessInstanceStatus.ACTIVE)
                .context(Map.of("key", "value"))
                .startedAt(now)
                .build();

        TaskInstance task = TaskInstance.builder()
                .id(taskId)
                .orgKey("org-1")
                .processInstanceId(procId)
                .taskDefinitionId("task-def-1")
                .name("Task Name")
                .status(TaskInstanceStatus.ACTIVE)
                .assignedTo("user-1")
                .activatedAt(now)
                .stepResults(List.of(StepResult.builder().stepId("step-1").completedAt(now).toolResponse(Map.of("res", "ok")).build()))
                .build();

        WorkProductInstance wp = WorkProductInstance.builder()
                .id(wpId)
                .orgKey("org-1")
                .processInstanceId(procId)
                .workProductDefinitionId("wp-def-1")
                .name("Work Product Name")
                .type(WorkProductType.ARTIFACT)
                .entityId("ent-1")
                .stateMachineInstanceId("sm-1")
                .currentState("DRAFT")
                .updatedAt(now)
                .build();

        var model = mapper.toModel(proc, List.of(task), List.of(wp));
        assertThat(model.getId()).isEqualTo(procId.toString());
        assertThat(model.getTasks()).hasSize(1);
        assertThat(model.getTasks().get(0).getId()).isEqualTo(taskId.toString());
        assertThat(model.getWorkProducts()).hasSize(1);
        assertThat(model.getWorkProducts().get(0).getId()).isEqualTo(wpId.toString());
    }

    @Test
    void toModel_completeTaskResponse() {
        UUID taskId = UUID.randomUUID();
        TaskInstance task = TaskInstance.builder()
                .id(taskId)
                .orgKey("org-1")
                .processInstanceId(UUID.randomUUID())
                .taskDefinitionId("task-def-1")
                .name("Task Name")
                .status(TaskInstanceStatus.COMPLETED)
                .build();

        CompleteTaskUseCase.Result result = new CompleteTaskUseCase.Result(true, task, "Condition met");
        CompleteTaskResponse response = mapper.toModel(result);

        assertThat(response.getAccepted()).isTrue();
        assertThat(response.getTask()).isNotNull();
        assertThat(response.getTask().getId()).isEqualTo(taskId.toString());
        assertThat(response.getPostconditionDetail()).isEqualTo("Condition met");
    }
}
