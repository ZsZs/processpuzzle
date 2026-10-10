package com.processpuzzle.workflow.execution.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactInstanceRepository
        extends JpaRepository<ArtifactInstance, UUID>, JpaSpecificationExecutor<ArtifactInstance> {

    Optional<ArtifactInstance> findByOrgKeyAndId(String orgKey, UUID id);

    List<ArtifactInstance> findByOrgKeyAndWorkflowInstanceId(String orgKey, UUID workflowInstanceId);

    /**
     * {@code getArtifactInstance}'s path parameter is {@code artifactId} — by the same
     * convention task instances use ({@code taskId} = the stable {@code taskDefinitionId}, not a
     * generated instance UUID — see {@code TaskInstanceRepository}), this addresses a work
     * product instance by its owning definition's id within the workflow instance, not by
     * {@link ArtifactInstance#getId()}.
     */
    Optional<ArtifactInstance> findByOrgKeyAndWorkflowInstanceIdAndArtifactDefinitionId(
            String orgKey, UUID workflowInstanceId, String artifactDefinitionId);

    /** Used by the (future) base-state change listener to find the row to refresh. */
    Optional<ArtifactInstance> findByOrgKeyAndStateMachineInstanceId(String orgKey, String stateMachineInstanceId);

    /**
     * Every artifact instance of a tenant in one statement, for erasing the tenant. A bulk delete, so it
     * neither loads the rows nor checks their version.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from ArtifactInstance a where a.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
