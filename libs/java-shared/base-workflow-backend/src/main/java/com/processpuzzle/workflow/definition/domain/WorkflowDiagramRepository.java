package com.processpuzzle.workflow.definition.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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

    Optional<WorkflowDiagram> findByOrgKeyAndWorkflowId(String orgKey, String workflowId);

    boolean existsByOrgKeyAndWorkflowId(String orgKey, String workflowId);

    List<WorkflowDiagram> findByOrgKey(String orgKey);

    void deleteByOrgKeyAndWorkflowId(String orgKey, String workflowId);
}
