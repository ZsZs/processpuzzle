package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.execution.domain.TaskInstance;
import com.processpuzzle.workflow.execution.domain.TaskInstanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class FindTaskInstanceUseCase {

    private final TaskInstanceRepository repository;

    public FindTaskInstanceUseCase(TaskInstanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public TaskInstance find(String orgKey, UUID workflowInstanceId, String taskDefinitionId) {
        return repository.findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(orgKey, workflowInstanceId, taskDefinitionId)
                .orElseThrow(() -> new NotFoundException(
                        "No task '%s' in workflow instance '%s'".formatted(taskDefinitionId, workflowInstanceId)));
    }
}
