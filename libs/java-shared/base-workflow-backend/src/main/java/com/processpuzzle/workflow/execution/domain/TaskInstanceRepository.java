package com.processpuzzle.workflow.execution.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskInstanceRepository
        extends JpaRepository<TaskInstance, UUID>, JpaSpecificationExecutor<TaskInstance> {

    Optional<TaskInstance> findByOrgKeyAndProcessInstanceIdAndTaskDefinitionId(
            String orgKey, UUID processInstanceId, String taskDefinitionId);

    List<TaskInstance> findByOrgKeyAndProcessInstanceId(String orgKey, UUID processInstanceId);

    List<TaskInstance> findByOrgKeyAndProcessInstanceIdAndStatus(
            String orgKey, UUID processInstanceId, TaskInstanceStatus status);
}
