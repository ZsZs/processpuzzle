package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds a task to the organization's catalog. Nothing is checked against the processes that may
 * later reference it: a task exists on its own, and it is
 * {@code WorkflowValidator} that refuses a process naming one that does not.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class CreateTaskDefinitionUseCase {

    private final TaskDefinitionRepository repository;

    public TaskDefinition create(String orgKey, TaskDefinition task) {
        task.setOrgKey(orgKey);
        if (repository.existsByOrgKeyAndId(orgKey, task.getId())) {
            throw new ConflictException("Task definition '%s' already exists".formatted(task.getId()));
        }
        return repository.save(task);
    }
}
