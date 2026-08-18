package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.definition.domain.WorkProductType;
import com.processpuzzle.workflow.execution.domain.ProcessInstance;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceStatus;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.domain.WorkProductInstance;
import com.processpuzzle.workflow.execution.usecases.inbound.AssignTaskUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.CancelProcessInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.CompleteTaskUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindAllProcessInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindProcessInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindTaskInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindWorkProductInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListTaskInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListWorkProductInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.SkipTaskUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.StartProcessInstanceUseCase;
import com.processpuzzle.workflow.model.AssignTaskRequest;
import com.processpuzzle.workflow.model.CancelProcessInstanceRequest;
import com.processpuzzle.workflow.model.CompleteTaskRequest;
import com.processpuzzle.workflow.model.CompleteTaskResponse;
import com.processpuzzle.workflow.model.PageOfProcessInstanceSummary;
import com.processpuzzle.workflow.model.StartProcessRequest;
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
    void processInstancesEndpoint_allMethods() {
        StartProcessInstanceUseCase startUseCase = mock(StartProcessInstanceUseCase.class);
        FindProcessInstanceUseCase findUseCase = mock(FindProcessInstanceUseCase.class);
        FindAllProcessInstancesUseCase findAllUseCase = mock(FindAllProcessInstancesUseCase.class);
        CancelProcessInstanceUseCase cancelUseCase = mock(CancelProcessInstanceUseCase.class);
        ListTaskInstancesUseCase listTasksUseCase = mock(ListTaskInstancesUseCase.class);
        ListWorkProductInstancesUseCase listWpsUseCase = mock(ListWorkProductInstancesUseCase.class);

        ProcessInstancesEndpoint endpoint = new ProcessInstancesEndpoint(
                startUseCase, findUseCase, findAllUseCase, cancelUseCase, listTasksUseCase, listWpsUseCase, mapper);

        UUID instanceId = UUID.randomUUID();
        ProcessInstance pi = ProcessInstance.builder().id(instanceId).orgKey(ORG).processDefinitionId("p1")
                .status(ProcessInstanceStatus.ACTIVE).startedAt(Instant.now()).build();

        when(startUseCase.start(eq(ORG), eq("p1"), eq("e1"), any())).thenReturn(pi);
        when(findUseCase.findByOrgKeyAndId(ORG, instanceId)).thenReturn(pi);
        when(listTasksUseCase.findAll(ORG, instanceId)).thenReturn(List.of());
        when(listWpsUseCase.findAll(ORG, instanceId)).thenReturn(List.of());
        when(findAllUseCase.findAll(any())).thenReturn(new PageImpl<>(List.of(pi)));

        // Start
        StartProcessRequest startReq = new StartProcessRequest("p1");
        startReq.setEntityId("e1");
        startReq.setContext(Map.of("a", "b"));
        ResponseEntity<com.processpuzzle.workflow.model.ProcessInstance> startRes = endpoint.startProcessInstance(ORG, startReq);
        assertThat(startRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(startRes.getBody().getId()).isEqualTo(instanceId.toString());

        // Get
        ResponseEntity<com.processpuzzle.workflow.model.ProcessInstance> getRes = endpoint.getProcessInstance(ORG, instanceId.toString());
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getRes.getBody().getId()).isEqualTo(instanceId.toString());

        // List
        ResponseEntity<PageOfProcessInstanceSummary> listRes = endpoint.listProcessInstances(
                ORG, "p1", com.processpuzzle.workflow.model.ProcessInstanceStatus.ACTIVE, "e1", null, null, null, null);
        assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listRes.getBody().getContent()).hasSize(1);

        // Cancel
        CancelProcessInstanceRequest cancelReq = new CancelProcessInstanceRequest();
        cancelReq.setReason("Cancel it");
        ResponseEntity<Void> cancelRes = endpoint.cancelProcessInstance(ORG, instanceId.toString(), cancelReq);
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
        TaskInstance ti = TaskInstance.builder().id(taskId).orgKey(ORG).processInstanceId(instanceId)
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
        CancelProcessInstanceRequest skipReq = new CancelProcessInstanceRequest();
        skipReq.setReason("Skip reason");
        ResponseEntity<com.processpuzzle.workflow.model.TaskInstance> skipRes = endpoint.skipTask(ORG, instanceId.toString(), "task-1", skipReq);
        assertThat(skipRes.getBody().getId()).isEqualTo(taskId.toString());
    }

    @Test
    void workProductInstancesEndpoint_allMethods() {
        ListWorkProductInstancesUseCase listUseCase = mock(ListWorkProductInstancesUseCase.class);
        FindWorkProductInstanceUseCase findUseCase = mock(FindWorkProductInstanceUseCase.class);

        WorkProductInstancesEndpoint endpoint = new WorkProductInstancesEndpoint(listUseCase, findUseCase, mapper);

        UUID instanceId = UUID.randomUUID();
        UUID wpId = UUID.randomUUID();
        WorkProductInstance wpi = WorkProductInstance.builder().id(wpId).orgKey(ORG).processInstanceId(instanceId)
                .workProductDefinitionId("wp-1").name("WP 1").type(WorkProductType.ARTIFACT).updatedAt(Instant.now()).build();

        when(listUseCase.findAll(ORG, instanceId)).thenReturn(List.of(wpi));
        when(findUseCase.find(ORG, instanceId, "wp-1")).thenReturn(wpi);

        ResponseEntity<List<com.processpuzzle.workflow.model.WorkProductInstance>> listRes = endpoint.listWorkProductInstances(
                ORG, instanceId.toString(), null, null);
        assertThat(listRes.getBody()).hasSize(1);

        ResponseEntity<com.processpuzzle.workflow.model.WorkProductInstance> getRes = endpoint.getWorkProductInstance(
                ORG, instanceId.toString(), "wp-1");
        assertThat(getRes.getBody().getId()).isEqualTo(wpId.toString());
    }
}
