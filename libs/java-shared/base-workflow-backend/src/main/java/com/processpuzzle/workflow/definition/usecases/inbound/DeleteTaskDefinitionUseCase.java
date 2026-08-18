package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteTaskDefinitionUseCase {

    private final ProcessDefinitionRepository repository;

    public void delete(String orgKey, String processId, String taskId) {
        ProcessDefinition process = repository.findByOrgKeyAndId(orgKey, processId)
                .orElseThrow(() -> new NotFoundException("No process definition with id '%s'".formatted(processId)));

        TaskDefinition task = process.findTask(taskId)
                .orElseThrow(() -> new NotFoundException(
                        "No task '%s' in process '%s'".formatted(taskId, processId)));

        java.util.List<String> stillDependedOnBy = process.getTasks().stream()
                .filter(t -> t.getDependsOn().contains(taskId))
                .map(TaskDefinition::getId)
                .toList();
        if (!stillDependedOnBy.isEmpty()) {
            throw new ConflictException(
                    "Task '%s' is still a dependency of tasks %s".formatted(taskId, stillDependedOnBy));
        }
        process.getTasks().remove(task);
        repository.save(process);
    }
}
