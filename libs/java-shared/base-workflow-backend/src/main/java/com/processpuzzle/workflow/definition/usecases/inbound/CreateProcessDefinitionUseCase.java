package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowExtendsValidator;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import com.processpuzzle.workflow.definition.domain.WorkflowValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class CreateProcessDefinitionUseCase {

    private final WorkflowRepository repository;
    private final WorkflowValidator validator;
    private final WorkflowExtendsValidator extendsValidator;

    public Workflow create(String orgKey, Workflow process) {
        process.setOrgKey(orgKey);
        if (repository.existsByOrgKeyAndId(orgKey, process.getId())) {
            throw new ConflictException("Process definition '%s' already exists".formatted(process.getId()));
        }
        extendsValidator.validate(orgKey, process.getId(), process.getExtendsProcessId());
        validator.validate(process);
        return repository.save(process);
    }
}
