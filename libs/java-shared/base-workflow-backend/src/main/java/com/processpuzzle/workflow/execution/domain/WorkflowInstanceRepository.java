package com.processpuzzle.workflow.execution.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowInstanceRepository
        extends JpaRepository<WorkflowInstance, UUID>, JpaSpecificationExecutor<WorkflowInstance> {

    Optional<WorkflowInstance> findByOrgKeyAndId(String orgKey, UUID id);

    /** Non-terminal statuses are ACTIVE and SUSPENDED; COMPLETED/CANCELLED are terminal. */
    boolean existsByOrgKeyAndWorkflowIdAndStatusIn(
            String orgKey, String workflowId, Collection<WorkflowInstanceStatus> statuses);

    long countByOrgKeyAndWorkflowIdAndStatusIn(
            String orgKey, String workflowId, Collection<WorkflowInstanceStatus> statuses);
}
