package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.TaskDefinition;
import com.processpuzzle.workflow.definition.domain.TaskDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Refuses to remove a task any process still assigns. One guard suffices here: nothing but a process
 * assignment points at a task — a task's own dependsOn lives on the assignment, not on the definition.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class DeleteTaskDefinitionUseCase {

    private final TaskDefinitionRepository repository;
    private final CatalogReferenceScanner referenceScanner;

    public void delete(String orgKey, String id) {
        TaskDefinition task = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No task definition with id '%s'".formatted(id)));

        List<String> workflows = referenceScanner.processesAssigningTask(orgKey, id);
        if (!workflows.isEmpty()) {
            throw new ConflictException("Task '%s' is still used by workflows %s".formatted(id, workflows));
        }
        repository.delete(task);
    }
}
