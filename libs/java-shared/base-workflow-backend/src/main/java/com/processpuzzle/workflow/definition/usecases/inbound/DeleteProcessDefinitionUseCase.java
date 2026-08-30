package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.usecases.outbound.ActiveProcessInstanceExistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteProcessDefinitionUseCase {

    private final WorkflowRepository repository;
    private final ActiveProcessInstanceExistencePort activeInstanceExistencePort;

    public void delete(String orgKey, String id) {
        Workflow process = repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No process definition with id '%s'".formatted(id)));

        if (activeInstanceExistencePort.existsActiveInstanceOf(orgKey, id)) {
            throw new ConflictException(
                    "'%s' still has active process instances — cancel or complete them first".formatted(id));
        }
        if (!repository.findByOrgKeyAndExtendsProcessId(orgKey, id).isEmpty()) {
            throw new ConflictException("'%s' is still extended by another process definition".formatted(id));
        }
        repository.delete(process);
    }
}
