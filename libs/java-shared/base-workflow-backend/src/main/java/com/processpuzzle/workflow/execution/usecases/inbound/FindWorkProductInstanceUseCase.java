package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.common.NotFoundException;
import com.processpuzzle.workflow.execution.domain.WorkProductInstance;
import com.processpuzzle.workflow.execution.domain.WorkProductInstanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class FindWorkProductInstanceUseCase {

    private final WorkProductInstanceRepository repository;

    public FindWorkProductInstanceUseCase(WorkProductInstanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public WorkProductInstance find(String orgKey, UUID processInstanceId, String workProductDefinitionId) {
        return repository.findByOrgKeyAndProcessInstanceIdAndWorkProductDefinitionId(orgKey, processInstanceId, workProductDefinitionId)
                .orElseThrow(() -> new NotFoundException(
                        "No work product '%s' in process instance '%s'".formatted(workProductDefinitionId, processInstanceId)));
    }
}
