package com.processpuzzle.workflow.execution.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowInstanceRepository
        extends JpaRepository<WorkflowInstance, UUID>, JpaSpecificationExecutor<WorkflowInstance> {

    Optional<WorkflowInstance> findByOrgKeyAndId(String orgKey, UUID id);

    long countByOrgKey(String orgKey);

    /** The highest instance number the organization has used; seeds a counter that does not exist yet. */
    @Query("select max(i.instanceNumber) from WorkflowInstance i where i.orgKey = :orgKey")
    Optional<Long> findMaxInstanceNumber(@Param("orgKey") String orgKey);

    /** Non-terminal statuses are ACTIVE and SUSPENDED; COMPLETED/CANCELLED are terminal. */
    boolean existsByOrgKeyAndWorkflowIdAndStatusIn(
            String orgKey, String workflowId, Collection<WorkflowInstanceStatus> statuses);

    /** Whether {@code entityId} already has a non-terminal instance of the workflow — a triggered start's idempotency check. */
    boolean existsByOrgKeyAndWorkflowIdAndEntityIdAndStatusIn(
            String orgKey, String workflowId, String entityId, Collection<WorkflowInstanceStatus> statuses);

    long countByOrgKeyAndWorkflowIdAndStatusIn(
            String orgKey, String workflowId, Collection<WorkflowInstanceStatus> statuses);
}
