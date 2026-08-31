package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagram;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the diagram layout of one workflow.
 *
 * <p>Throws rather than answering an empty layout when nothing has been arranged yet: the modeler acts
 * on the difference — it keeps its automatic swimlane layout — so "never arranged" has to stay
 * distinguishable from "arranged, then emptied". This is the opposite call from
 * {@code WorkflowTranslationEndpoint}'s, where the loader cannot act on the difference and an unseeded
 * bundle is therefore a {@code 200} with an empty object.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FindWorkflowDiagramUseCase {

    private final WorkflowDiagramRepository repository;

    public WorkflowDiagram findByOrgKeyAndWorkflowId(String orgKey, String workflowId) {
        return repository.findByOrgKeyAndWorkflowId(orgKey, workflowId)
                .orElseThrow(() -> new NotFoundException(
                        "No diagram layout for workflow '%s'".formatted(workflowId)));
    }
}
