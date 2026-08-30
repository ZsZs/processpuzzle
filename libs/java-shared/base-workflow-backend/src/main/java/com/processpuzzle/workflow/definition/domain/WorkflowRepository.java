package com.processpuzzle.workflow.definition.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * No {@code @EntityGraph} on the finders: a workflow is a single row, its roles, artifacts, tools
 * and task uses being JSONB columns rather than {@code @OneToMany} collections, so there is nothing
 * left to fetch-join.
 */
public interface WorkflowRepository
        extends JpaRepository<Workflow, WorkflowKey>, JpaSpecificationExecutor<Workflow> {

    Optional<Workflow> findByOrgKeyAndId(String orgKey, String id);

    boolean existsByOrgKeyAndId(String orgKey, String id);

    List<Workflow> findByOrgKeyAndExtendsProcessId(String orgKey, String extendsProcessId);

    List<Workflow> findByOrgKey(String orgKey);
}
