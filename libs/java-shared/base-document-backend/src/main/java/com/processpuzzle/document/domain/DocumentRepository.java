package com.processpuzzle.document.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * Every finder is scoped by {@code orgKey}, exactly as {@code RuleDefinitionRepository}
 * documents: the inherited {@code findById}/{@code existsById} take an {@link DocumentKey}, so
 * an unscoped read of another tenant's row is not expressible by accident.
 */
public interface DocumentRepository
        extends JpaRepository<Document, DocumentKey>, JpaSpecificationExecutor<Document> {

    Optional<Document> findByOrgKeyAndId(String orgKey, String id);

    boolean existsByOrgKeyAndId(String orgKey, String id);

    /**
     * The slug lookup the public read path resolves against. Returns at most one row: a unique
     * constraint on ({@code org_key}, {@code slug}) backs it, so a duplicate is rejected by the
     * database rather than silently shadowing another document.
     */
    Optional<Document> findByOrgKeyAndSlug(String orgKey, String slug);

    boolean existsByOrgKeyAndSlug(String orgKey, String slug);

    List<Document> findByOrgKey(String orgKey);

    void deleteByOrgKey(String orgKey);
}
