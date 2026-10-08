package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.domain.StepResult;
import com.processpuzzle.workflow.execution.domain.EventInstance;
import com.processpuzzle.workflow.execution.domain.EventInstanceStatus;
import com.processpuzzle.workflow.definition.domain.EventDirection;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.usecases.inbound.CompleteTaskUseCase;
import com.processpuzzle.workflow.model.CompleteTaskResponse;
import com.processpuzzle.workflow.model.PageOfWorkflowInstance;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.processpuzzle.workflow.execution.usecases.outbound.EntityLabelPort;
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

    /**
     * The subject is shown by name: resolved through the port per response, falling back to the id when
     * the port names nothing or the instance has no entity type to ask by.
     */
    @Test
    void toModel_namesTheSubjectThroughThePortAndFallsBackToItsId() {
        EntityLabelPort labels = (orgKey, type, id) -> "order".equals(type) && "o-1".equals(id)
                ? Optional.of("ORD-1001") : Optional.empty();
        WorkflowExecutionMapper labelling = new WorkflowExecutionMapper(labels);
        WorkflowInstance.WorkflowInstanceBuilder base = WorkflowInstance.builder().id(UUID.randomUUID()).orgKey("org-1")
                .workflowId("w").workflowName("W").status(WorkflowInstanceStatus.ACTIVE).startedAt(Instant.now());

        var named = labelling.toModel(base.entityId("o-1").entityType("order").build(), List.of(), List.of());
        var unknown = labelling.toModel(base.entityId("o-2").entityType("order").build(), List.of(), List.of());
        var untyped = labelling.toModel(base.entityId("o-1").entityType(null).build(), List.of(), List.of());
        var subjectless = labelling.toModel(base.entityId(null).build(), List.of(), List.of());

        assertThat(named.getEntityLabel()).isEqualTo("ORD-1001");
        assertThat(named.getEntityType()).isEqualTo("order");
        assertThat(unknown.getEntityLabel()).isEqualTo("o-2");
        assertThat(untyped.getEntityLabel()).isEqualTo("o-1");
        assertThat(subjectless.getEntityLabel()).isNull();
    }

    /**
     * The page wraps rows the caller has already assembled — the endpoint maps each instance together
     * with its task and artifact instances, which this mapper has no repository to reach. Only the
     * paging metadata is this method's business.
     */
    @Test
    void toPageModel_wrapsAlreadyAssembledRows() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        WorkflowInstance instance = WorkflowInstance.builder()
                .id(id)
                .orgKey("org-1")
                .instanceNumber(3L)
                .workflowId("proc-def-1")
                .workflowName("Workflow Name")
                .status(WorkflowInstanceStatus.ACTIVE)
                .entityId("entity-1")
                .startedAt(now)
                .build();

        com.processpuzzle.workflow.model.WorkflowInstance row = mapper.toModel(instance, List.of(), List.of());
        assertThat(row.getId()).isEqualTo(id.toString());
        assertThat(row.getInstanceNumber()).isEqualTo(3L);
        assertThat(row.getWorkflowId()).isEqualTo("proc-def-1");
        assertThat(row.getStatus().getValue()).isEqualTo("ACTIVE");

        PageOfWorkflowInstance pageModel =
                mapper.toPageModel(new PageImpl<>(List.of(instance), PageRequest.of(0, 10), 1), List.of(row));
        assertThat(pageModel.getContent()).containsExactly(row);
        assertThat(pageModel.getTotalElements()).isEqualTo(1);
        assertThat(pageModel.getSize()).isEqualTo(10);
    }

    @Test
    void toModel_fullWorkflowInstance() {
        UUID procId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID wpId = UUID.randomUUID();
        Instant now = Instant.now();

        WorkflowInstance proc = WorkflowInstance.builder()
                .id(procId)
                .orgKey("org-1")
                .workflowId("proc-def-1")
                .workflowName("Workflow Name")
                .status(WorkflowInstanceStatus.ACTIVE)
                .initialContext(Map.of("key", "value"))
                .startedAt(now)
                .build();

        TaskInstance task = TaskInstance.builder()
                .id(taskId)
                .orgKey("org-1")
                .workflowInstanceId(procId)
                .taskDefinitionId("task-def-1")
                .name("Task Name")
                .status(TaskInstanceStatus.ACTIVE)
                .assignedTo("user-1")
                .activatedAt(now)
                .stepResults(List.of(StepResult.builder().stepId("step-1").completedAt(now).toolResponse(Map.of("res", "ok")).build()))
                .build();

        ArtifactInstance wp = ArtifactInstance.builder()
                .id(wpId)
                .orgKey("org-1")
                .workflowInstanceId(procId)
                .artifactDefinitionId("wp-def-1")
                .name("Artifact Name")
                .type(ArtifactType.DOCUMENT)
                .entityId("ent-1")
                .stateMachineInstanceId("sm-1")
                .currentState("DRAFT")
                .updatedAt(now)
                .build();

        var model = mapper.toModel(proc, List.of(task), List.of(wp));
        assertThat(model.getId()).isEqualTo(procId.toString());
        assertThat(model.getTasks()).hasSize(1);
        assertThat(model.getTasks().get(0).getId()).isEqualTo(taskId.toString());
        assertThat(model.getArtifacts()).hasSize(1);
        assertThat(model.getArtifacts().get(0).getId()).isEqualTo(wpId.toString());
    }

    @Test
    void toModel_completeTaskResponse() {
        UUID taskId = UUID.randomUUID();
        TaskInstance task = TaskInstance.builder()
                .id(taskId)
                .orgKey("org-1")
                .workflowInstanceId(UUID.randomUUID())
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
    @Test
    void toModel_carriesCancellationAndTimerFields() {
        Instant now = Instant.now();
        TaskInstance cancelled = TaskInstance.builder().id(UUID.randomUUID()).taskDefinitionId("issue").name("Issue")
                .status(TaskInstanceStatus.CANCELLED).cancelledAt(now).cancelReason("interrupted by overdue").build();
        EventInstance timer = EventInstance.builder().id(UUID.randomUUID()).eventUseId("overdue")
                .direction(EventDirection.CATCH).status(EventInstanceStatus.WAITING).dueAt(now).fireCount(2).build();
        EventInstance legacy = EventInstance.builder().id(UUID.randomUUID()).eventUseId("old")
                .direction(EventDirection.CATCH).status(EventInstanceStatus.WAITING).build();

        var task = mapper.toModel(cancelled);
        assertThat(task.getStatus()).isEqualTo(com.processpuzzle.workflow.model.TaskInstanceStatus.CANCELLED);
        assertThat(task.getCancelledAt()).isNotNull();
        assertThat(task.getCancelReason()).isEqualTo("interrupted by overdue");
        assertThat(mapper.toModel(timer).getDueAt()).isNotNull();
        assertThat(mapper.toModel(timer).getFireCount()).isEqualTo(2);
        assertThat(mapper.toModel(timer).getEventDefinitionId()).isNull();
        assertThat(mapper.toModel(legacy).getFireCount()).isZero();
    }
}
