package com.processpuzzle.workflow.definition.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.definition.domain.Workflow;
import com.processpuzzle.workflow.definition.domain.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FindProcessDefinitionUseCase {

    private final WorkflowRepository repository;

    @Transactional(readOnly = true)
    public Workflow findByOrgKeyAndId(String orgKey, String id) {
        return repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No process definition with id '%s'".formatted(id)));
    }
}
