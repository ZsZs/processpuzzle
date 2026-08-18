package com.processpuzzle.workflow.execution.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProcessInstanceRepository
        extends JpaRepository<ProcessInstance, UUID>, JpaSpecificationExecutor<ProcessInstance> {

    Optional<ProcessInstance> findByOrgKeyAndId(String orgKey, UUID id);

    /** Non-terminal statuses are ACTIVE and SUSPENDED; COMPLETED/CANCELLED are terminal. */
    boolean existsByOrgKeyAndProcessDefinitionIdAndStatusIn(
            String orgKey, String processDefinitionId, Collection<ProcessInstanceStatus> statuses);

    long countByOrgKeyAndProcessDefinitionIdAndStatusIn(
            String orgKey, String processDefinitionId, Collection<ProcessInstanceStatus> statuses);
}
