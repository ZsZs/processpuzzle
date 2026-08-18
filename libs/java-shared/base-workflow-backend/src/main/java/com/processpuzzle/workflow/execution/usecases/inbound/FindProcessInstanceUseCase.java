package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.execution.domain.ProcessInstance;
import com.processpuzzle.workflow.execution.domain.ProcessInstanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class FindProcessInstanceUseCase {

    private final ProcessInstanceRepository repository;

    public FindProcessInstanceUseCase(ProcessInstanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ProcessInstance findByOrgKeyAndId(String orgKey, UUID id) {
        return repository.findByOrgKeyAndId(orgKey, id)
                .orElseThrow(() -> new NotFoundException("No process instance with id '%s'".formatted(id)));
    }
}
