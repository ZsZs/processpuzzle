package com.processpuzzle.workflow.execution.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkProductInstanceRepository
        extends JpaRepository<WorkProductInstance, UUID>, JpaSpecificationExecutor<WorkProductInstance> {

    Optional<WorkProductInstance> findByOrgKeyAndId(String orgKey, UUID id);

    List<WorkProductInstance> findByOrgKeyAndProcessInstanceId(String orgKey, UUID processInstanceId);

    /**
     * {@code getWorkProductInstance}'s path parameter is {@code workProductId} — by the same
     * convention task instances use ({@code taskId} = the stable {@code taskDefinitionId}, not a
     * generated instance UUID — see {@code TaskInstanceRepository}), this addresses a work
     * product instance by its owning definition's id within the process instance, not by
     * {@link WorkProductInstance#getId()}.
     */
    Optional<WorkProductInstance> findByOrgKeyAndProcessInstanceIdAndWorkProductDefinitionId(
            String orgKey, UUID processInstanceId, String workProductDefinitionId);

    /** Used by the (future) base-state change listener to find the row to refresh. */
    Optional<WorkProductInstance> findByOrgKeyAndStateMachineInstanceId(String orgKey, String stateMachineInstanceId);
}
