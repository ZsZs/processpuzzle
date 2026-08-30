package com.processpuzzle.workflow.execution.domain;

import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.execution.events.ProcessInstanceCancelledEvent;
import com.processpuzzle.workflow.execution.events.ProcessInstanceCompletedEvent;
import com.processpuzzle.workflow.execution.events.ProcessInstanceStartedEvent;
import com.processpuzzle.workflow.execution.events.TaskActivatedEvent;
import com.processpuzzle.workflow.execution.events.TaskCompletedEvent;
import com.processpuzzle.workflow.execution.events.TaskSkippedEvent;
import com.processpuzzle.workflow.execution.events.ArtifactInstanceCreatedEvent;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowExecutionDomainTest {

    @Test
    void processInstance_builderAndGettersSettersAndMethods() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("k1", "v1");

        ProcessInstance pi = ProcessInstance.builder()
                .id(id)
                .orgKey("org-1")
                .processDefinitionId("proc-def-1")
                .processDefinitionName("Proc Def 1")
                .status(ProcessInstanceStatus.ACTIVE)
                .entityId("entity-1")
                .initialContext(ctx)
                .startedAt(now)
                .completedAt(now.plusSeconds(60))
                .version(1L)
                .build();

        assertThat(pi.getId()).isEqualTo(id);
        assertThat(pi.getOrgKey()).isEqualTo("org-1");
        assertThat(pi.getProcessDefinitionId()).isEqualTo("proc-def-1");
        assertThat(pi.getProcessDefinitionName()).isEqualTo("Proc Def 1");
        assertThat(pi.getStatus()).isEqualTo(ProcessInstanceStatus.ACTIVE);
        assertThat(pi.getEntityId()).isEqualTo("entity-1");
        assertThat(pi.getInitialContext()).containsEntry("k1", "v1");
        assertThat(pi.getStartedAt()).isEqualTo(now);
        assertThat(pi.getCompletedAt()).isEqualTo(now.plusSeconds(60));
        assertThat(pi.getVersion()).isEqualTo(1L);

        pi.getInitialContext().put("k2", "v2");
        assertThat(pi.getInitialContext()).containsEntry("k2", "v2");

        ProcessInstance pi2 = ProcessInstance.builder().id(id).build();
        assertThat(pi)
                .isEqualTo(pi2)
                .hasSameHashCodeAs(pi2);
        assertThat(pi.toString()).contains("org-1");

        ProcessInstance empty = new ProcessInstance();
        empty.setInitialContext(new HashMap<>());
        empty.getInitialContext().put("k3", "v3");
        assertThat(empty.getInitialContext()).containsEntry("k3", "v3");
    }

    @Test
    void taskInstance_builderAndGettersSetters() {
        UUID id = UUID.randomUUID();
        UUID piId = UUID.randomUUID();
        Instant now = Instant.now();

        StepResult sr = StepResult.builder()
                .stepId("s1")
                .completedAt(now)
                .toolResponse(Map.of("r", "ok"))
                .error(null)
                .build();

        TaskInstance ti = TaskInstance.builder()
                .id(id)
                .orgKey("org-1")
                .processInstanceId(piId)
                .taskDefinitionId("task-1")
                .name("Task 1")
                .status(TaskInstanceStatus.ACTIVE)
                .assignedTo("user-1")
                .blockedReason("precondition failed")
                .activatedAt(now)
                .completedAt(now.plusSeconds(10))
                .skippedAt(now.plusSeconds(20))
                .stepResults(List.of(sr))
                .version(2L)
                .build();

        assertThat(ti.getId()).isEqualTo(id);
        assertThat(ti.getOrgKey()).isEqualTo("org-1");
        assertThat(ti.getProcessInstanceId()).isEqualTo(piId);
        assertThat(ti.getTaskDefinitionId()).isEqualTo("task-1");
        assertThat(ti.getName()).isEqualTo("Task 1");
        assertThat(ti.getStatus()).isEqualTo(TaskInstanceStatus.ACTIVE);
        assertThat(ti.getAssignedTo()).isEqualTo("user-1");
        assertThat(ti.getBlockedReason()).isEqualTo("precondition failed");
        assertThat(ti.getActivatedAt()).isEqualTo(now);
        assertThat(ti.getCompletedAt()).isEqualTo(now.plusSeconds(10));
        assertThat(ti.getSkippedAt()).isEqualTo(now.plusSeconds(20));
        assertThat(ti.getStepResults()).containsExactly(sr);
        assertThat(ti.getVersion()).isEqualTo(2L);

        TaskInstance ti2 = TaskInstance.builder().id(id).build();
        assertThat(ti)
                .isEqualTo(ti2)
                .hasSameHashCodeAs(ti2);
        assertThat(ti.toString()).contains("Task 1");

        TaskInstance empty = new TaskInstance();
        assertThat(empty.getStepResults()).isNotNull();
    }

    @Test
    void artifactInstance_builderAndGettersSetters() {
        UUID id = UUID.randomUUID();
        UUID piId = UUID.randomUUID();
        Instant now = Instant.now();

        ArtifactInstance wpi = ArtifactInstance.builder()
                .id(id)
                .orgKey("org-1")
                .processInstanceId(piId)
                .artifactDefinitionId("wp-1")
                .name("Artifact 1")
                .type(ArtifactType.DOCUMENT)
                .entityId("entity-1")
                .stateMachineInstanceId("sm-1")
                .currentState("draft")
                .updatedAt(now)
                .build();

        assertThat(wpi.getId()).isEqualTo(id);
        assertThat(wpi.getOrgKey()).isEqualTo("org-1");
        assertThat(wpi.getProcessInstanceId()).isEqualTo(piId);
        assertThat(wpi.getArtifactDefinitionId()).isEqualTo("wp-1");
        assertThat(wpi.getName()).isEqualTo("Artifact 1");
        assertThat(wpi.getType()).isEqualTo(ArtifactType.DOCUMENT);
        assertThat(wpi.getEntityId()).isEqualTo("entity-1");
        assertThat(wpi.getStateMachineInstanceId()).isEqualTo("sm-1");
        assertThat(wpi.getCurrentState()).isEqualTo("draft");
        assertThat(wpi.getUpdatedAt()).isEqualTo(now);

        ArtifactInstance wpi2 = ArtifactInstance.builder().id(id).build();
        assertThat(wpi)
                .isEqualTo(wpi2)
                .hasSameHashCodeAs(wpi2);
        assertThat(wpi.toString()).contains("Artifact 1");
    }

    @Test
    void stepResult_builderAndGettersSetters() {
        Instant now = Instant.now();
        StepResult sr = StepResult.builder()
                .stepId("step-1")
                .completedAt(now)
                .toolResponse(Map.of("key", "val"))
                .error("some error")
                .build();

        assertThat(sr.getStepId()).isEqualTo("step-1");
        assertThat(sr.getCompletedAt()).isEqualTo(now);
        assertThat(sr.getToolResponse()).containsEntry("key", "val");
        assertThat(sr.getError()).isEqualTo("some error");

        StepResult empty = new StepResult();
        empty.setStepId("step-2");
        empty.setCompletedAt(now);
        empty.setToolResponse(Map.of());
        empty.setError("err");
        assertThat(empty.getStepId()).isEqualTo("step-2");
    }

    @Test
    void events_instantiationAndGetters() {
        UUID piId = UUID.randomUUID();
        UUID tiId = UUID.randomUUID();
        UUID wpId = UUID.randomUUID();

        ProcessInstanceStartedEvent started = new ProcessInstanceStartedEvent("org-1", piId, "proc-1", "entity-1");
        assertThat(started.orgKey()).isEqualTo("org-1");
        assertThat(started.processInstanceId()).isEqualTo(piId);
        assertThat(started.processDefinitionId()).isEqualTo("proc-1");
        assertThat(started.entityId()).isEqualTo("entity-1");

        ProcessInstanceCompletedEvent completed = new ProcessInstanceCompletedEvent("org-1", piId, "proc-1");
        assertThat(completed.orgKey()).isEqualTo("org-1");
        assertThat(completed.processInstanceId()).isEqualTo(piId);

        ProcessInstanceCancelledEvent cancelled = new ProcessInstanceCancelledEvent("org-1", piId, "proc-1", "User cancelled");
        assertThat(cancelled.orgKey()).isEqualTo("org-1");
        assertThat(cancelled.reason()).isEqualTo("User cancelled");

        TaskActivatedEvent taskActivated = new TaskActivatedEvent("org-1", piId, tiId, "task-1");
        assertThat(taskActivated.orgKey()).isEqualTo("org-1");
        assertThat(taskActivated.taskDefinitionId()).isEqualTo("task-1");
        assertThat(taskActivated.taskInstanceId()).isEqualTo(tiId);

        TaskCompletedEvent taskCompleted = new TaskCompletedEvent("org-1", piId, tiId, "task-1");
        assertThat(taskCompleted.taskDefinitionId()).isEqualTo("task-1");
        assertThat(taskCompleted.taskInstanceId()).isEqualTo(tiId);

        TaskSkippedEvent taskSkipped = new TaskSkippedEvent("org-1", piId, tiId, "task-1", "Skipped by rule");
        assertThat(taskSkipped.reason()).isEqualTo("Skipped by rule");
        assertThat(taskSkipped.taskInstanceId()).isEqualTo(tiId);

        ArtifactInstanceCreatedEvent wpCreated = new ArtifactInstanceCreatedEvent(
                "org-1", piId, wpId, "wp-def-1", "sm-1", "entity-1");
        assertThat(wpCreated.orgKey()).isEqualTo("org-1");
        assertThat(wpCreated.artifactInstanceId()).isEqualTo(wpId);
        assertThat(wpCreated.stateMachineId()).isEqualTo("sm-1");
    }
    @Test
    void enums_allValuesCanBeInstantiated() {
        assertThat(ProcessInstanceStatus.values()).containsExactlyInAnyOrder(
                ProcessInstanceStatus.ACTIVE, ProcessInstanceStatus.COMPLETED,
                ProcessInstanceStatus.CANCELLED, ProcessInstanceStatus.SUSPENDED);
        assertThat(TaskInstanceStatus.values()).containsExactlyInAnyOrder(
                TaskInstanceStatus.PENDING, TaskInstanceStatus.ACTIVE,
                TaskInstanceStatus.COMPLETED, TaskInstanceStatus.SKIPPED, TaskInstanceStatus.BLOCKED);
    }
}
