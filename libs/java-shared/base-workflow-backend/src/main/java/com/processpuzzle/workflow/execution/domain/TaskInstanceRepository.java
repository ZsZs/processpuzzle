package com.processpuzzle.workflow.execution.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskInstanceRepository
        extends JpaRepository<TaskInstance, UUID>, JpaSpecificationExecutor<TaskInstance> {

    Optional<TaskInstance> findByOrgKeyAndWorkflowInstanceIdAndTaskDefinitionId(
            String orgKey, UUID workflowInstanceId, String taskDefinitionId);

    List<TaskInstance> findByOrgKeyAndWorkflowInstanceId(String orgKey, UUID workflowInstanceId);

    List<TaskInstance> findByOrgKeyAndWorkflowInstanceIdAndStatus(
            String orgKey, UUID workflowInstanceId, TaskInstanceStatus status);
}
