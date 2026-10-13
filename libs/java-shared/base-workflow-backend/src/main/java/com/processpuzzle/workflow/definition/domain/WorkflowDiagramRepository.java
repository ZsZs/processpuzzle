package com.processpuzzle.workflow.definition.domain;

import com.processpuzzle.core.definition.Channel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every finder is scoped by {@code orgKey}, same discipline as {@link WorkflowRepository}: the
 * inherited {@code findById}/{@code existsById} take a {@link WorkflowDiagramKey}, so an unscoped read
 * of another tenant's layout is not expressible by accident.
 *
 * <p>No {@code @EntityGraph} on the finders: a layout is a single row, its nodes and edges being JSONB
 * columns rather than {@code @OneToMany} collections, so there is nothing left to fetch-join.
 */
public interface WorkflowDiagramRepository
        extends JpaRepository<WorkflowDiagram, WorkflowDiagramKey>, JpaSpecificationExecutor<WorkflowDiagram> {

    Optional<WorkflowDiagram> findByOrgKeyAndWorkflowIdAndChannel(String orgKey, String workflowId, Channel channel);

    boolean existsByOrgKeyAndWorkflowIdAndChannel(String orgKey, String workflowId, Channel channel);

    List<WorkflowDiagram> findByOrgKeyAndChannel(String orgKey, Channel channel);

    void deleteByOrgKeyAndWorkflowIdAndChannel(String orgKey, String workflowId, Channel channel);

    /**
     * Every workflow diagram layout of a tenant in one statement, for erasing the tenant. A bulk delete, so it
     * neither loads the rows nor checks their version.
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from WorkflowDiagram d where d.orgKey = :orgKey")
    int eraseByOrgKey(@Param("orgKey") String orgKey);
}
