package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.core.definition.Channel;
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
public class CreateWorkflowUseCase {

    private final WorkflowRepository repository;
    private final WorkflowValidator validator;
    private final WorkflowExtendsValidator extendsValidator;

    public Workflow create(String orgKey, Workflow workflow) {
        workflow.setOrgKey(orgKey);
        if (repository.existsByOrgKeyAndIdAndChannel(orgKey, workflow.getId(), Channel.DRAFT)) {
            throw new ConflictException("Workflow definition '%s' already exists".formatted(workflow.getId()));
        }
        extendsValidator.validate(orgKey, workflow.getId(), workflow.getExtendsWorkflowId());
        validator.validate(workflow);
        Workflow saved = repository.save(workflow);
        return saved;
    }
}
