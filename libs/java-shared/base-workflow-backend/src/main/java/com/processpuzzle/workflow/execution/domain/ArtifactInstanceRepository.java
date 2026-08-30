package com.processpuzzle.workflow.execution.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactInstanceRepository
        extends JpaRepository<ArtifactInstance, UUID>, JpaSpecificationExecutor<ArtifactInstance> {

    Optional<ArtifactInstance> findByOrgKeyAndId(String orgKey, UUID id);

    List<ArtifactInstance> findByOrgKeyAndProcessInstanceId(String orgKey, UUID processInstanceId);

    /**
     * {@code getArtifactInstance}'s path parameter is {@code artifactId} — by the same
     * convention task instances use ({@code taskId} = the stable {@code taskDefinitionId}, not a
     * generated instance UUID — see {@code TaskInstanceRepository}), this addresses a work
     * product instance by its owning definition's id within the process instance, not by
     * {@link ArtifactInstance#getId()}.
     */
    Optional<ArtifactInstance> findByOrgKeyAndProcessInstanceIdAndArtifactDefinitionId(
            String orgKey, UUID processInstanceId, String artifactDefinitionId);

    /** Used by the (future) base-state change listener to find the row to refresh. */
    Optional<ArtifactInstance> findByOrgKeyAndStateMachineInstanceId(String orgKey, String stateMachineInstanceId);
}
