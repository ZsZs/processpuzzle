package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.ToolDefinition;
import com.processpuzzle.workflow.definition.domain.ToolDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Refuses to remove a tool a workflow still lists, or a task step still invokes. The step guard is new
 * with the shared catalog: a task now outlives the one workflow it used to belong to, so a tool can be
 * reachable through a task without any workflow naming it directly.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class DeleteToolDefinitionUseCase {

    private final ToolDefinitionRepository repository;
    private final CatalogReferenceScanner referenceScanner;

    public void delete(String orgKey, String id) {
        ToolDefinition tool = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No tool definition with id '%s'".formatted(id)));

        List<String> workflows = referenceScanner.workflowsUsingTool(orgKey, id);
        if (!workflows.isEmpty()) {
            throw new ConflictException("Tool '%s' is still referenced by workflows %s".formatted(id, workflows));
        }
        List<String> tasks = referenceScanner.tasksUsingTool(orgKey, id);
        if (!tasks.isEmpty()) {
            throw new ConflictException("Tool '%s' is still invoked by steps of tasks %s".formatted(id, tasks));
        }
        repository.delete(tool);
    }
}
