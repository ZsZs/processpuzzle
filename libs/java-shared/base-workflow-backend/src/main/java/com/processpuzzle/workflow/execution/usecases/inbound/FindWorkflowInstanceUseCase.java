package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.execution.domain.WorkflowInstance;
import com.processpuzzle.workflow.execution.domain.WorkflowInstanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class FindWorkflowInstanceUseCase {

    private final WorkflowInstanceRepository repository;

    public FindWorkflowInstanceUseCase(WorkflowInstanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public WorkflowInstance findByOrgKeyAndId(String orgKey, UUID id) {
        return repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No workflow instance with id '%s'".formatted(id)));
    }
}
