package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowDiagramRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.usecases.outbound.ActiveWorkflowInstanceExistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteWorkflowUseCase {

    private final WorkflowRepository repository;
    private final WorkflowDiagramRepository diagramRepository;
    private final ActiveWorkflowInstanceExistencePort activeInstanceExistencePort;

    public void delete(String orgKey, String id) {
        Workflow workflow = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No workflow definition with id '%s'".formatted(id)));

        if (activeInstanceExistencePort.existsActiveInstanceOf(orgKey, id)) {
            throw new ConflictException(
                    "'%s' still has active workflow instances — cancel or complete them first".formatted(id));
        }
        if (!repository.findByOrgKeyAndExtendsWorkflowId(orgKey, id).isEmpty()) {
            throw new ConflictException("'%s' is still extended by another workflow definition".formatted(id));
        }
        // The modeler's arrangement goes with the workflow it arranged. Deleted here rather than left
        // for DeleteWorkflowDiagramUseCase, because an orphaned layout is unreachable — the diagram is
        // addressed only by its workflow's id — and would silently resurface if that id were reused.
        diagramRepository.deleteByOrgKeyAndWorkflowId(orgKey, id);
        repository.delete(workflow);
    }
}
