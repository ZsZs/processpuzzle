package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ListTaskInstancesUseCase {

    private final TaskInstanceRepository repository;

    public ListTaskInstancesUseCase(TaskInstanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TaskInstance> findAll(String orgKey, UUID workflowInstanceId) {
        return repository.findByOrgKeyAndWorkflowInstanceId(orgKey, workflowInstanceId);
    }
}
