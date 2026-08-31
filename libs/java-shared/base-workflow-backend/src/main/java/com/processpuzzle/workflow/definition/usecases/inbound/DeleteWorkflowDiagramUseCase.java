package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagram;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Discards one workflow's arrangement, resetting the modeler to its automatic swimlane layout. The
 * workflow itself is untouched.
 *
 * <p>{@link DeleteWorkflowUseCase} drops the layout along with the workflow it belongs to, so this
 * exists for the "re-arrange from scratch" gesture rather than for cleanup — which is why a missing
 * layout is a 404 here rather than a silent success: the caller asked to discard something, and there
 * was nothing to discard.
 */
@Component
@RequiredArgsConstructor
@Transactional
public class DeleteWorkflowDiagramUseCase {

    private final WorkflowDiagramRepository repository;

    public void delete(String orgKey, String workflowId) {
        WorkflowDiagram diagram = repository.findByOrgKeyAndWorkflowId(orgKey, workflowId)
                .orElseThrow(() -> new NotFoundException(
                        "No diagram layout for workflow '%s'".formatted(workflowId)));
        repository.delete(diagram);
    }
}
