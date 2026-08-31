package com.processpuzzle.workflow.execution.usecases.inbound;

import com.processpuzzle.workflow.execution.domain.ArtifactInstance;
import com.processpuzzle.workflow.execution.domain.ArtifactInstanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ListArtifactInstancesUseCase {

    private final ArtifactInstanceRepository repository;

    public ListArtifactInstancesUseCase(ArtifactInstanceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ArtifactInstance> findAll(String orgKey, UUID workflowInstanceId) {
        return repository.findByOrgKeyAndWorkflowInstanceId(orgKey, workflowInstanceId);
    }
}
