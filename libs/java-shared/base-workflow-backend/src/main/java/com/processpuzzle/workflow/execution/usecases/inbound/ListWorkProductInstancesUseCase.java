package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.execution.domain.WorkProductInstance;
import com.processpuzzle.workflow.execution.domain.WorkProductInstanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ListWorkProductInstancesUseCase {

    private final WorkProductInstanceRepository repository;

    public ListWorkProductInstancesUseCase(WorkProductInstanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<WorkProductInstance> findAll(String orgKey, UUID processInstanceId) {
        return repository.findByOrgKeyAndProcessInstanceId(orgKey, processInstanceId);
    }
}
