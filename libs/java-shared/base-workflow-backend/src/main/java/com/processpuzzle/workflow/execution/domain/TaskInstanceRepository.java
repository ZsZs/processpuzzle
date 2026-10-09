package com.processpuzzle.workflow.execution.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskInstanceRepository
        extends JpaRepository<TaskInstance, UUID>, JpaSpecificationExecutor<TaskInstance> {

    Optional<TaskInstance> findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(
            String orgKey, UUID workflowInstanceId, String taskDefinitionId);

    List<TaskInstance> findByOrgKeyAndWorkflowInstanceId(String orgKey, UUID workflowInstanceId);

    /** The rows of one page of instances, for the list endpoint. */
    List<TaskInstance> findByOrgKeyAndWorkflowInstanceIdIn(String orgKey, Collection<UUID> workflowInstanceIds);

    /** A count rather than entities: it reads the committed rows, not this transaction's cached copies. */
    long countByOrgKeyAndWorkflowInstanceIdAndStatusNotIn(
            String orgKey, UUID workflowInstanceId, Collection<TaskInstanceStatus> statuses);

    List<TaskInstance> findByOrgKeyAndWorkflowInstanceIdAndStatus(
            String orgKey, UUID workflowInstanceId, TaskInstanceStatus status);
}
