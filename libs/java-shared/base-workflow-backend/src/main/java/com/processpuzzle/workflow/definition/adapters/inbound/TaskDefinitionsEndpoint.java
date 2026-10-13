package com.processpuzzle.workflow.definition.adapters.inbound;

import com.processpuzzle.workflow.definition.adapters.inbound.release.ReleaseStates;
import com.processpuzzle.workflow.definition.adapters.inbound.release.TaskReleaseParticipant;
import com.processpuzzle.core.definition.Channel;
import com.processpuzzle.workflow.api.TaskDefinitionsApi;
import com.processpuzzle.workflow.definition.usecases.inbound.CreateTaskDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.DeleteTaskDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindAllTaskDefinitionsUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.FindTaskDefinitionUseCase;
import com.processpuzzle.workflow.definition.usecases.inbound.ReplaceTaskDefinitionUseCase;
import com.processpuzzle.workflow.model.TaskDefinition;
import com.processpuzzle.workflow.model.TaskDefinitionInput;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implements the generated {@code TaskDefinitionsApi} (from the "Task Definitions" tag).
 *
 * <p>Organization-scoped, not workflow-scoped: a task belongs to the tenant's catalog and may be
 * referenced by any number of workflow definitions.
 */
@RestController
public class TaskDefinitionsEndpoint implements TaskDefinitionsApi {

    private final CreateTaskDefinitionUseCase createTaskDefinition;
    private final ReplaceTaskDefinitionUseCase replaceTaskDefinition;
    private final DeleteTaskDefinitionUseCase deleteTaskDefinition;
    private final FindTaskDefinitionUseCase findTaskDefinition;
    private final FindAllTaskDefinitionsUseCase findAllTaskDefinitions;
    private final WorkflowDefinitionMapper mapper;
    private final TaskReleaseParticipant releaseParticipant;

    public TaskDefinitionsEndpoint(CreateTaskDefinitionUseCase createTaskDefinition,
                                    ReplaceTaskDefinitionUseCase replaceTaskDefinition,
                                    DeleteTaskDefinitionUseCase deleteTaskDefinition,
                                    FindTaskDefinitionUseCase findTaskDefinition,
                                    FindAllTaskDefinitionsUseCase findAllTaskDefinitions,
                                    WorkflowDefinitionMapper mapper,
            TaskReleaseParticipant releaseParticipant) {
        this.createTaskDefinition = createTaskDefinition;
        this.replaceTaskDefinition = replaceTaskDefinition;
        this.deleteTaskDefinition = deleteTaskDefinition;
        this.findTaskDefinition = findTaskDefinition;
        this.findAllTaskDefinitions = findAllTaskDefinitions;
        this.mapper = mapper;
        this.releaseParticipant = releaseParticipant;
    }

    @Override
    public ResponseEntity<List<TaskDefinition>> listTaskDefinitions(String orgKey, String where, String order, String revision) {
        Channel channel = Channel.ofRevision(revision);
        var tasks = findAllTaskDefinitions.findAll(orgKey, channel, where, order).stream().map(mapper::toTaskModel).toList();
        return ResponseEntity.ok(ReleaseStates.stamp(channel, releaseParticipant, orgKey, tasks,
                TaskDefinition::getId, TaskDefinition::setReleaseState));
    }

    @Override
    public ResponseEntity<TaskDefinition> createTaskDefinition(String orgKey, TaskDefinitionInput input) {
        var created = createTaskDefinition.create(orgKey, mapper.toTaskDomain(input));
        return new ResponseEntity<>(mapper.toTaskModel(created), HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<TaskDefinition> getTaskDefinition(String orgKey, String taskId, String revision) {
        Channel channel = Channel.ofRevision(revision);
        var task = mapper.toTaskModel(findTaskDefinition.findByOrgKeyAndId(orgKey, taskId, channel));
        return ResponseEntity.ok(ReleaseStates.stampOne(channel, releaseParticipant, orgKey, task,
                TaskDefinition::getId, TaskDefinition::setReleaseState));
    }

    @Override
    public ResponseEntity<TaskDefinition> updateTaskDefinition(String orgKey, String taskId, TaskDefinitionInput input) {
        var updated = replaceTaskDefinition.replace(orgKey, taskId, mapper.toTaskDomain(input));
        return ResponseEntity.ok(mapper.toTaskModel(updated));
    }

    @Override
    public ResponseEntity<Void> deleteTaskDefinition(String orgKey, String taskId) {
        deleteTaskDefinition.delete(orgKey, taskId);
        return ResponseEntity.noContent().build();
    }
}
