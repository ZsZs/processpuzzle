package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.definition.domain.ArtifactType;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.usecases.inbound.AssignTaskUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.CancelWorkflowInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.CompleteTaskUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindAllWorkflowInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindWorkflowInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindTaskInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindArtifactInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListTaskInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListArtifactInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.SkipTaskUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.StartWorkflowInstanceUseCase;
import com.processpuzzle.workflow.model.AssignTaskRequest;
import com.processpuzzle.workflow.model.CancelWorkflowInstanceRequest;
import com.processpuzzle.workflow.model.CompleteTaskRequest;
import com.processpuzzle.workflow.model.CompleteTaskResponse;
import com.processpuzzle.workflow.model.PageOfWorkflowInstance;
import com.processpuzzle.workflow.model.StartWorkflowRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowExecutionEndpointsTest {

    private static final String ORG = "org-1";

    private WorkflowExecutionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new WorkflowExecutionMapper();
    }

    @Test
    void workflowInstancesEndpoint_allMethods() {
        StartWorkflowInstanceUseCase startUseCase = mock(StartWorkflowInstanceUseCase.class);
        FindWorkflowInstanceUseCase findUseCase = mock(FindWorkflowInstanceUseCase.class);
        FindAllWorkflowInstancesUseCase findAllUseCase = mock(FindAllWorkflowInstancesUseCase.class);
        CancelWorkflowInstanceUseCase cancelUseCase = mock(CancelWorkflowInstanceUseCase.class);
        ListTaskInstancesUseCase listTasksUseCase = mock(ListTaskInstancesUseCase.class);
        ListArtifactInstancesUseCase listWpsUseCase = mock(ListArtifactInstancesUseCase.class);

        WorkflowInstancesEndpoint endpoint = new WorkflowInstancesEndpoint(
                startUseCase, findUseCase, findAllUseCase, cancelUseCase, listTasksUseCase, listWpsUseCase, mapper);

        UUID instanceId = UUID.randomUUID();
        WorkflowInstance pi = WorkflowInstance.builder().id(instanceId).orgKey(ORG).workflowId("p1")
                .status(WorkflowInstanceStatus.ACTIVE).startedAt(Instant.now()).build();

        when(startUseCase.start(eq(ORG), eq("p1"), eq("e1"), any())).thenReturn(pi);
        when(findUseCase.findByOrgKeyAndId(ORG, instanceId)).thenReturn(pi);
        when(listTasksUseCase.findAll(ORG, instanceId)).thenReturn(List.of());
        when(listWpsUseCase.findAll(ORG, instanceId)).thenReturn(List.of());
        when(findAllUseCase.findAll(any())).thenReturn(new PageImpl<>(List.of(pi)));

        // Start
        StartWorkflowRequest startReq = new StartWorkflowRequest("p1");
        startReq.setEntityId("e1");
        startReq.setContext(Map.of("a", "b"));
        ResponseEntity<com.processpuzzle.workflow.model.WorkflowInstance> startRes = endpoint.startWorkflowInstance(ORG, startReq);
        assertThat(startRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(startRes.getBody().getId()).isEqualTo(instanceId.toString());

        // Get
        ResponseEntity<com.processpuzzle.workflow.model.WorkflowInstance> getRes = endpoint.getWorkflowInstance(ORG, instanceId.toString());
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getRes.getBody().getId()).isEqualTo(instanceId.toString());

        // List
        ResponseEntity<PageOfWorkflowInstance> listRes = endpoint.listWorkflowInstances(
                ORG, "p1", com.processpuzzle.workflow.model.WorkflowInstanceStatus.ACTIVE, "e1", null, null, null, null);
        assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listRes.getBody().getContent()).hasSize(1);

        // Cancel
        CancelWorkflowInstanceRequest cancelReq = new CancelWorkflowInstanceRequest();
        cancelReq.setReason("Cancel it");
        ResponseEntity<Void> cancelRes = endpoint.cancelWorkflowInstance(ORG, instanceId.toString(), cancelReq);
        assertThat(cancelRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(cancelUseCase).cancel(ORG, instanceId, "Cancel it");
    }

    @Test
    void taskInstancesEndpoint_allMethods() {
        ListTaskInstancesUseCase listTasksUseCase = mock(ListTaskInstancesUseCase.class);
        FindTaskInstanceUseCase findTaskUseCase = mock(FindTaskInstanceUseCase.class);
        AssignTaskUseCase assignUseCase = mock(AssignTaskUseCase.class);
        CompleteTaskUseCase completeUseCase = mock(CompleteTaskUseCase.class);
        SkipTaskUseCase skipUseCase = mock(SkipTaskUseCase.class);

        TaskInstancesEndpoint endpoint = new TaskInstancesEndpoint(
                listTasksUseCase, findTaskUseCase, assignUseCase, completeUseCase, skipUseCase, mapper);

        UUID instanceId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        TaskInstance ti = TaskInstance.builder().id(taskId).orgKey(ORG).workflowInstanceId(instanceId)
                .taskDefinitionId("task-1").status(TaskInstanceStatus.ACTIVE).name("Task 1").build();

        when(listTasksUseCase.findAll(ORG, instanceId)).thenReturn(List.of(ti));
        when(findTaskUseCase.find(ORG, instanceId, "task-1")).thenReturn(ti);
        when(assignUseCase.assign(ORG, instanceId, "task-1", "user-1")).thenReturn(ti);
        when(completeUseCase.complete(eq(ORG), eq(instanceId), eq("task-1"), any()))
                .thenReturn(new CompleteTaskUseCase.Result(true, ti, null));
        when(skipUseCase.skip(ORG, instanceId, "task-1", "Skip reason")).thenReturn(ti);

        // List
        ResponseEntity<List<com.processpuzzle.workflow.model.TaskInstance>> listRes = endpoint.listTaskInstances(
                ORG, instanceId.toString(), com.processpuzzle.workflow.model.TaskInstanceStatus.ACTIVE, null, null);
        assertThat(listRes.getBody()).hasSize(1);

        // Get
        ResponseEntity<com.processpuzzle.workflow.model.TaskInstance> getRes = endpoint.getTaskInstance(ORG, instanceId.toString(), "task-1");
        assertThat(getRes.getBody().getId()).isEqualTo(taskId.toString());

        // Assign
        AssignTaskRequest assignReq = new AssignTaskRequest("user-1");
        ResponseEntity<com.processpuzzle.workflow.model.TaskInstance> assignRes = endpoint.assignTask(ORG, instanceId.toString(), "task-1", assignReq);
        assertThat(assignRes.getBody().getId()).isEqualTo(taskId.toString());

        // Complete
        CompleteTaskRequest completeReq = new CompleteTaskRequest();
        completeReq.setContext(Map.of("res", "done"));
        ResponseEntity<CompleteTaskResponse> completeRes = endpoint.completeTask(ORG, instanceId.toString(), "task-1", completeReq);
        assertThat(completeRes.getBody().getAccepted()).isTrue();

        // Skip
        CancelWorkflowInstanceRequest skipReq = new CancelWorkflowInstanceRequest();
        skipReq.setReason("Skip reason");
        ResponseEntity<com.processpuzzle.workflow.model.TaskInstance> skipRes = endpoint.skipTask(ORG, instanceId.toString(), "task-1", skipReq);
        assertThat(skipRes.getBody().getId()).isEqualTo(taskId.toString());
    }

    @Test
    void artifactInstancesEndpoint_allMethods() {
        ListArtifactInstancesUseCase listUseCase = mock(ListArtifactInstancesUseCase.class);
        FindArtifactInstanceUseCase findUseCase = mock(FindArtifactInstanceUseCase.class);

        ArtifactInstancesEndpoint endpoint = new ArtifactInstancesEndpoint(listUseCase, findUseCase, mapper);

        UUID instanceId = UUID.randomUUID();
        UUID wpId = UUID.randomUUID();
        ArtifactInstance wpi = ArtifactInstance.builder().id(wpId).orgKey(ORG).workflowInstanceId(instanceId)
                .artifactDefinitionId("wp-1").name("WP 1").type(ArtifactType.DOCUMENT).updatedAt(Instant.now()).build();

        when(listUseCase.findAll(ORG, instanceId)).thenReturn(List.of(wpi));
        when(findUseCase.find(ORG, instanceId, "wp-1")).thenReturn(wpi);

        ResponseEntity<List<com.processpuzzle.workflow.model.ArtifactInstance>> listRes = endpoint.listArtifactInstances(
                ORG, instanceId.toString(), null, null);
        assertThat(listRes.getBody()).hasSize(1);

        ResponseEntity<com.processpuzzle.workflow.model.ArtifactInstance> getRes = endpoint.getArtifactInstance(
                ORG, instanceId.toString(), "wp-1");
        assertThat(getRes.getBody().getId()).isEqualTo(wpId.toString());
    }
}
