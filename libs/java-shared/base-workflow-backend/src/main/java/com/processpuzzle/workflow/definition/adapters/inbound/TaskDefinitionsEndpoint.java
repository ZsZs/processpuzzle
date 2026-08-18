package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.api.TaskDefinitionsApi;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateTaskDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteTaskDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindProcessDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceTaskDefinitionUseCase;
import com.processpuzzle.workflow.model.TaskDefinition;
import com.processpuzzle.workflow.model.TaskDefinitionInput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implements the generated {@code TaskDefinitionsApi} (from the "Task Definitions" tag). See
 * {@code RoleDefinitionsEndpoint} for why {@code where}/{@code order} aren't applied here yet.
 */
@RestController
public class TaskDefinitionsEndpoint implements TaskDefinitionsApi {

    private final CreateTaskDefinitionUseCase createTaskDefinition;
    private final ReplaceTaskDefinitionUseCase replaceTaskDefinition;
    private final DeleteTaskDefinitionUseCase deleteTaskDefinition;
    private final FindProcessDefinitionUseCase findProcessDefinition;
    private final WorkflowDefinitionMapper mapper;

    public TaskDefinitionsEndpoint(CreateTaskDefinitionUseCase createTaskDefinition,
                                    ReplaceTaskDefinitionUseCase replaceTaskDefinition,
                                    DeleteTaskDefinitionUseCase deleteTaskDefinition,
                                    FindProcessDefinitionUseCase findProcessDefinition,
                                    WorkflowDefinitionMapper mapper) {
        this.createTaskDefinition = createTaskDefinition;
        this.replaceTaskDefinition = replaceTaskDefinition;
        this.deleteTaskDefinition = deleteTaskDefinition;
        this.findProcessDefinition = findProcessDefinition;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<TaskDefinition>> listTaskDefinitions(String orgKey, String processId, String where, String order) {
        var process = findProcessDefinition.findByOrgKeyAndId(orgKey, processId);
        return ResponseEntity.ok(process.getTasks().stream().map(mapper::toTaskModel).toList());
    }

    @Override
    public ResponseEntity<TaskDefinition> createTaskDefinition(String orgKey, String processId, TaskDefinitionInput input) {
        var created = createTaskDefinition.create(orgKey, processId, mapper.toTaskDomain(input));
        return new ResponseEntity<>(mapper.toTaskModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<TaskDefinition> getTaskDefinition(String orgKey, String processId, String taskId) {
        var process = findProcessDefinition.findByOrgKeyAndId(orgKey, processId);
        var task = process.findTask(taskId)
                .orElseThrow(() -> new NotFoundException("No task '%s' in process '%s'".formatted(taskId, processId)));
        return ResponseEntity.ok(mapper.toTaskModel(task));
    }

    @Override
    public ResponseEntity<TaskDefinition> updateTaskDefinition(String orgKey, String processId, String taskId, TaskDefinitionInput input) {
        var updated = replaceTaskDefinition.replace(orgKey, processId, taskId, mapper.toTaskDomain(input));
        return ResponseEntity.ok(mapper.toTaskModel(updated));
    }

    @Override
    public ResponseEntity<Void> deleteTaskDefinition(String orgKey, String processId, String taskId) {
        deleteTaskDefinition.delete(orgKey, processId, taskId);
        return ResponseEntity.noContent().build();
    }
}
