package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionValidator;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class CreateTaskDefinitionUseCase {

    private final ProcessDefinitionRepository repository;
    private final ProcessDefinitionValidator validator;

    public TaskDefinition create(String orgKey, String processId, TaskDefinition task) {
        ProcessDefinition process = repository.findByOrgKeyAndId(orgKey, processId)
                .orElseThrow(() -> new NotFoundException("No process definition with id '%s'".formatted(processId)));

        if (process.findTask(task.getId()).isPresent()) {
            throw new ConflictException(
                    "Task '%s' already exists in process '%s'".formatted(task.getId(), processId));
        }
        process.addTask(task);
        validator.validate(process);
        repository.save(process);
        return task;
    }
}
