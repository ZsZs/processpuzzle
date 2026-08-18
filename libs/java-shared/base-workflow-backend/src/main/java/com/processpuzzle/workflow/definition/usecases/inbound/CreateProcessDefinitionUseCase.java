package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.ConflictException;
import com.processpuzzle.workflow.definition.domain.ProcessDefinition;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionExtendsValidator;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionRepository;
import com.processpuzzle.workflow.definition.domain.ProcessDefinitionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class CreateProcessDefinitionUseCase {

    private final ProcessDefinitionRepository repository;
    private final ProcessDefinitionValidator validator;
    private final ProcessDefinitionExtendsValidator extendsValidator;

    public ProcessDefinition create(String orgKey, ProcessDefinition process) {
        process.setOrgKey(orgKey);
        if (repository.existsByOrgKeyAndId(orgKey, process.getId())) {
            throw new ConflictException("Process definition '%s' already exists".formatted(process.getId()));
        }
        extendsValidator.validate(orgKey, process.getId(), process.getExtendsProcessId());
        validator.validate(process);
        return repository.save(process);
    }
}
