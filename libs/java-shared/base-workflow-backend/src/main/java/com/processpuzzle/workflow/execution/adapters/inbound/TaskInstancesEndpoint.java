package com.processpuzzle.workflow.execution.adapters.inbound;

import com.processpuzzle.workflow.api.TaskInstancesApi;
import com.processpuzzle.workflow.execution.domain.TaskInstanceStatus;
import com.processpuzzle.workflow.execution.usecases.inbound.AssignTaskUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.CompleteTaskUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.FindTaskInstanceUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.ListTaskInstancesUseCase;
import com.processpuzzle.workflow.execution.usecases.inbound.SkipTaskUseCase;
import com.processpuzzle.workflow.model.AssignTaskRequest;
import com.processpuzzle.workflow.model.CancelWorkflowInstanceRequest;
import com.processpuzzle.workflow.model.CompleteTaskRequest;
import com.processpuzzle.workflow.model.CompleteTaskResponse;
import com.processpuzzle.workflow.model.TaskInstance;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements the generated {@code TaskInstancesApi} (from the "Task Instances" tag). See
 * {@code WorkflowInstancesEndpoint}'s Javadoc for the same {@code {reason: string}}
 * generator-uncertainty note — it applies to {@code skipTask} here too.
 */
@RestController
public class TaskInstancesEndpoint implements TaskInstancesApi {

    private final ListTaskInstancesUseCase listTaskInstances;
    private final FindTaskInstanceUseCase findTaskInstance;
    private final AssignTaskUseCase assignTask;
    private final CompleteTaskUseCase completeTask;
    private final SkipTaskUseCase skipTask;
    private final WorkflowExecutionMapper mapper;

    public TaskInstancesEndpoint(ListTaskInstancesUseCase listTaskInstances,
                                  FindTaskInstanceUseCase findTaskInstance,
                                  AssignTaskUseCase assignTask,
                                  CompleteTaskUseCase completeTask,
                                  SkipTaskUseCase skipTask,
                                  WorkflowExecutionMapper mapper) {
        this.listTaskInstances = listTaskInstances;
        this.findTaskInstance = findTaskInstance;
        this.assignTask = assignTask;
        this.completeTask = completeTask;
        this.skipTask = skipTask;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<TaskInstance>> listTaskInstances(
            String orgKey, String instanceId, com.processpuzzle.workflow.model.TaskInstanceStatus status,
            String where, String order) {
        UUID id = UUID.fromString(instanceId);
        var tasks = listTaskInstances.findAll(orgKey, id);
        if (status != null) {
            TaskInstanceStatus domainStatus = TaskInstanceStatus.valueOf(status.getValue());
            tasks = tasks.stream().filter(t -> t.getStatus() == domainStatus).toList();
        }
        return ResponseEntity.ok(tasks.stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<TaskInstance> getTaskInstance(String orgKey, String instanceId, String taskId) {
        var task = findTaskInstance.find(orgKey, UUID.fromString(instanceId), taskId);
        return ResponseEntity.ok(mapper.toModel(task));
    }

    @Override
    public ResponseEntity<TaskInstance> assignTask(String orgKey, String instanceId, String taskId, AssignTaskRequest request) {
        var task = assignTask.assign(orgKey, UUID.fromString(instanceId), taskId, request.getUserId());
        return ResponseEntity.ok(mapper.toModel(task));
    }

    @Override
    public ResponseEntity<CompleteTaskResponse> completeTask(String orgKey, String instanceId, String taskId, CompleteTaskRequest request) {
        Map<String, Object> context = request == null || request.getContext() == null ? Map.of() : request.getContext();
        var result = completeTask.complete(orgKey, UUID.fromString(instanceId), taskId, context);
        return ResponseEntity.ok(mapper.toModel(result));
    }

    @Override
    public ResponseEntity<TaskInstance> skipTask(String orgKey, String instanceId, String taskId, CancelWorkflowInstanceRequest request) {
        String reason = request == null ? null : request.getReason();
        var task = skipTask.skip(orgKey, UUID.fromString(instanceId), taskId, reason);
        return ResponseEntity.ok(mapper.toModel(task));
    }
}
